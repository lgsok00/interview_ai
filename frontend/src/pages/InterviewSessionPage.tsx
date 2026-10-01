import {
    completeInterviewSession,
    generateInterviewFollowUp,
    getInterviewAnswers,
    getInterviewQuestions,
    getInterviewSession,
    type InterviewAnswer,
    type InterviewQuestion,
    type InterviewSession,
    type InterviewSessionStatus,
    retryInterviewGeneration,
    startInterviewSession,
    submitInterviewAnswer,
} from "../api/interviewSessionApi";
import {ApiError} from "../api/ApiError";
import {Link, useParams} from "react-router-dom";
import {useEffect, useRef, useState} from "react";

type SessionLoadState =
    | { sessionId: number; session: InterviewSession }
    | { sessionId: number; error: string }

const statusLabels: Record<InterviewSessionStatus, string> = {
    GENERATING: '질문 준비 중',
    READY: '질문 준비 완료',
    IN_PROGRESS: '면접 진행 중',
    COMPLETED: '면접 완료',
    FAILED: '질문 생성 실패',
}

function messageOf(error: unknown): string {
    if (error instanceof ApiError) return error.message

    return '면접 세션을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'
}

export function InterviewSessionPage() {
    const {sessionId: routeSessionId} = useParams()

    return (
        <InterviewSessionContent key={routeSessionId ?? 'missing'} sessionId={Number(routeSessionId)}/>
    )
}

