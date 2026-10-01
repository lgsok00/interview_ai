import {
    applyDraft,
    type ApplyDraftInput,
    createDraft,
    type DraftDetails,
    type DraftStatus,
    type DraftSummary,
    getDraft,
    listDrafts,
    regenerateDraft
} from "../api/coverLetterDraftApi";
import {ApiError} from "../api/ApiError";
import {type JobPostingSummary, listJobPostings, type Page} from "../api/catalogApi";
import {type SubmitEvent, useCallback, useEffect, useRef, useState} from "react";
import {type CoverLetterDetails, getCoverLetter, listResumes, type ResumeSummary} from "../api/documentsApi";
import {Link, useParams} from "react-router-dom";

const statusLabels: Record<DraftStatus, string> = {
    PENDING: '생성 대기',
    RUNNING: '생성 중',
    REVIEW_READY: '검토 가능',
    FAILED: '생성 실패',
    APPLIED: '적용 완료',
}

function messageOf(error: unknown): string {
    if (error instanceof ApiError) {
        if (error.code === 'DRAFT_BASE_VERSION_CONFLICT') {
            return '초안 생성 후 자기소개서가 변경되었습니다. 검토 내용은 유지됩니다. 최신 버전으로 재생성하거나 문서 화면에서 직접 수정해 주세요.'
        }

        if (error.code === 'REPRESENTATIVE_RESUME_NOT_READY') {
            return '사용할 이력서가 준비되지 않았습니다. 추출 완료된 이력서를 선택하거나 문서 화면에서 대표 이력서를 설정해 주세요.'
        }

        return error.message
    }

    return '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'
}

function generating(status: DraftStatus): boolean {
    return status === 'PENDING' || status === 'RUNNING'
}

interface JobPickerProps {
    selected: JobPostingSummary | null
    onSelect: (job: JobPostingSummary) => void
}

function JobPicker({selected, onSelect}: JobPickerProps) {
    const [keyword, setKeyword] = useState('')
    const [query, setQuery] = useState({keyword: '', page: 0, revision: 0})
    const [state, setState] = useState<{
        key: string
        result?: Page<JobPostingSummary>
        error?: string
    } | null>(null)

    const queryKey = JSON.stringify(query)
    const current = state?.key === queryKey ? state : null

    useEffect(() => {
        let active = true

        void listJobPostings({
            keyword: query.keyword,
            page: query.page,
            size: 10,
        })
            .then((result) => {
                if (active) setState({key: queryKey, result})
            })
            .catch((error: unknown) => {
                if (active) setState({key: queryKey, error: messageOf(error)})
            })

        return () => {
            active = false
        }
    }, [query.keyword, query.page, queryKey])

    return (
        <section className="draft-job-picker" aria-label="채용공고 선택">
            <label>
                공고 검색
                <input
                    value={keyword}
                    maxLength={100}
                    placeholder="기업명·공고명·직무 검색"
                    onChange={(event) => setKeyword(event.target.value)}
                />
            </label>
            <div className="documents-actions">
                <button
                    type="button"
                    onClick={() => setQuery({
                        keyword: keyword.trim(),
                        page: 0,
                        revision: query.revision + 1,
                    })}
                >
                    검색
                </button>
            </div>

            {selected && (
                <p role="status">
                    선택: {selected.companyName} · {selected.title}
                </p>
            )}

            {!current && <p>공고를 불러오는 중입니다.</p>}
            {current?.error && (
                <div>
                    <p className="form-alert" role="alert">{current.error}</p>
                    <button
                        type="button"
                        onClick={() => setQuery({
                            ...query,
                            revision: query.revision + 1,
                        })}
                    >
                        다시 조회
                    </button>
                </div>
            )}

            {current?.result && (
                <>
                    {current.result.items.length === 0 && (
                        <p>검색 결과가 없습니다.</p>
                    )}
                    <ul className="documents-list">
                        {current.result.items.map((job) => (
                            <li className="documents-card" key={job.id}>
                                <h3>{job.companyName} · {job.title}</h3>
                                <p>{job.jobRole}</p>
                                <div className="documents-actions">
                                    <button
                                        type="button"
                                        aria-pressed={selected?.id === job.id}
                                        onClick={() => onSelect(job)}
                                    >
                                        {selected?.id === job.id ? '선택됨' : '선택'}
                                    </button>
                                </div>
                            </li>
                        ))}
                    </ul>
                    <div className="documents-actions">
                        <button
                            type="button"
                            disabled={query.page === 0}
                            onClick={() => setQuery({...query, page: query.page - 1})}
                        >
                            이전
                        </button>
                        <span>
                            {query.page + 1} / {Math.max(current.result.totalPages, 1)}
                        </span>
                        <button
                            type="button"
                            disabled={query.page + 1 >= current.result.totalPages}
                            onClick={() => setQuery({...query, page: query.page + 1})}
                        >
                            다음
                        </button>
                    </div>
                </>
            )}
        </section>
    )
}

