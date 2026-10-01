import {
    getInterviewResult,
    type InterviewEvaluationStatus,
    type InterviewQuestionType,
    type InterviewResult,
    type InterviewResultQuestion,
    requestInterviewAnswerEvaluation,
    retryInterviewAnswerEvaluation
} from "../api/interviewSessionApi";
import {ApiError} from "../api/ApiError";
import {Link, useParams} from "react-router-dom";
import {useEffect, useRef, useState} from "react";

type ResultLoadState =
    | { sessionId: number; result: InterviewResult }
    | { sessionId: number; error: string }

const evaluationLabels: Record<InterviewEvaluationStatus, string> = {
    PENDING: '평가 대기 중',
    PROCESSING: '평가 중',
    COMPLETED: '평가 완료',
    FAILED: '평가 실패',
}

const questionTypeLabels: Record<InterviewQuestionType, string> = {
    TECHNICAL: '기술',
    BEHAVIORAL: '인성',
    FOLLOW_UP: '꼬리 질문',
}

function messageOf(error: unknown): string {
    if (error instanceof ApiError) {
        if (error.code === 'INTERVIEW_RESULT_NOT_READY') {
            return '면접 완료 처리가 아직 반영되지 않았습니다. 잠시 후 다시 시도해 주세요.'
        }

        return error.message
    }

    return '면접 결과를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'
}

function formatScore(score: number | null): string {
    return score === null ? '-' : `${score.toFixed(1)}점`
}

function EvaluationStatus({question}: { question: InterviewResultQuestion }) {
    const evaluation = question.evaluation
    const status = evaluation?.status

    if (!question.answerId || !question.answer) {
        return <span className="result-status result-status-muted">답변 없음</span>
    }

    if (!status) {
        return <span className="result-status result-status-muted">평가 미요청</span>
    }

    return (
        <span className={`result-status result-status-${status.toLowerCase()}`}>
            {evaluationLabels[status]}
        </span>
    )
}

export function InterviewResultPage() {
    const {sessionId: routeSessionId} = useParams()

    return (
        <InterviewResultContent key={routeSessionId ?? 'missing'} sessionId={Number(routeSessionId)}/>
    )
}

