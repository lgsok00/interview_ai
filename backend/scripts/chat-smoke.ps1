param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$Email,

    [Parameter(Mandatory = $true)]
    [ValidateRange(1, [long]::MaxValue)]
    [long]$JobPostingId,

    [ValidateNotNullOrEmpty()]
    [string]$BaseUrl = "http://localhost:8080",

    [ValidateRange(30, 600)]
    [int]$TimeoutSeconds = 180
)

$ErrorActionPreference = "Stop"

function Invoke-JsonRequest {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet("GET", "POST", "PUT")]
        [string]$Method,

        [Parameter(Mandatory = $true)]
        [string]$Uri,

        [hashtable]$Headers = @{},

        [object]$Body
    )

    $parameters = @{
        Method      = $Method
        Uri         = $Uri
        Headers     = $Headers
        ContentType = "application/json"
    }

    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 10
        $parameters.Body = [System.Text.Encoding]::UTF8.GetBytes($json)
    }

    try {
        Invoke-RestMethod @parameters
    }
    catch {
        $details = $_.ErrorDetails.Message
        if ([string]::IsNullOrWhiteSpace($details)) {
            $details = $_.Exception.Message
        }
        throw "$Method $Uri 실패: $details"
    }
}

function Wait-Until {
    param(
        [Parameter(Mandatory = $true)]
        [scriptblock]$Request,

        [Parameter(Mandatory = $true)]
        [scriptblock]$IsComplete,

        [Parameter(Mandatory = $true)]
        [scriptblock]$IsFailed,

        [Parameter(Mandatory = $true)]
        [string]$Description
    )

    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)

    while ([DateTimeOffset]::UtcNow -lt $deadline) {
        $result = & $Request

        if (& $IsFailed $result) {
            $json = $result | ConvertTo-Json -Depth 10 -Compress
            throw "$Description 실패 상태: $json"
        }

        if (& $IsComplete $result) {
            return $result
        }

        Start-Sleep -Seconds 2
    }

    throw "$Description 대기 시간이 ${TimeoutSeconds}초를 초과했습니다."
}

Write-Host "[1/8] 애플리케이션 health 확인"
$health = Invoke-JsonRequest -Method GET -Uri "$BaseUrl/actuator/health"
if ($health.status -ne "UP") {
    throw "애플리케이션 health가 UP이 아닙니다: $($health.status)"
}

$securePassword = Read-Host "Smoke 사용자 비밀번호" -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
}
finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
}

Write-Host "[2/8] 로그인"
$login = Invoke-JsonRequest -Method POST -Uri "$BaseUrl/api/auth/login" -Body @{
    email    = $Email
    password = $password
}
$password = $null

if ([string]::IsNullOrWhiteSpace($login.accessToken)) {
    throw "로그인 응답에 accessToken이 없습니다."
}

$headers = @{ Authorization = "Bearer $($login.accessToken)" }

Write-Host "[3/8] 면접 세션 생성 및 AI 초기 질문 대기"
$session = Invoke-JsonRequest -Method POST -Uri "$BaseUrl/api/interview-sessions" -Headers $headers -Body @{
    jobPostingId = $JobPostingId
}
$sessionId = [long]$session.id

$session = Wait-Until `
    -Description "초기 질문 생성" `
    -Request { Invoke-JsonRequest -Method GET -Uri "$BaseUrl/api/interview-sessions/$sessionId" -Headers $headers } `
    -IsComplete { param($value) $value.status -eq "READY" } `
    -IsFailed { param($value) $value.status -eq "FAILED" }

