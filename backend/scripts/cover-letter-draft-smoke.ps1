param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$Email,

    [Parameter(Mandatory = $true)]
    [ValidateRange(1, [long]::MaxValue)]
    [long]$CoverLetterId,

    [Parameter(Mandatory = $true)]
    [ValidateRange(1, [long]::MaxValue)]
    [long]$JobPostingId,

    [ValidateRange(1, [long]::MaxValue)]
    [Nullable[long]]$ResumeId,

    [ValidateNotNullOrEmpty()]
    [string]$BaseUrl = "http://localhost:8080",

    [ValidateRange(30, 600)]
    [int]$TimeoutSeconds = 180
)

$ErrorActionPreference = "Stop"

function Invoke-JsonRequest {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet("GET", "POST")]
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
        throw "$Method $Uri failed: $details"
    }
}

Write-Host "[1/4] Checking application health"
$health = Invoke-JsonRequest -Method GET -Uri "$BaseUrl/actuator/health"
if ($health.status -ne "UP") {
    throw "Application health is not UP: $($health.status)"
}

$securePassword = Read-Host "Smoke user password" -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
}
finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
}

Write-Host "[2/4] Logging in"
$login = Invoke-JsonRequest -Method POST -Uri "$BaseUrl/api/auth/login" -Body @{
    email = $Email
    password = $password
}
$password = $null
if ([string]::IsNullOrWhiteSpace($login.accessToken)) {
    throw "Login response does not contain accessToken."
}
$headers = @{ Authorization = "Bearer $($login.accessToken)" }

Write-Host "[3/4] Requesting AI cover-letter draft"
$body = @{
    jobPostingId = $JobPostingId
    instruction = "Preserve the existing facts and writing style while emphasizing skills relevant to the job posting."
}
if ($null -ne $ResumeId) {
    $body.resumeId = $ResumeId.Value
}
$draft = Invoke-JsonRequest -Method POST `
    -Uri "$BaseUrl/api/cover-letters/$CoverLetterId/drafts" `
    -Headers $headers `
    -Body $body
$draftId = [long]$draft.id

Write-Host "[4/4] Waiting for the real OpenAI Chat result"
$deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
do {
    Start-Sleep -Seconds 2
    $draft = Invoke-JsonRequest -Method GET `
        -Uri "$BaseUrl/api/cover-letters/$CoverLetterId/drafts/$draftId" `
        -Headers $headers
    if ($draft.status -eq "FAILED") {
        $json = $draft | ConvertTo-Json -Depth 10 -Compress
        throw "Draft generation failed: $json"
    }
} while ($draft.status -ne "REVIEW_READY" -and [DateTimeOffset]::UtcNow -lt $deadline)

if ($draft.status -ne "REVIEW_READY") {
    throw "Draft generation exceeded the ${TimeoutSeconds}-second timeout."
}
foreach ($field in @("generatedTitle", "generatedContent", "changeSummary")) {
    if ([string]::IsNullOrWhiteSpace($draft.$field)) {
        throw "Draft result field is empty: $field"
    }
}
if ($draft.attemptCount -lt 1 -or $draft.attemptCount -gt 3) {
    throw "attemptCount is outside the 1-3 range: $($draft.attemptCount)"
}

[pscustomobject]@{
    Result             = "PASS"
    DraftId            = $draftId
    Status             = $draft.status
    AttemptCount       = $draft.attemptCount
    BaseVersionNumber  = $draft.baseVersionNumber
    GeneratedTitle     = $draft.generatedTitle
    GeneratedLength    = $draft.generatedContent.Length
    ChangeSummary      = $draft.changeSummary
    WarningCount       = @($draft.warnings).Count
} | Format-List