interface DraftReviewProps {
    coverLetterId: number
    draftId: number
    onDraft: (draft: DraftDetails) => void
    onCreated: (draft: DraftDetails) => void
    onApplied: (draft: CoverLetterDetails) => void
}

function DraftReview({coverLetterId, draftId, onDraft, onCreated, onApplied}: DraftReviewProps) {
    const [draft, setDraft] = useState<DraftDetails | null>(null)
    const [form, setForm] = useState<ApplyDraftInput | null>(null)
    const [revision, setRevision] = useState(0)
    const [error, setError] = useState('')
    const [busy, setBusy] = useState(false)
    const actionLock = useRef(false)

    useEffect(() => {
        let active = true
        let timer: ReturnType<typeof setTimeout> | undefined

        async function load() {
            try {
                const response = await getDraft(coverLetterId, draftId)
                if (!active) return

                setDraft(response)
                setError('')
                onDraft(response)

                if (response.status === 'REVIEW_READY') {
                    setForm((previous) => previous ?? {
                        title: response.generatedTitle ?? '',
                        content: response.generatedContent ?? '',
                    })
                }

                if (generating(response.status)) {
                    timer = setTimeout(() => void load(), 3000)
                }

            } catch (loadError) {
                if (active) setError(messageOf(loadError))
            }
        }

        void load()

        return () => {
            active = false
            if (timer !== undefined) clearTimeout(timer)
        }
    }, [coverLetterId, draftId, revision, onDraft])

    async function regenerate() {
        if (actionLock.current) return

        if (form && draft && (
            form.title !== draft.generatedTitle
            || form.content !== draft.generatedContent
        )) {
            if (!window.confirm('검토 중인 수정 내용을 적용하지 않고 새 초안을 생성할까요?')) {
                return
            }
        }

        actionLock.current = true
        setBusy(true)
        setError('')

        try {
            onCreated(await regenerateDraft(coverLetterId, draftId))

        } catch (actionError) {
            setError(messageOf(actionError))

        } finally {
            actionLock.current = false
            setBusy(false)
        }
    }

    async function submit(event: SubmitEvent<HTMLFormElement>) {
        event.preventDefault()
        if (!form || !draft || actionLock.current) return

        const input = {
            title: form.title.trim(),
            content: form.content.trim(),
        }

        if (!input.title || !input.content) {
            setError('제목과 내용을 입력해 주세요.')
            return
        }

        actionLock.current = true
        setBusy(true)
        setError('')

        try {
            const letter = await applyDraft(coverLetterId, draftId, input)
            onApplied(letter)

            const applied: DraftDetails = {
                ...draft,
                status: 'APPLIED',
                appliedVersionNumber: letter.currentVersionNumber,
            }

            setDraft(applied)
            onDraft(applied)

        } catch (actionError) {
            setError(messageOf(actionError))

        } finally {
            actionLock.current = false
            setBusy(false)
        }
    }

    return (
        <section className="documents-panel">
            <h2>초안 검토</h2>
            {error && (
                <div>
                    <p className="form-alert" role="alert">{error}</p>
                    <button
                        type="button"
                        disabled={busy}
                        onClick={() => setRevision((value) => value + 1)}
                    >
                        상태 다시 조회
                    </button>
                </div>
            )}

            {!draft && !error && <p>초안을 불러오는 중입니다.</p>}

            {draft && (
                <>
                    <p role="status">
                        {statusLabels[draft.status]} · 기준 버전 {draft.baseVersionNumber}
                    </p>

                    {generating(draft.status) && (
                        <p>
                            초안을 생성하고 있습니다. 생성 상태를 3초 간격으로 확인합니다.
                            {error ? ' 조회에 실패해 자동 확인을 중단했습니다.' : ''}
                        </p>
                    )}

                    {draft.status === 'FAILED' && (
                        <p className="form-alert" role="alert">
                            {draft.failureCode === 'DRAFT_AI_NOT_CONFIGURED'
                                ? 'AI 초안 생성 기능이 현재 준비되지 않았습니다.'
                                : '초안을 생성하지 못했습니다. 다시 생성해 주세요.'}
                        </p>
                    )}

                    {draft.changeSummary && (
                        <p className="draft-text">{draft.changeSummary}</p>
                    )}

                    {draft.warnings.length > 0 && (
                        <div className="draft-warnings">
                            <h3>검토할 사항</h3>
                            <ul>
                                {draft.warnings.map((warning, index) => (
                                    <li key={index}>{warning}</li>
                                ))}
                            </ul>
                        </div>
                    )}

                    {draft.status === 'REVIEW_READY' && form && (
                        <form
                            className="documents-form"
                            onSubmit={(event) => void submit(event)}
                        >
                            <p>
                                사실관계와 표현을 확인하고 수정하세요.
                                적용하면 자기소개서의 새 버전으로 저장됩니다.
                            </p>
                            <label>
                                제목
                                <input
                                    required
                                    maxLength={100}
                                    disabled={busy}
                                    value={form.title}
                                    onChange={(event) => setForm({
                                        ...form,
                                        title: event.target.value,
                                    })}
                                />
                            </label>
                            <label>
                                내용
                                <textarea
                                    required
                                    maxLength={20000}
                                    disabled={busy}
                                    value={form.content}
                                    onChange={(event) => setForm({
                                        ...form,
                                        content: event.target.value,
                                    })}
                                />
                                <small>{form.content.length.toLocaleString()} / 20,000자</small>
                            </label>
                            <button
                                className="primary-button documents-action"
                                type="submit"
                                disabled={busy}
                            >
                                {busy ? '처리 중…' : '검토한 내용 적용'}
                            </button>
                        </form>
                    )}

                    {draft.status === 'APPLIED' && (
                        <>
                            <p role="status">
                                버전 {draft.appliedVersionNumber}에 적용된 초안입니다.
                                실제 저장된 내용은 문서 화면에서 확인하세요.
                            </p>
                            <details>
                                <summary>생성 당시 초안 보기</summary>
                                <h3>{draft.generatedTitle}</h3>
                                <p className="draft-text">{draft.generatedContent}</p>
                            </details>
                        </>
                    )}

                    {!generating(draft.status) && (
                        <div className="documents-actions">
                            <button
                                type="button"
                                disabled={busy}
                                onClick={() => void regenerate()}
                            >
                                최신 자기소개서로 재생성
                            </button>
                            <p>
                                기존 공고·이력서·추가 지시를 사용합니다.
                                입력을 변경하려면 위에서 새 초안을 생성하세요.
                            </p>
                        </div>
                    )}
                </>
            )}
        </section>
    )
}