$questionsResponse = Invoke-JsonRequest `
    -Method GET `
    -Uri "$BaseUrl/api/interview-sessions/$sessionId/questions" `
    -Headers $headers
$questions = @()
foreach ($item in $questionsResponse) {
    $questions += $item
}
if ($questions.Count -ne 5) {
    $responseJson = $questionsResponse | ConvertTo-Json -Depth 10 -Compress
    throw "초기 질문은 5개여야 하지만 $($questions.Count)개입니다. 응답: $responseJson"
}
if (@($questions | Where-Object { $_.generationSource -ne "AI" }).Count -ne 0) {
    throw "초기 질문 중 AI가 아닌 생성 결과가 있습니다. RAG 검색 결과와 fallback 사유를 확인하세요."
}
if (@($questions | Where-Object { $_.questionType -eq "TECHNICAL" }).Count -ne 3 -or
    @($questions | Where-Object { $_.questionType -eq "BEHAVIORAL" }).Count -ne 2) {
    throw "초기 질문 유형은 TECHNICAL 3개와 BEHAVIORAL 2개여야 합니다."
}

Write-Host "[4/8] 면접 시작"
Invoke-JsonRequest -Method POST -Uri "$BaseUrl/api/interview-sessions/$sessionId/start" -Headers $headers | Out-Null

$question = $questions[0]
$answerText = "상황을 먼저 파악하고 요구사항을 작은 단위로 나눈 뒤 우선순위를 정했습니다. 핵심 기능을 구현하고 자동 테스트와 로그를 통해 결과를 검증했으며, 발견한 문제를 수정해 안정적으로 배포 가능한 상태를 만들었습니다."

Write-Host "[5/8] 첫 질문 답변 저장"
$answer = Invoke-JsonRequest -Method PUT `
    -Uri "$BaseUrl/api/interview-sessions/$sessionId/questions/$($question.id)/answer" `
    -Headers $headers `
    -Body @{ content = $answerText }

Write-Host "[6/8] AI 꼬리 질문 생성"
$followUp = Invoke-JsonRequest -Method POST `
    -Uri "$BaseUrl/api/interview-sessions/$sessionId/questions/$($question.id)/follow-up" `
    -Headers $headers
if ($followUp.question.generationSource -ne "AI" -or $followUp.question.questionType -ne "FOLLOW_UP") {
    $json = $followUp | ConvertTo-Json -Depth 10 -Compress
    throw "AI FOLLOW_UP 질문이 생성되지 않았습니다: $json"
}

Write-Host "[7/8] 답변 AI 평가 요청 및 완료 대기"
$evaluationUri = "$BaseUrl/api/interview-sessions/$sessionId/answers/$($answer.id)/evaluation"
Invoke-JsonRequest -Method POST -Uri $evaluationUri -Headers $headers | Out-Null
$evaluation = Wait-Until `
    -Description "답변 평가" `
    -Request { Invoke-JsonRequest -Method GET -Uri $evaluationUri -Headers $headers } `
    -IsComplete { param($value) $value.status -eq "COMPLETED" } `
    -IsFailed { param($value) $value.status -eq "FAILED" }

foreach ($scoreName in @("starScore", "logicScore", "jobFitScore")) {
    $score = [int]$evaluation.$scoreName
    if ($score -lt 0 -or $score -gt 100) {
        throw "$scoreName 점수가 0~100 범위를 벗어났습니다: $score"
    }
}
foreach ($textName in @("strengths", "improvements", "improvedAnswer")) {
    if ([string]::IsNullOrWhiteSpace($evaluation.$textName)) {
        throw "평가 결과의 $textName 값이 비어 있습니다."
    }
}

Write-Host "[8/8] 면접 완료"
$completed = Invoke-JsonRequest -Method POST -Uri "$BaseUrl/api/interview-sessions/$sessionId/complete" -Headers $headers
if ($completed.status -ne "COMPLETED") {
    throw "면접 세션이 COMPLETED 상태가 아닙니다: $($completed.status)"
}

[pscustomobject]@{
    Result                   = "PASS"
    SessionId                = $sessionId
    InitialQuestionCount     = $questions.Count
    InitialGenerationSource  = "AI"
    FollowUpQuestionId       = $followUp.question.id
    FollowUpGenerationSource = $followUp.question.generationSource
    EvaluationId             = $evaluation.id
    EvaluationStatus         = $evaluation.status
    StarScore                = $evaluation.starScore
    LogicScore               = $evaluation.logicScore
    JobFitScore              = $evaluation.jobFitScore
    SessionStatus            = $completed.status
} | Format-List
