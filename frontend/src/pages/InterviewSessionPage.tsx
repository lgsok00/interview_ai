import {
    getInterviewQuestions,
    getInterviewSession,
    type InterviewQuestion,
    type InterviewSession,
    type InterviewSessionStatus,
    startInterviewSession,
} from "../api/interviewSessionApi";
import {ApiError} from "../api/ApiError";
import {Link, useParams} from "react-router-dom";
import {useEffect, useState} from "react";

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
    const sessionId = Number(routeSessionId)
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
    const [starting, setStarting] = useState(false)
    const [actionError, setActionError] = useState('')

    useEffect(() => {
        if (!validSessionId) return

        let active = true
        let timeout: ReturnType<typeof setTimeout> | undefined

        async function loadSession() {
            try {
                const result = await getInterviewSession(sessionId)
                if (!active) return

                if (result.status === 'READY') {
                    const questionResults = await getInterviewQuestions(sessionId)
                    if (!active) return

                    setQuestions(questionResults)
                }

                setLoadState({sessionId, session: result})

                if (result.status === 'GENERATING') {
                    timeout = setTimeout(() => void loadSession(), 2000)
                }

            } catch (error) {
                if (active) {
                    setLoadState({sessionId, error: messageOf(error)})
                }
            }
        }

        void loadSession()

        return () => {
            active = false
            if (timeout) clearTimeout(timeout)
        }
    }, [sessionId, validSessionId])

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
                <p className="eyebrow">INTERVIEW PREPARATION</p>
                <h1>면접 질문을 준비하고 있어요.</h1>
                <p className="workspace-intro">
                    공고와 내 자료를 바탕으로 연습 질문을 만들고 있습니다.
                </p>

                <section className="interview-status-card" aria-live="polite">
                    {loading && (
                        <p role="status">면접 세션 상태를 확인하고 있습니다.</p>
                    )}

                    {errorMessage && (
                        <p className="form-alert" role="alert">{errorMessage}</p>
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
                                <section className="interview-question-preview"
                                         aria-labelledby="question-preview-title">
                                    <div>
                                        <p className="eyebrow">YOUR QUESTIONS</p>
                                        <h2 id="question-preview-title">이번 면접 질문</h2>
                                        <p>질문을 먼저 확인한 뒤 준비가 되면 면접을 시작하세요.</p>
                                    </div>

                                    {questions.length === 0 ? (
                                        <p role="status">질문을 불러오지 못했거나 아직 준비되지 않았습니다. 새로고침해 주세요.</p>
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

                                    {actionError && <p className="form-alert" role="alert">{actionError}</p>}

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
                                <p role="alert">
                                    질문을 만들지 못했습니다.
                                    {session.failureCode && ` 오류 코드: ${session.failureCode}`}
                                </p>
                            )}

                            {session.status === 'IN_PROGRESS' && (
                                <p role="status">이 면접 세션은 진행 중입니다.</p>
                            )}

                            {session.status === 'COMPLETED' && (
                                <p role="status">이 면접 세션은 완료되었습니다.</p>
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