function InterviewSessionContent({sessionId}: { sessionId: number }) {
    const validSessionId = Number.isSafeInteger(sessionId) && sessionId > 0
    const [loadState, setLoadState] = useState<SessionLoadState | null>(null)

    const currentState = loadState?.sessionId === sessionId ? loadState : null
    const session = currentState && 'session' in currentState ? currentState.session : null
    const errorMessage = !validSessionId
        ? '면접 세션 주소가 올바르지 않습니다.'
        : currentState && 'error' in currentState
            ? currentState.error
            : ''
    const loading = validSessionId && currentState === null

    const [questions, setQuestions] = useState<InterviewQuestion[]>([])
    const [answers, setAnswers] = useState<InterviewAnswer[]>([])
    const [drafts, setDrafts] = useState<Record<number, string>>({})
    const [starting, setStarting] = useState(false)
    const [completing, setCompleting] = useState(false)
    const [answeringQuestionId, setAnsweringQuestionId] = useState<number | null>(null)
    const [actionError, setActionError] = useState('')

    const [refreshVersion, setRefreshVersion] = useState(0)
    const [retryingGeneration, setRetryingGeneration] = useState(false)
    const [generationRetryError, setGenerationRetryError] = useState('')
    const [sessionRefreshError, setSessionRefreshError] = useState('')
    const generationRetryLock = useRef(false)
    const pageVersion = useRef(0)

    useEffect(() => {
        pageVersion.current += 1

        return () => {
            pageVersion.current += 1
        }
    }, [])

    useEffect(() => {
        if (!validSessionId || retryingGeneration) return

        let active = true
        let timeout: ReturnType<typeof setTimeout> | undefined

        async function loadSession() {
            try {
                const result = await getInterviewSession(sessionId)
                if (!active) return

                if (result.status === 'READY' || result.status === 'IN_PROGRESS') {
                    const questionResults = await getInterviewQuestions(sessionId)
                    if (!active) return

                    setQuestions(questionResults)

                    if (result.status === 'IN_PROGRESS') {
                        const answerResults = await getInterviewAnswers(sessionId)
                        if (!active) return

                        setAnswers(answerResults)
                    }
                }

                setSessionRefreshError('')
                setLoadState({sessionId, session: result})

                if (result.status === 'GENERATING') {
                    timeout = setTimeout(() => void loadSession(), 2000)
                }

            } catch (error) {
                if (!active) return

                const message = messageOf(error)
                setSessionRefreshError(message)
                setLoadState((current) => {
                    if (current?.sessionId === sessionId && 'session' in current) {
                        return current
                    }

                    return {sessionId, error: message}
                })
            }
        }

        void loadSession()

        return () => {
            active = false
            if (timeout) clearTimeout(timeout)
        }
    }, [sessionId, validSessionId, refreshVersion, retryingGeneration])

    async function handleRetryGeneration() {
        if (session?.status !== 'FAILED' || generationRetryLock.current) return

        const version = pageVersion.current
        generationRetryLock.current = true
        setRetryingGeneration(true)
        setGenerationRetryError('')

        try {
            await retryInterviewGeneration(sessionId)
            if (pageVersion.current !== version) return

            setLoadState({
                sessionId,
                session: {...session, status: 'GENERATING', failureCode: null},
            })

            setRefreshVersion((current) => current + 1)

        } catch (error) {
            if (pageVersion.current !== version) return

            setGenerationRetryError(
                error instanceof ApiError
                    ? error.message
                    : '재요청 결과를 확인하지 못했습니다. 상태를 새로고침한 뒤 다시 시도해 주세요.'
            )

            setRefreshVersion((current) => current + 1)

        } finally {
            if (pageVersion.current === version) {
                generationRetryLock.current = false
                setRetryingGeneration(false)
            }
        }
    }

    async function handleStartInterview() {
        setStarting(true)
        setActionError('')

        try {
            const result = await startInterviewSession(sessionId)
            setLoadState({sessionId, session: result})

        } catch (error) {
            setActionError(messageOf(error))

        } finally {
            setStarting(false)
        }
    }

    async function handleSubmitAnswer(question: InterviewQuestion) {
        const content = drafts[question.id]?.trim() ?? ''
        if (!content) return

        setAnsweringQuestionId(question.id)
        setActionError('')

        try {
            const savedAnswer = await submitInterviewAnswer(sessionId, question.id, content)

            setAnswers((current) => [
                ...current.filter((answer) => answer.questionId !== savedAnswer.questionId),
                savedAnswer,
            ])
            setDrafts((current) => ({...current, [question.id]: ''}))

            if (question.questionType !== 'FOLLOW_UP' && !savedAnswer.followUpQuestionId) {
                const result = await generateInterviewFollowUp(sessionId, question.id)

                setQuestions((current) => {
                    if (current.some((item) => item.id === result.question.id)) {
                        return current
                    }

                    return [...current, result.question]
                })
                setAnswers((current) => current.map((answer) =>
                    answer.questionId === question.id
                        ? {...answer, followUpQuestionId: result.question.id}
                        : answer,
                ))
            }

        } catch (error) {
            setActionError(messageOf(error))

        } finally {
            setAnsweringQuestionId(null)
        }
    }

    async function handleCompleteInterview() {
        setCompleting(true)
        setActionError('')

        try {
            const result = await completeInterviewSession(sessionId)
            setLoadState({sessionId, session: result})

        } catch (error) {
            setActionError(messageOf(error))

        } finally {
            setCompleting(false)
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
                <p className="eyebrow">INTERVIEW PRACTICE</p>
                <h1>
                    {session?.status === 'IN_PROGRESS'
                        ? '답변을 이어가 볼까요?'
                        : '면접 질문을 준비하고 있어요.'}
                </h1>
                <p className="workspace-intro">
                    {session?.status === 'IN_PROGRESS'
                        ? '답변을 제출하면 초기 질문에 대한 꼬리 질문이 이어집니다.'
                        : '공고와 내 자료를 바탕으로 연습 질문을 만들고 있습니다.'}
                </p>

                <section className="interview-status-card" aria-live="polite">
                    {loading && (
                        <p role="status">면접 세션 상태를 확인하고 있습니다.</p>
                    )}

                    {errorMessage && (
                        <p className="form-alert" role="alert">{errorMessage}</p>
                    )}

                    {sessionRefreshError && session && (
                        <p className="form-alert" role="alert">
                            {sessionRefreshError}
                        </p>
                    )}

                    {(errorMessage || sessionRefreshError || session?.status === 'FAILED') && (
                        <button
                            className="secondary-button"
                            type="button"
                            disabled={retryingGeneration || loading}
                            onClick={() => {
                                setSessionRefreshError('')
                                setRefreshVersion((current) => current + 1)
                            }}
                        >
                            상태 새로고침
                        </button>
                    )}

                    {session && (
                        <>
                            <span className={`status-pill status-${session.status.toLowerCase()}`}>
                                {statusLabels[session.status]}
                            </span>
                            <h2>{session.jobPostingTitle}</h2>
                            <p className="catalog-company">
                                {session.companyName} · {session.jobRole}
                            </p>

                            {session.status === 'GENERATING' && (
                                <p role="status">
                                    질문을 만드는 중입니다. 완료되면 이 화면이 자동으로 갱신됩니다.
                                </p>
                            )}

                            {session.status === 'READY' && (
                                <section
                                    className="interview-question-preview"
                                    aria-labelledby="question-preview-title"
                                >
                                    <div>
                                        <p className="eyebrow">YOUR QUESTIONS</p>
                                        <h2 id="question-preview-title">이번 면접 질문</h2>
                                        <p>질문을 먼저 확인한 뒤 준비가 되면 면접을 시작하세요.</p>
                                    </div>

                                    {questions.length === 0 ? (
                                        <p role="status">
                                            질문을 불러오지 못했거나 아직 준비되지 않았습니다. 새로고침해 주세요.
                                        </p>
                                    ) : (
                                        <ol className="interview-question-list">
                                            {questions.map((question) => (
                                                <li key={question.id}>
                                                    <span className="question-number">
                                                        질문 {question.sequenceNumber}
                                                    </span>
                                                    <span className="question-type">
                                                        {question.questionType === 'TECHNICAL' ? '기술' :
                                                            question.questionType === 'BEHAVIORAL' ? '인성' : '꼬리 질문'}
                                                    </span>
                                                    <p>{question.content}</p>
                                                </li>
                                            ))}
                                        </ol>
                                    )}

                                    {actionError && (
                                        <p className="form-alert" role="alert">{actionError}</p>
                                    )}

                                    <button
                                        className="primary-button interview-start-button"
                                        type="button"
                                        onClick={() => void handleStartInterview()}
                                        disabled={starting || questions.length === 0}
                                    >
                                        {starting ? '면접을 시작하는 중…' : '면접 시작하기'}
                                    </button>
                                </section>
                            )}

                            {session.status === 'FAILED' && (
                                <div>
                                    <p role="alert">
                                        질문을 만들지 못했습니다.
                                        {session.failureCode && ` 오류 코드: ${session.failureCode}`}
                                    </p>

                                    {generationRetryError && (
                                        <p className="form-alert" role="alert">
                                            {generationRetryError}
                                        </p>
                                    )}

                                    <button
                                        className="primary-button"
                                        type="button"
                                        onClick={() => void handleRetryGeneration()}
                                        disabled={retryingGeneration}
                                    >
                                        {retryingGeneration ? '재시도 요청 중…' : '질문 생성 다시 시도'}
                                    </button>
                                </div>
                            )}

                            {session.status === 'IN_PROGRESS' && (
                                <section
                                    className="interview-question-preview"
                                    aria-labelledby="live-interview-title"
                                >
                                    <div>
                                        <p className="eyebrow">INTERVIEW IN PROGRESS</p>
                                        <h2 id="live-interview-title">질문에 답변해 주세요</h2>
                                        <p>답변은 질문별로 저장되고 초기 질문 뒤에 꼬리 질문이 이어집니다.</p>
                                    </div>

                                    {questions.length === 0 ? (
                                        <p role="status">질문을 불러오고 있습니다.</p>
                                    ) : (
                                        <ol className="interview-question-list">
                                            {questions.map((question) => {
                                                const answer = answers.find(
                                                    (item) => item.questionId === question.id,
                                                )
                                                const isFollowUp = question.questionType === 'FOLLOW_UP'
                                                const pending = answeringQuestionId === question.id

                                                return (
                                                    <li key={question.id}>
                                                        <span className="question-number">
                                                            질문 {question.sequenceNumber}
                                                        </span>
                                                        <span className="question-type">
                                                            {isFollowUp ? '꼬리 질문' :
                                                                question.questionType === 'TECHNICAL'
                                                                    ? '기술'
                                                                    : '인성'}
                                                        </span>
                                                        <p>{question.content}</p>

                                                        {answer ? (
                                                            <>
                                                                <p className="interview-answer-label">내 답변</p>
                                                                <p className="interview-answer">
                                                                    {answer.content}
                                                                </p>
                                                                {!isFollowUp && !answer.followUpQuestionId && (
                                                                    <>
                                                                        {actionError && (
                                                                            <p className="form-alert" role="alert">
                                                                                {actionError}
                                                                            </p>
                                                                        )}
                                                                        <button
                                                                            className="secondary-button"
                                                                            type="button"
                                                                            disabled={pending}
                                                                            onClick={() => {
                                                                                setDrafts((current) => ({
                                                                                    ...current,
                                                                                    [question.id]: answer.content,
                                                                                }))
                                                                                void generateInterviewFollowUp(
                                                                                    sessionId,
                                                                                    question.id,
                                                                                ).then((result) => {
                                                                                    setQuestions((current) =>
                                                                                        current.some(
                                                                                            (item) => item.id === result.question.id,
                                                                                        )
                                                                                            ? current
                                                                                            : [...current, result.question],
                                                                                    )
                                                                                    setAnswers((current) =>
                                                                                        current.map((item) =>
                                                                                            item.questionId === question.id
                                                                                                ? {
                                                                                                    ...item,
                                                                                                    followUpQuestionId: result.question.id,
                                                                                                }
                                                                                                : item,
                                                                                        ),
                                                                                    )
                                                                                    setActionError('')
                                                                                }).catch((error: unknown) => {
                                                                                    setActionError(messageOf(error))
                                                                                })
                                                                            }}
                                                                        >
                                                                            꼬리 질문 다시 만들기
                                                                        </button>
                                                                    </>
                                                                )}
                                                            </>
                                                        ) : (
                                                            <div className="interview-answer-form">
                                                                <label htmlFor={`answer-${question.id}`}>
                                                                    답변
                                                                </label>
                                                                <textarea
                                                                    id={`answer-${question.id}`}
                                                                    value={drafts[question.id] ?? ''}
                                                                    maxLength={10000}
                                                                    rows={5}
                                                                    placeholder="경험과 판단 근거를 구체적으로 작성해 주세요."
                                                                    onChange={(event) => setDrafts((current) => ({
                                                                        ...current,
                                                                        [question.id]: event.target.value,
                                                                    }))}
                                                                />
                                                                <div className="interview-answer-actions">
                                                                    <span>
                                                                        {(drafts[question.id] ?? '').length} / 10,000자
                                                                    </span>
                                                                    <button
                                                                        className="primary-button"
                                                                        type="button"
                                                                        disabled={pending || !drafts[question.id]?.trim()}
                                                                        onClick={() => void handleSubmitAnswer(question)}
                                                                    >
                                                                        {pending ? '저장 중…' : '답변 제출'}
                                                                    </button>
                                                                </div>
                                                            </div>
                                                        )}
                                                    </li>
                                                )
                                            })}
                                        </ol>
                                    )}

                                    {actionError && (
                                        <p className="form-alert" role="alert">{actionError}</p>
                                    )}

                                    <button
                                        className="primary-button interview-start-button"
                                        type="button"
                                        onClick={() => void handleCompleteInterview()}
                                        disabled={completing || answeringQuestionId !== null}
                                    >
                                        {completing ? '면접을 마치는 중…' : '면접 완료하기'}
                                    </button>
                                </section>
                            )}

                            {session.status === 'COMPLETED' && (
                                <section className="interview-question-preview"
                                         aria-labelledby="interview-completed-title">
                                    <div>
                                        <p className="eyebrow">PRACTICE COMPLETE</p>
                                        <h2 id="interview-completed-title">수고하셨습니다.</h2>
                                        <p>답변별 평가와 개선사항을 확인해 다음 연습에 활용해 보세요.</p>
                                    </div>
                                    <Link className="primary-button interview-start-button"
                                          to={`/interviews/${session.id}/result`}>
                                        면접 결과 보기
                                    </Link>
                                </section>
                            )}
                        </>
                    )}

                    <Link className="secondary-button interview-back-link" to="/catalog">
                        공고 목록으로 돌아가기
                    </Link>
                </section>
            </section>
        </main>
    )
}