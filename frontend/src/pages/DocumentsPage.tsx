import {type SubmitEvent, useEffect, useState} from 'react'
import {Link} from 'react-router-dom'
import {ApiError} from '../api/ApiError'
import {
    type CoverLetterSummary,
    type CoverLetterVersion,
    createCoverLetter,
    deleteCoverLetter,
    deleteResume,
    downloadResume,
    getCoverLetter,
    listCoverLetters,
    listCoverLetterVersions,
    listResumes,
    restoreCoverLetterVersion,
    type ResumeSummary,
    setRepresentativeCoverLetter,
    setRepresentativeResume,
    updateCoverLetter,
    updateResumeTitle,
    uploadResume,
} from '../api/documentsApi'

type Tab = 'coverLetters' | 'resumes'

interface CoverLetterForm {
    title: string
    content: string
}

function errorMessage(error: unknown): string {
    if (error instanceof ApiError) return error.message
    return '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'
}

function saveBlob(blob: Blob, filename: string) {
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = filename
    anchor.click()
    URL.revokeObjectURL(url)
}

export function DocumentsPage() {
    const [tab, setTab] = useState<Tab>('coverLetters')
    const [coverLetters, setCoverLetters] = useState<CoverLetterSummary[]>([])
    const [resumes, setResumes] = useState<ResumeSummary[]>([])
    const [form, setForm] = useState<CoverLetterForm | null>(null)
    const [editingId, setEditingId] = useState<number | null>(null)
    const [versionsFor, setVersionsFor] = useState<number | null>(null)
    const [versions, setVersions] = useState<CoverLetterVersion[]>([])
    const [resumeTitle, setResumeTitle] = useState('')
    const [resumeFile, setResumeFile] = useState<File | null>(null)
    const [editingResumeId, setEditingResumeId] = useState<number | null>(null)
    const [editingResumeTitle, setEditingResumeTitle] = useState('')
    const [loading, setLoading] = useState(true)
    const [busy, setBusy] = useState(false)
    const [error, setError] = useState('')

    async function refresh() {
        setLoading(true)
        setError('')
        try {
            const [letters, resumeItems] = await Promise.all([listCoverLetters(), listResumes()])
            setCoverLetters(letters)
            setResumes(resumeItems)
        } catch (loadError) {
            setError(errorMessage(loadError))
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        let active = true

        void Promise.all([listCoverLetters(), listResumes()])
            .then(([letters, resumeItems]) => {
                if (!active) return

                setCoverLetters(letters)
                setResumes(resumeItems)
            })
            .catch((loadError: unknown) => {
                if (active) {
                    setError(errorMessage(loadError))
                }
            })
            .finally(() => {
                if (active) {
                    setLoading(false)
                }
            })

        return () => {
            active = false
        }
    }, [])

    async function runAction(action: () => Promise<unknown>) {
        setBusy(true)
        setError('')
        try {
            await action()
            await refresh()
        } catch (actionError) {
            setError(errorMessage(actionError))
        } finally {
            setBusy(false)
        }
    }

    async function beginEdit(id: number) {
        setError('')
        try {
            const item = await getCoverLetter(id)
            setEditingId(id)
            setForm({title: item.title, content: item.content})
        } catch (loadError) {
            setError(errorMessage(loadError))
        }
    }

    async function toggleVersions(id: number) {
        if (versionsFor === id) {
            setVersionsFor(null)
            setVersions([])
            return
        }

        setError('')
        try {
            setVersions(await listCoverLetterVersions(id))
            setVersionsFor(id)
        } catch (loadError) {
            setError(errorMessage(loadError))
        }
    }

    async function submitCoverLetter(event: SubmitEvent<HTMLFormElement>) {
        event.preventDefault()
        if (!form) return

        await runAction(async () => {
            if (editingId === null) {
                await createCoverLetter(form)
            } else {
                await updateCoverLetter(editingId, form)
            }
            setForm(null)
            setEditingId(null)
        })
    }

    async function submitResume(event: SubmitEvent<HTMLFormElement>) {
        event.preventDefault()
        const formElement = event.currentTarget
        if (!resumeFile || !resumeTitle.trim()) return

        await runAction(async () => {
            await uploadResume(resumeTitle.trim(), resumeFile)
            setResumeTitle('')
            setResumeFile(null)
            formElement.reset()
        })
    }

    async function handleDownload(item: ResumeSummary) {
        setBusy(true)
        setError('')
        try {
            const blob = await downloadResume(item.id)
            saveBlob(blob, item.originalFilename || `${item.title}.pdf`)
        } catch (downloadError) {
            setError(errorMessage(downloadError))
        } finally {
            setBusy(false)
        }
    }

    async function handleDeleteCoverLetter(item: CoverLetterSummary) {
        if (!window.confirm(`“${item.title}” 자기소개서를 삭제할까요?`)) return
        await runAction(() => deleteCoverLetter(item.id))
    }

    async function handleDeleteResume(item: ResumeSummary) {
        if (!window.confirm(`“${item.title}” 이력서를 삭제할까요?`)) return
        await runAction(() => deleteResume(item.id))
    }

    return (
        <main className="workspace">
            <header className="workspace-header">
                <Link className="brand" to="/">
                    <span className="brand-mark">i</span>
                    <span>interview<span className="brand-accent">AI</span></span>
                </Link>
                <Link className="secondary-button documents-home-link" to="/">홈으로</Link>
            </header>

            <section className="documents-content">
                <p className="eyebrow">MY MATERIALS</p>
                <h1>내 자료 정리</h1>
                <p className="workspace-intro">
                    자기소개서와 이력서를 관리하고 면접 연습에 사용할 대표 자료를 설정하세요.
                </p>

                {error && <p className="form-alert" role="alert">{error}</p>}

                <div className="documents-tabs" role="tablist" aria-label="문서 종류">
                    <button
                        className={tab === 'coverLetters' ? 'selected' : ''}
                        type="button"
                        role="tab"
                        aria-selected={tab === 'coverLetters'}
                        onClick={() => setTab('coverLetters')}
                    >
                        자기소개서 {coverLetters.length}
                    </button>
                    <button
                        className={tab === 'resumes' ? 'selected' : ''}
                        type="button"
                        role="tab"
                        aria-selected={tab === 'resumes'}
                        onClick={() => setTab('resumes')}
                    >
                        이력서 {resumes.length}
                    </button>
                </div>

                {loading ? (
                    <p className="documents-state">자료를 불러오는 중입니다.</p>
                ) : tab === 'coverLetters' ? (
                    <section className="documents-panel">
                        <div className="documents-heading">
                            <h2>자기소개서</h2>
                            <button
                                className="primary-button documents-action"
                                type="button"
                                onClick={() => {
                                    setEditingId(null)
                                    setForm({title: '', content: ''})
                                }}
                            >
                                새 자기소개서
                            </button>
                        </div>

                        {form && (
                            <form className="documents-form" onSubmit={(event) => void submitCoverLetter(event)}>
                                <label>
                                    제목
                                    <input
                                        maxLength={100}
                                        required
                                        value={form.title}
                                        onChange={(event) => setForm({...form, title: event.target.value})}
                                    />
                                </label>
                                <label>
                                    내용
                                    <textarea
                                        maxLength={20000}
                                        required
                                        value={form.content}
                                        onChange={(event) => setForm({...form, content: event.target.value})}
                                    />
                                    <small>{form.content.length.toLocaleString()} / 20,000자</small>
                                </label>
                                <div className="documents-form-actions">
                                    <button className="primary-button documents-action" disabled={busy} type="submit">
                                        {editingId === null ? '저장' : '새 버전으로 저장'}
                                    </button>
                                    <button
                                        className="secondary-button"
                                        type="button"
                                        onClick={() => {
                                            setForm(null)
                                            setEditingId(null)
                                        }}
                                    >
                                        취소
                                    </button>
                                </div>
                            </form>
                        )}

                        {coverLetters.length === 0 ? (
                            <p className="documents-state">등록된 자기소개서가 없습니다.</p>
                        ) : (
                            <ul className="documents-list">
                                {coverLetters.map((item) => (
                                    <li className="documents-card" key={item.id}>
                                        <div className="documents-card-heading">
                                            <div>
                                                <h3>{item.title}</h3>
                                                <p>현재 버전 {item.currentVersionNumber}</p>
                                            </div>
                                            {item.representative && <span className="documents-badge">대표 자료</span>}
                                        </div>
                                        <div className="documents-actions">
                                            <button type="button" onClick={() => void beginEdit(item.id)}>수정</button>
                                            <Link className="draft-entry-link" to={`/documents/${item.id}/drafts`}>
                                                AI 초안
                                            </Link>
                                            <button type="button" onClick={() => void toggleVersions(item.id)}>
                                                {versionsFor === item.id ? '버전 닫기' : '버전 이력'}
                                            </button>
                                            {!item.representative && (
                                                <button
                                                    disabled={busy}
                                                    type="button"
                                                    onClick={() => void runAction(() => setRepresentativeCoverLetter(item.id))}
                                                >
                                                    대표로 설정
                                                </button>
                                            )}
                                            <button
                                                className="documents-danger"
                                                disabled={busy}
                                                type="button"
                                                onClick={() => void handleDeleteCoverLetter(item)}
                                            >
                                                삭제
                                            </button>
                                        </div>
                                        {versionsFor === item.id && (
                                            <ol className="documents-versions">
                                                {versions.map((version) => (
                                                    <li key={version.versionNumber}>
                                                        <span>
                                                            v{version.versionNumber} · {version.title}
                                                            {version.current ? ' (현재)' : ''}
                                                        </span>
                                                        {!version.current && (
                                                            <button
                                                                disabled={busy}
                                                                type="button"
                                                                onClick={() => void runAction(() =>
                                                                    restoreCoverLetterVersion(item.id, version.versionNumber),
                                                                )}
                                                            >
                                                                이 버전 복원
                                                            </button>
                                                        )}
                                                    </li>
                                                ))}
                                            </ol>
                                        )}
                                    </li>
                                ))}
                            </ul>
                        )}
                    </section>
                ) : (
                    <section className="documents-panel">
                        <div className="documents-heading">
                            <h2>이력서 PDF</h2>
                            <span>PDF, 최대 10MB</span>
                        </div>

                        <form className="documents-upload" onSubmit={(event) => void submitResume(event)}>
                            <label>
                                제목
                                <input
                                    maxLength={100}
                                    required
                                    value={resumeTitle}
                                    onChange={(event) => setResumeTitle(event.target.value)}
                                    placeholder="예: 백엔드 개발자 이력서"
                                />
                            </label>
                            <label>
                                PDF 파일
                                <input
                                    accept="application/pdf,.pdf"
                                    required
                                    type="file"
                                    onChange={(event) => setResumeFile(event.target.files?.[0] ?? null)}
                                />
                            </label>
                            <button className="primary-button documents-action" disabled={busy || !resumeFile}
                                    type="submit">
                                PDF 업로드
                            </button>
                        </form>

                        {resumes.length === 0 ? (
                            <p className="documents-state">등록된 이력서가 없습니다.</p>
                        ) : (
                            <ul className="documents-list">
                                {resumes.map((item) => (
                                    <li className="documents-card" key={item.id}>
                                        <div className="documents-card-heading">
                                            <div>
                                                {editingResumeId === item.id ? (
                                                    <form
                                                        className="documents-inline-edit"
                                                        onSubmit={(event) => {
                                                            event.preventDefault()
                                                            void runAction(async () => {
                                                                await updateResumeTitle(item.id, editingResumeTitle)
                                                                setEditingResumeId(null)
                                                            })
                                                        }}
                                                    >
                                                        <input
                                                            aria-label="이력서 제목"
                                                            maxLength={100}
                                                            required
                                                            value={editingResumeTitle}
                                                            onChange={(event) => setEditingResumeTitle(event.target.value)}
                                                        />
                                                        <button disabled={busy} type="submit">저장</button>
                                                        <button type="button"
                                                                onClick={() => setEditingResumeId(null)}>취소
                                                        </button>
                                                    </form>
                                                ) : (
                                                    <h3>{item.title}</h3>
                                                )}
                                                <p>
                                                    {item.originalFilename} · {(item.fileSize / 1024).toFixed(0)}KB
                                                    · 텍스트 추출 {item.extractionStatus}
                                                </p>
                                            </div>
                                            {item.representative && <span className="documents-badge">대표 자료</span>}
                                        </div>
                                        <div className="documents-actions">
                                            <button type="button" disabled={busy}
                                                    onClick={() => void handleDownload(item)}>
                                                PDF 다운로드
                                            </button>
                                            <button
                                                type="button"
                                                onClick={() => {
                                                    setEditingResumeId(item.id)
                                                    setEditingResumeTitle(item.title)
                                                }}
                                            >
                                                제목 수정
                                            </button>
                                            {!item.representative && (
                                                <button
                                                    disabled={busy}
                                                    type="button"
                                                    onClick={() => void runAction(() => setRepresentativeResume(item.id))}
                                                >
                                                    대표로 설정
                                                </button>
                                            )}
                                            <button
                                                className="documents-danger"
                                                disabled={busy}
                                                type="button"
                                                onClick={() => void handleDeleteResume(item)}
                                            >
                                                삭제
                                            </button>
                                        </div>
                                    </li>
                                ))}
                            </ul>
                        )}
                    </section>
                )}
            </section>
        </main>
    )
}