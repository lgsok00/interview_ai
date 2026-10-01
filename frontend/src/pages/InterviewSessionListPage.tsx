import {
    type InterviewSessionPageResponse,
    type InterviewSessionStatus,
    listInterviewSessions
} from "../api/interviewSessionApi";
import {Link, useSearchParams} from "react-router-dom";
import {useEffect, useState} from "react";
import {ApiError} from "../api/ApiError";

type LoadState =
    | {
    page: number
    revision: number
    data: InterviewSessionPageResponse
}
    | {
    page: number
    revision: number
    error: string
}

const statusLabels: Record<InterviewSessionStatus, string> = {
    GENERATING: '질문 준비 중',
    READY: '질문 준비 완료',
    IN_PROGRESS: '면접 진행 중',
    COMPLETED: '면접 완료',
    FAILED: '질문 생성 실패',
}

const actionLabels: Record<InterviewSessionStatus, string> = {
    GENERATING: '준비 상태 확인',
    READY: '질문 확인·시작',
    IN_PROGRESS: '면접 재개',
    COMPLETED: '결과 보기',
    FAILED: '실패 상태 확인',
}

const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short',
})

function formatDate(value: string): string {
    const date = new Date(value)

    return Number.isNaN(date.getDate())
        ? '날짜 정보 없음'
        : dateFormatter.format(date)
}

export function InterviewSessionListPage() {
    const [searchParams, setSearchParams] = useSearchParams()
    const rawPage = searchParams.get('page') ?? '0'
    const parsedPage = Number(rawPage)
    const page = /^\d+$/.test(rawPage) && Number.isSafeInteger(parsedPage) && parsedPage <= 2147483647
        ? parsedPage
        : 0

    const [revision, setRevision] = useState(0)
    const [loadState, setLoadState] = useState<LoadState | null>(null)

    const currentState = loadState?.page === page && loadState.revision === revision ? loadState : null

    const data = currentState && 'data' in currentState ? currentState.data : null

    const errorMessage = currentState && 'error' in currentState ? currentState.error : ''

    const loading = currentState === null

    useEffect(() => {
        let active = true

        void listInterviewSessions(page)
            .then((result) => {
                if (active) {
                    setLoadState({page, revision, data: result})
                }
            })
            .catch((error: unknown) => {
                if (active) {
                    setLoadState({
                        page,
                        revision,
                        error: error instanceof ApiError
                            ? error.message
                            : '면접 목록을 불러오지 못했습니다. 다시 시도해 주세요.',
                    })
                }
            })

        return () => {
            active = false
        }
    }, [page, revision]);

    function movePage(nextPage: number) {
        setSearchParams((previous) => {
            const next = new URLSearchParams(previous)
            next.set('page', String(nextPage))

            return next
        })
    }

    return (
        <main className="workspace">
            <header className="workspace-header">
                <Link className="brand" to="/">
                    <span className="brand-mark">i</span>
                    <span>interview<span className="brand-accent">AI</span></span>
                </Link>

                <div className="workspace-header-actions">
                    <Link className="secondary-button" to="/growth">
                        성장 분석
                    </Link>
                    <Link className="secondary-button" to="/">
                        홈
                    </Link>
                </div>
            </header>

            <section className="catalog-content">
                <p className="eyebrow">MY INTERVIEWS</p>

                <div className="catalog-title-row">
                    <div>
                        <h1>내 면접 기록</h1>
                        <p className="workspace-intro">
                            진행 중인 면접을 이어가고, 완료한 면접의 결과를 확인하세요.
                        </p>
                    </div>
                    <Link className="secondary-button" to="/catalog">
                        새 면접 준비
                    </Link>
                </div>

                <section className="catalog-panel" aria-label="면접 세션 목록">
                    <div className="session-list-toolbar">
                        <p>
                            {data
                                ? `전체 ${data.totalElements}개 · 최근 생성순`
                                : '최근 생성순'}
                        </p>
                        <button
                            className="secondary-button"
                            type="button"
                            disabled={loading}
                            onClick={() => setRevision((value) => value + 1)}
                        >
                            {errorMessage ? '다시 시도' : '새로고침'}
                        </button>
                    </div>

                    {loading && (
                        <p className="catalog-state" role="status">
                            면접 목록을 불러오는 중입니다.
                        </p>
                    )}

                    {errorMessage && (
                        <p className="form-alert" role="alert">
                            {errorMessage}
                        </p>
                    )}

                    {data && (
                        <>
                            {data.content.length === 0 ? (
                                <div className="catalog-state">
                                    <p>
                                        {data.totalElements === 0
                                            ? '아직 생성한 면접이 없습니다.'
                                            : '이 페이지에 표시할 면접이 없습니다.'}
                                    </p>

                                    {page > 0 ? (
                                        <button
                                            className="secondary-button"
                                            type="button"
                                            onClick={() => movePage(0)}
                                        >
                                            첫 페이지로
                                        </button>
                                    ) : (
                                        <Link className="secondary-button" to="/catalog">
                                            채용공고 둘러보기
                                        </Link>
                                    )}
                                </div>
                            ) : (
                                <div className="catalog-list">
                                    {data.content.map((session) => (
                                        <article
                                            className="catalog-card session-list-card"
                                            key={session.id}
                                        >
                                            <div className="catalog-card-main">
                                                <span
                                                    className="session-status"
                                                    data-status={session.status}
                                                >
                                                    {statusLabels[session.status]}
                                                </span>
                                                <p className="catalog-company">
                                                    {session.companyName}
                                                </p>
                                                <h2>{session.jobPostingTitle}</h2>
                                                <p className="catalog-meta">
                                                    {session.jobRole}
                                                </p>
                                                <p className="catalog-meta">
                                                    생성 {formatDate(session.createdAt)}
                                                </p>
                                                {session.completedAt && (
                                                    <p className="catalog-meta">
                                                        완료 {formatDate(session.completedAt)}
                                                    </p>
                                                )}
                                                {session.status === 'FAILED' && (
                                                    <p className="catalog-meta">
                                                        질문을 준비하지 못했습니다. 상세 상태를 확인해 주세요.
                                                    </p>
                                                )}
                                            </div>

                                            <Link
                                                className="secondary-button session-list-action"
                                                aria-label={`${session.companyName} ${session.jobPostingTitle} 면접 ${session.id}: ${actionLabels[session.status]}`}
                                                to={session.status === 'COMPLETED'
                                                    ? `/interviews/${session.id}/result`
                                                    : `/interviews/${session.id}`}
                                            >
                                                {actionLabels[session.status]}
                                            </Link>
                                        </article>
                                    ))}
                                </div>
                            )}

                            {data.totalPages > 0 && (
                                <nav
                                    className="session-list-pagination"
                                    aria-label="면접 목록 페이지 이동"
                                >
                                    <button
                                        className="secondary-button"
                                        type="button"
                                        disabled={data.first || page === 0}
                                        onClick={() => movePage(page - 1)}
                                    >
                                        이전
                                    </button>
                                    <span>
                                        {page + 1}페이지 · 전체 {data.totalPages}페이지
                                    </span>
                                    <button
                                        className="secondary-button"
                                        type="button"
                                        disabled={data.last}
                                        onClick={() => movePage(page + 1)}
                                    >
                                        다음
                                    </button>
                                </nav>
                            )}
                        </>
                    )}
                </section>
            </section>
        </main>
    )
}