function InterviewResultContent({sessionId}: { sessionId: number }) {
    const validSessionId = Number.isSafeInteger(sessionId) && sessionId > 0
    const [loadState, setLoadState] = useState<ResultLoadState | null>(null)
    const [refreshing, setRefreshing] = useState(validSessionId)
    const [refreshVersion, setRefreshVersion] = useState(0)
    const [refreshError, setRefreshError] = useState('')
    const [retryingAnswerId, setRetryingAnswerId] = useState<number | null>(null)
    const [retryErrors, setRetryErrors] = useState<Record<number, string>>({})
    const retryLock = useRef(false)
    const pageVersion = useRef(0)

    useEffect(() => {
        pageVersion.current += 1

        return () => {
            pageVersion.current += 1
        }
    }, [])

    const currentState = loadState?.sessionId === sessionId ? loadState : null
    const result = currentState && 'result' in currentState ? currentState.result : null
    const errorMessage = !validSessionId
        ? '면접 세션 주소가 올바르지 않습니다.'
        : currentState && 'error' in currentState
            ? currentState.error
            : ''
    const loading = validSessionId && currentState === null

    function loadResult() {
        if (!validSessionId) return

        setRefreshing(true)
        setRefreshVersion((current) => current + 1)
    }

    useEffect(() => {
        if (!validSessionId || retryingAnswerId !== null) return

        let active = true
        let timeout: ReturnType<typeof setTimeout> | undefined

        async function refreshResult() {
            try {
                const response = await getInterviewResult(sessionId)
                if (!active) return

                setRefreshError('')
                setLoadState({sessionId, result: response})

                if (response.pendingEvaluationCount + response.processingEvaluationCount > 0) {
                    timeout = setTimeout(() => void refreshResult(), 3000)
                }

            } catch (error) {
                if (!active) return

                const message = messageOf(error)
                setRefreshError(message)
                setLoadState((current) => {
                    if (current?.sessionId === sessionId && 'result' in current) {
                        return current
                    }

                    return {sessionId, error: message}
                })

            } finally {
                if (active) {
                    setRefreshing(false)
                }
            }
        }

        void refreshResult()

        return () => {
            active = false
            if (timeout) clearTimeout(timeout)
        }
    }, [sessionId, validSessionId, refreshVersion, retryingAnswerId]);

    async function handleRequestEvaluation(question: InterviewResultQuestion) {
        const answerId = question.answerId

        if (!answerId || question.evaluation?.status || retryLock.current) {
            return
        }

        const version = pageVersion.current
        retryLock.current = true

        setRetryingAnswerId(answerId)
        setRetryErrors((current) => ({...current, [answerId]: ''}))

        try {
            await requestInterviewAnswerEvaluation(sessionId, answerId)

        } catch (error) {
            if (pageVersion.current !== version) return

            setRetryErrors((current) => ({
                ...current,
                [answerId]: error instanceof ApiError
                    ? error.message
                    : '평가 요청 결과를 확인하지 못했습니다. 새로고침 후 상태를 확인해 주세요.',
            }))

        } finally {
            if (pageVersion.current === version) {
                retryLock.current = false
                setRetryingAnswerId(null)
                loadResult()
            }
        }
    }

    async function handleRetryEvaluation(question: InterviewResultQuestion) {
        const answerId = question.answerId

        if (!answerId || question.evaluation?.status !== 'FAILED' || retryLock.current) {
            return
        }

        const version = pageVersion.current
        retryLock.current = true

        setRetryingAnswerId(answerId)
        setRetryErrors((current) => ({...current, [answerId]: ''}))

        try {
            const accepted = await retryInterviewAnswerEvaluation(sessionId, answerId)
            if (pageVersion.current !== version) return

            setLoadState((current) => {
                if (current?.sessionId !== sessionId || !('result' in current)) {
                    return current
                }

                const previous = current.result
                const target = previous.questions.find((item) => item.answerId === answerId)

                if (target?.evaluation?.status !== 'FAILED') return current

                return {
                    sessionId,
                    result: {
                        ...previous,
                        analysisStatus: 'PARTIAL',
                        failedEvaluationCount: previous.failedEvaluationCount - 1,
                        pendingEvaluationCount: previous.pendingEvaluationCount + 1,
                        questions: previous.questions.map((item) =>
                            item.answerId === answerId
                                ? {
                                    ...item,
                                    evaluation: {
                                        status: accepted.status,
                                        starScore: accepted.starScore,
                                        logicScore: accepted.logicScore,
                                        jobFitScore: accepted.jobFitScore,
                                        averageScore: null,
                                        strengths: accepted.strengths,
                                        improvements: accepted.improvements,
                                        improvedAnswer: accepted.improvedAnswer,
                                        failureCode: accepted.failureCode,
                                        completedAt: accepted.completedAt,
                                    },
                                }
                                : item,
                        ),
                    },
                }
            })

        } catch (error) {
            if (pageVersion.current !== version) return

            setRetryErrors((current) => ({
                ...current,
                [answerId]: error instanceof ApiError
                    ? error.message
                    : "재요청 결과를 확인하지 못했습니다. 결과를 새로고침한 뒤 다시 시도해 주세요.",
            }))

        } finally {
            if (pageVersion.current === version) {
                retryLock.current = false
                setRetryingAnswerId(null)
                loadResult()
            }
        }
    }

    return (
        <main className="workspace interview-status-workspace">
            <header className="workspace-header">
                <Link className="brand" to="/">
                    <span className="brand-mark">i</span>
                    <span>interview<span className="brand-accent">AI</span></span>
                </Link>
                <div className="workspace-header-actions">
                    <Link className="secondary-button" to="/catalog">채용공고</Link>
                </div>
            </header>

            <section className="interview-status-content">
                <p className="eyebrow">INTERVIEW RESULT</p>
                <h1>면접 결과를 확인해요.</h1>
                <p className="workspace-intro">
                    평가 상태와 답변별 강점, 개선 제안을 살펴보세요.
                </p>

                <section className="interview-status-card" aria-live="polite">
                    {loading && <p role="status">면접 결과를 불러오고 있습니다.</p>}

                    {errorMessage && (
                        <p className="form-alert" role="alert">{errorMessage}</p>
                    )}

                    {refreshError && result && (
                        <p className="form-alert" role="alert">
                            {refreshError}
                            {' '}기존 결과를 표시하고 있습니다. 결과를 새로고침해 주세요.
                        </p>
                    )}

                    {errorMessage && (
                        <button
                            className="secondary-button"
                            type="button"
                            disabled={refreshing || retryingAnswerId !== null}
                            onClick={loadResult}
                        >
                            {refreshing ? '불러오는 중…' : '결과 다시 불러오기'}
                        </button>
                    )}

                    {result && (
                        <>
                            <div className="result-heading">
                                <div>
                                    <span className="status-pill status-completed">면접 완료</span>
                                    <h2>{result.session.jobPostingTitle}</h2>
                                    <p className="catalog-company">
                                        {result.session.companyName} · {result.session.jobRole}
                                    </p>
                                </div>
                                <button
                                    className="secondary-button"
                                    type="button"
                                    onClick={() => void loadResult()}
                                    disabled={refreshing || retryingAnswerId !== null}
                                >
                                    {refreshing ? '새로고침 중…' : '결과 새로고침'}
                                </button>
                            </div>

                            <section className="result-summary" aria-label="평가 요약">
                                <div className="result-summary-main">
                                    <span>종합 점수</span>
                                    <strong>{formatScore(result.overall.averageScore)}</strong>
                                    <small>평가 완료 답변 {result.completedEvaluationCount} / {result.answerCount}</small>
                                </div>
                                <ScoreItem label="STAR 구성" score={result.overall.starScore}/>
                                <ScoreItem label="논리성" score={result.overall.logicScore}/>
                                <ScoreItem label="직무 적합성" score={result.overall.jobFitScore}/>
                            </section>

                            {(result.pendingEvaluationCount > 0 || result.processingEvaluationCount > 0) && (
                                <p className="result-notice" role="status">
                                    평가가 진행 중입니다. 완료되는 대로 화면을 자동 갱신합니다.
                                    대기 {result.pendingEvaluationCount}건 · 처리 중 {result.processingEvaluationCount}건
                                </p>
                            )}

                            {result.failedEvaluationCount > 0 && (
                                <p className="result-notice result-notice-warning" role="status">
                                    평가에 실패한 답변이 {result.failedEvaluationCount}건 있습니다.
                                    아래 답변별 상태와 오류 코드를 확인해 주세요.
                                </p>
                            )}

                            {result.notRequestedEvaluationCount > 0 && (
                                <p className="result-notice" role="status">
                                    평가가 요청되지 않은 답변이 {result.notRequestedEvaluationCount}건 있습니다.
                                </p>
                            )}

                            {result.overall.sampleCount === 0 && result.answerCount > 0 && (
                                <p className="result-notice">
                                    아직 완료된 평가가 없어 종합 점수를 표시할 수 없습니다.
                                </p>
                            )}

                            <section className="result-question-section">
                                <p className="eyebrow">ANSWER REVIEW</p>
                                <h2>질문별 답변과 개선사항</h2>

                                {result.questions.length === 0 ? (
                                    <p>이 면접에 등록된 질문이 없습니다.</p>
                                ) : (
                                    <ol className="result-question-list">
                                        {result.questions.map((question) => (
                                            <li key={question.questionId} className="result-question-card">
                                                <div className="result-question-heading">
                                                    <div>
                                                        <span className="question-number">질문 {question.sequence}</span>
                                                        <span className="question-type">
                                                            {questionTypeLabels[question.questionType]}
                                                        </span>
                                                    </div>
                                                    <EvaluationStatus question={question}/>
                                                </div>

                                                <h3>{question.question}</h3>

                                                {question.answer ? (
                                                    <div className="result-answer-block">
                                                        <span>내 답변</span>
                                                        <p>{question.answer}</p>
                                                    </div>
                                                ) : (
                                                    <p className="result-empty">제출된 답변이 없습니다.</p>
                                                )}

                                                {question.evaluation?.status === 'COMPLETED' && (
                                                    <EvaluationDetails question={question}/>
                                                )}

                                                {question.answerId !== null
                                                    && question.answer
                                                    && !question.evaluation?.status && (
                                                        <button
                                                            className="secondary-button"
                                                            type="button"
                                                            disabled={retryingAnswerId !== null || refreshing}
                                                            onClick={() => void handleRequestEvaluation(question)}
                                                        >
                                                            {retryingAnswerId === question.answerId
                                                                ? '평가 요청 중…'
                                                                : '답변 평가 요청'}
                                                        </button>
                                                    )
                                                }

                                                {question.evaluation?.status === 'FAILED' && (
                                                    <div>
                                                        <p className="result-failure" role="status">
                                                            평가 실패
                                                            {question.evaluation.failureCode
                                                                ? ` · ${question.evaluation.failureCode}`
                                                                : ''}
                                                        </p>

                                                        {question.answerId !== null && (
                                                            <button
                                                                className="secondary-button"
                                                                type="button"
                                                                disabled={retryingAnswerId !== null || refreshing}
                                                                onClick={() => void handleRetryEvaluation(question)}
                                                            >
                                                                {retryingAnswerId === question.answerId
                                                                    ? '재시도 요청 중…'
                                                                    : '답변 평가 다시 시도'}
                                                            </button>
                                                        )}
                                                    </div>
                                                )}

                                                {question.answerId !== null
                                                    && retryErrors[question.answerId] && (
                                                        <p className="form-alert" role="alert">
                                                            {retryErrors[question.answerId]}
                                                        </p>
                                                    )}
                                            </li>
                                        ))}
                                    </ol>
                                )}
                            </section>
                        </>
                    )}

                    <div className="result-footer-actions">
                        <Link className="secondary-button" to="/">홈으로</Link>
                        <Link className="primary-button" to="/catalog">다른 공고 찾아보기</Link>
                    </div>
                </section>
            </section>
        </main>
    )
}