interface WorkspaceData {
    letter: CoverLetterDetails
    resumes: ResumeSummary[]
    drafts: DraftSummary[]
}

function DraftWorkspace({coverLetterId}: { coverLetterId: number }) {
    const [data, setData] = useState<WorkspaceData | null>(null)
    const [error, setError] = useState('')
    const [revision, setRevision] = useState(0)
    const [job, setJob] = useState<JobPostingSummary | null>(null)
    const [resumeId, setResumeId] = useState('')
    const [instruction, setInstruction] = useState('')
    const [selectedId, setSelectedId] = useState<number | null>(null)
    const [busy, setBusy] = useState(false)
    const createLock = useRef(false)

    useEffect(() => {
        let active = true

        void Promise.all([
            getCoverLetter(coverLetterId),
            listResumes(),
            listDrafts(coverLetterId),
        ])
            .then(([letter, resumes, drafts]) => {
                if (!active) return

                setData({letter, resumes, drafts})
                setError('')
            })
            .catch((loadError: unknown) => {
                if (active) setError(messageOf(loadError))
            })

        return () => {
            active = false
        }
    }, [coverLetterId, revision]);

    const rememberDraft = useCallback((draft: DraftDetails) => {
        setData((previous) => {
            if (!previous) return previous

            const exists = previous.drafts.some((item) => item.id === draft.id)

            return {
                ...previous,
                drafts: exists
                    ? previous.drafts.map((item) => item.id === draft.id ? draft : item)
                    : [draft, ...previous.drafts],
            }
        })
    }, [])

    function selectCreated(draft: DraftDetails) {
        rememberDraft(draft)
        setSelectedId(draft.id)
    }

    async function submit(event: SubmitEvent<HTMLFormElement>) {
        event.preventDefault()
        if (!job || createLock.current) return

        createLock.current = true
        setBusy(true)
        setError('')

        try {
            selectCreated(await createDraft(coverLetterId, {
                jobPostingId: job.id,
                ...(resumeId ? {resumeId: Number(resumeId)} : {}),
                instruction,
            }))

        } catch (actionError) {
            setError(messageOf(actionError))

        } finally {
            createLock.current = false
            setBusy(false)
        }
    }

    return (
        <main className="workspace">
            <header className="workspace-header">
                <Link className="brand" to="/">
                    <span className="brand-mark">i</span>
                    <span>interview<span className="brand-accent">AI</span></span>
                </Link>
                <Link className="secondary-button documents-home-link" to="/documents">
                    내 자료로
                </Link>
            </header>

            <section className="documents-content draft-content">
                <p className="eyebrow">AI COVER LETTER</p>
                <h1>자기소개서 초안</h1>
                <p className="workspace-intro">
                    지원 공고와 이력서를 바탕으로 초안을 만들고 검토한 내용을 적용하세요.
                </p>

                {error && <p className="form-alert" role="alert">{error}</p>}

                {!data ? (
                    error ? (
                        <button
                            type="button"
                            onClick={() => setRevision((value) => value + 1)}
                        >
                            다시 조회
                        </button>
                    ) : <p className="documents-state">자료를 불러오는 중입니다.</p>
                ) : (
                    <>
                        <section className="documents-panel">
                            <h2>{data.letter.title}</h2>
                            <p>현재 버전 {data.letter.currentVersionNumber}</p>
                            <details>
                                <summary>현재 자기소개서 보기</summary>
                                <p className="draft-text">{data.letter.content}</p>
                            </details>

                            <form
                                className="documents-form"
                                onSubmit={(event) => void submit(event)}
                            >
                                <fieldset className="draft-fields" disabled={busy}>
                                    <legend>새 초안 생성</legend>
                                    <JobPicker selected={job} onSelect={setJob}/>

                                    <label>
                                        사용할 이력서
                                        <select
                                            value={resumeId}
                                            onChange={(event) => setResumeId(event.target.value)}
                                        >
                                            <option value="">대표 이력서 사용</option>
                                            {data.resumes
                                                .filter((resume) => resume.extractionStatus === 'COMPLETED')
                                                .map((resume) => (
                                                    <option key={resume.id} value={resume.id}>
                                                        {resume.title}
                                                        {resume.representative ? ' (대표)' : ''}
                                                    </option>
                                                ))}
                                        </select>
                                    </label>
                                    <small>
                                        추출 완료된 이력서만 선택할 수 있습니다.
                                        대표 이력서가 없다면 먼저 등록하거나 이력서를 선택하세요.
                                    </small>

                                    <label>
                                        추가 지시 · 선택
                                        <textarea
                                            value={instruction}
                                            maxLength={1000}
                                            placeholder="강조할 경험이나 개선하고 싶은 표현을 입력하세요."
                                            onChange={(event) => setInstruction(event.target.value)}
                                        />
                                        <small>{instruction.length} / 1,000자</small>
                                    </label>

                                    <button
                                        className="primary-button documents-action"
                                        type="submit"
                                        disabled={!job || busy}
                                    >
                                        {busy ? '요청 중…' : '초안 생성'}
                                    </button>
                                </fieldset>
                            </form>
                        </section>

                        <section className="documents-panel">
                            <h2>초안 이력</h2>
                            {data.drafts.length === 0 && <p>생성한 초안이 없습니다.</p>}
                            <ul className="documents-list">
                                {data.drafts.map((draft) => (
                                    <li className="documents-card" key={draft.id}>
                                        <h3>{draft.generatedTitle ?? '자기소개서 초안'}</h3>
                                        <p>
                                            {statusLabels[draft.status]} · 기준 버전 {draft.baseVersionNumber}
                                        </p>
                                        <p>{draft.createdAt.replace('T', ' ')}</p>
                                        <div className="documents-actions">
                                            <button
                                                type="button"
                                                aria-pressed={selectedId === draft.id}
                                                onClick={() => {
                                                    if (selectedId !== null && selectedId !== draft.id) {
                                                        if (!window.confirm('다른 초안을 열까요? 적용하지 않은 검토 내용은 저장되지 않습니다.')) {
                                                            return
                                                        }
                                                    }
                                                    setSelectedId(draft.id)
                                                }}
                                            >
                                                {selectedId === draft.id ? '열려 있음' : '상세 보기'}
                                            </button>
                                        </div>
                                    </li>
                                ))}
                            </ul>
                        </section>

                        {selectedId !== null && (
                            <DraftReview
                                key={selectedId}
                                coverLetterId={coverLetterId}
                                draftId={selectedId}
                                onDraft={rememberDraft}
                                onCreated={selectCreated}
                                onApplied={(letter) => setData((previous) =>
                                    previous ? {...previous, letter} : previous,
                                )}
                            />
                        )}
                    </>
                )}
            </section>
        </main>
    )
}

export function CoverLetterDraftPage() {
    const {coverLetterId: routeId} = useParams()
    const coverLetterId = Number(routeId)

    if (!Number.isSafeInteger(coverLetterId) || coverLetterId <= 0) {
        return (
            <main className="workspace">
                <p className="form-alert" role="alert">
                    자기소개서 주소가 올바르지 않습니다.
                </p>
                <Link to="/documents">내 자료로 돌아가기</Link>
            </main>
        )
    }

    return <DraftWorkspace key={coverLetterId} coverLetterId={coverLetterId}/>
}