function ScoreItem({label, score}: { label: string; score: number | null }) {
    return (
        <div className="result-score-item">
            <span>{label}</span>
            <strong>{formatScore(score)}</strong>
        </div>
    )
}

function EvaluationDetails({question}: { question: InterviewResultQuestion }) {
    const evaluation = question.evaluation
    if (!evaluation || evaluation.status !== 'COMPLETED') return null

    return (
        <div className="result-evaluation">
            <div className="result-dimension-scores">
                <ScoreItem label="STAR" score={evaluation.starScore}/>
                <ScoreItem label="논리성" score={evaluation.logicScore}/>
                <ScoreItem label="직무 적합성" score={evaluation.jobFitScore}/>
            </div>

            {evaluation.strengths && (
                <div className="result-feedback">
                    <h4>잘한 점</h4>
                    <p>{evaluation.strengths}</p>
                </div>
            )}

            {evaluation.improvements && (
                <div className="result-feedback result-feedback-improve">
                    <h4>개선할 점</h4>
                    <p>{evaluation.improvements}</p>
                </div>
            )}

            {evaluation.improvedAnswer && (
                <details className="result-improved-answer">
                    <summary>개선 답변 예시 보기</summary>
                    <p>{evaluation.improvedAnswer}</p>
                </details>
            )}
        </div>
    )
}
