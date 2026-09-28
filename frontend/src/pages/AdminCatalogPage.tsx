import {type SubmitEvent, useCallback, useEffect, useState} from 'react'
import {Link} from 'react-router-dom'
import {ApiError} from '../api/ApiError'
import {
    type CompanyInput,
    type CompanySummary,
    createCompany,
    createJobPosting,
    deleteCompany,
    deleteJobPosting,
    type EmploymentType,
    getCompany,
    getJobPosting,
    type JobPostingInput,
    type JobPostingSummary,
    listAllCompanies,
    listCompanies,
    listJobPostings,
    updateCompany,
    updateJobPosting,
} from '../api/adminCatalogApi'

type Section = 'companies' | 'postings'

const emptyCompany: CompanyInput = {
    name: '',
    industry: '',
    description: '',
    websiteUrl: '',
    location: '',
}

const emptyPosting: JobPostingInput = {
    companyId: undefined,
    title: '',
    jobRole: '',
    employmentType: 'FULL_TIME',
    location: '',
    description: '',
    sourceUrl: '',
    opensAt: null,
    closesAt: null,
    manuallyClosed: false,
}

const employmentTypes: Array<{ value: EmploymentType; label: string }> = [
    {value: 'FULL_TIME', label: '정규직'},
    {value: 'CONTRACT', label: '계약직'},
    {value: 'INTERN', label: '인턴'},
    {value: 'PART_TIME', label: '파트타임'},
    {value: 'OTHER', label: '기타'},
]

const statusLabels = {SCHEDULED: '예정', OPEN: '모집 중', CLOSED: '마감'}

function errorMessage(error: unknown): string {
    if (error instanceof ApiError) return error.message
    return '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'
}

function toDateTimeInput(value: string | null): string {
    if (!value) return ''
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return ''
    return new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
        .toISOString()
        .slice(0, 16)
}

function toOffsetDateTime(value: string): string | null {
    return value ? new Date(value).toISOString() : null
}

export function AdminCatalogPage() {
    const [section, setSection] = useState<Section>('companies')
    const [companies, setCompanies] = useState<CompanySummary[]>([])
    const [companyOptions, setCompanyOptions] = useState<CompanySummary[]>([])
    const [postings, setPostings] = useState<JobPostingSummary[]>([])
    const [companySearchInput, setCompanySearchInput] = useState('')
    const [postingSearchInput, setPostingSearchInput] = useState('')
    const [companyKeyword, setCompanyKeyword] = useState('')
    const [postingKeyword, setPostingKeyword] = useState('')
    const [companyPageNumber, setCompanyPageNumber] = useState(0)
    const [postingPageNumber, setPostingPageNumber] = useState(0)
    const [companyTotalPages, setCompanyTotalPages] = useState(0)
    const [postingTotalPages, setPostingTotalPages] = useState(0)
    const [companyTotal, setCompanyTotal] = useState(0)
    const [postingTotal, setPostingTotal] = useState(0)
    const [loading, setLoading] = useState(true)
    const [saving, setSaving] = useState(false)
    const [error, setError] = useState('')
    const [notice, setNotice] = useState('')
    const [companyFormOpen, setCompanyFormOpen] = useState(false)
    const [companyId, setCompanyId] = useState<number | null>(null)
    const [companyForm, setCompanyForm] = useState<CompanyInput>(emptyCompany)
    const [postingFormOpen, setPostingFormOpen] = useState(false)
    const [postingId, setPostingId] = useState<number | null>(null)
    const [postingForm, setPostingForm] = useState<JobPostingInput>(emptyPosting)

    const loadCatalog = useCallback(async () => {
        try {
            const [companyPage, postingPage, allCompanies] = await Promise.all([
                listCompanies(companyKeyword, companyPageNumber),
                listJobPostings(postingKeyword, postingPageNumber),
                listAllCompanies(),
            ])
            setCompanies(companyPage.items)
            setPostings(postingPage.items)
            setCompanyOptions(allCompanies)
            setCompanyTotalPages(companyPage.totalPages)
            setPostingTotalPages(postingPage.totalPages)
            setCompanyTotal(companyPage.totalElements)
            setPostingTotal(postingPage.totalElements)
        } catch (loadError) {
            setError(errorMessage(loadError))
        } finally {
            setLoading(false)
        }
    }, [companyKeyword, postingKeyword, companyPageNumber, postingPageNumber])

    useEffect(() => {
        let active = true

        void Promise.resolve().then(() => {
            if (active) {
                return loadCatalog()
            }
        })

        return () => {
            active = false
        }
    }, [loadCatalog])

    function startNewCompany() {
        setCompanyId(null)
        setCompanyForm(emptyCompany)
        setCompanyFormOpen(true)
        setNotice('')
        setError('')
    }

    async function startEditCompany(company: CompanySummary) {
        setError('')
        try {
            const details = await getCompany(company.id)
            setCompanyId(details.id)
            setCompanyForm({
                name: details.name,
                industry: details.industry ?? '',
                description: details.description,
                websiteUrl: details.websiteUrl ?? '',
                location: details.location ?? '',
            })
            setCompanyFormOpen(true)
            setSection('companies')
        } catch (editError) {
            setError(errorMessage(editError))
        }
    }

    async function submitCompany(event: SubmitEvent<HTMLFormElement>) {
        event.preventDefault()
        setSaving(true)
        setError('')
        setNotice('')
        try {
            if (companyId === null) await createCompany(companyForm)
            else await updateCompany(companyId, companyForm)
            setCompanyFormOpen(false)
            setNotice(companyId === null ? '기업을 등록했습니다.' : '기업 정보를 수정했습니다.')
            setLoading(true)
            await loadCatalog()
        } catch (saveError) {
            setError(errorMessage(saveError))
        } finally {
            setSaving(false)
        }
    }

    async function removeCompany(company: CompanySummary) {
        if (!window.confirm(`'${company.name}' 기업을 삭제할까요?`)) return
        setError('')
        setNotice('')
        try {
            await deleteCompany(company.id)
            setNotice('기업을 삭제했습니다.')
            setLoading(true)
            await loadCatalog()
        } catch (deleteError) {
            setError(errorMessage(deleteError))
        }
    }

    function startNewPosting() {
        setPostingId(null)
        setPostingForm({...emptyPosting, companyId: companyOptions[0]?.id})
        setPostingFormOpen(true)
        setNotice('')
        setError('')
    }

    async function startEditPosting(posting: JobPostingSummary) {
        setError('')
        try {
            const details = await getJobPosting(posting.id)
            setPostingId(details.id)
            setPostingForm({
                companyId: details.companyId,
                title: details.title,
                jobRole: details.jobRole,
                employmentType: details.employmentType,
                location: details.location ?? '',
                description: details.description,
                sourceUrl: details.sourceUrl ?? '',
                opensAt: details.opensAt,
                closesAt: details.closesAt,
                manuallyClosed: details.manuallyClosed,
            })
            setPostingFormOpen(true)
            setSection('postings')
        } catch (editError) {
            setError(errorMessage(editError))
        }
    }

    async function submitPosting(event: SubmitEvent<HTMLFormElement>) {
        event.preventDefault()
        if (!postingForm.companyId) {
            setError('먼저 기업을 등록한 뒤 공고를 등록해 주세요.')
            return
        }
        setSaving(true)
        setError('')
        setNotice('')
        const input: JobPostingInput = {
            ...postingForm,
            opensAt: toOffsetDateTime(postingForm.opensAt ?? ''),
            closesAt: toOffsetDateTime(postingForm.closesAt ?? ''),
        }
        try {
            if (postingId === null) {
                await createJobPosting({...input, companyId: postingForm.companyId})
            } else {
                await updateJobPosting(postingId, input)
            }
            setPostingFormOpen(false)
            setNotice(postingId === null ? '채용공고를 등록했습니다.' : '채용공고를 수정했습니다.')
            setLoading(true)
            await loadCatalog()
        } catch (saveError) {
            setError(errorMessage(saveError))
        } finally {
            setSaving(false)
        }
    }

    async function removePosting(posting: JobPostingSummary) {
        if (!window.confirm(`'${posting.title}' 공고를 삭제할까요?`)) return
        setError('')
        setNotice('')
        try {
            await deleteJobPosting(posting.id)
            setNotice('채용공고를 삭제했습니다.')
            setLoading(true)
            await loadCatalog()
        } catch (deleteError) {
            setError(errorMessage(deleteError))
        }
    }

    return (
        <main className="workspace admin-workspace">
            <header className="workspace-header">
                <Link className="brand" to="/">
                    <span className="brand-mark">i</span>
                    <span>interview<span className="brand-accent">AI</span></span>
                </Link>
                <div className="workspace-header-actions">
                    <span className="account-label">관리자 카탈로그</span>
                    <Link className="secondary-button" to="/">사용자 화면</Link>
                </div>
            </header>

            <section className="admin-content">
                <p className="eyebrow">CATALOG MANAGEMENT</p>
                <div className="admin-title-row">
                    <div>
                        <h1>기업과 채용공고</h1>
                        <p className="workspace-intro">사용자에게 공개할 카탈로그 정보를 관리합니다.</p>
                    </div>
                    <span className="admin-count">기업 {companyTotal} · 공고 {postingTotal}</span>
                </div>

                {error && <p className="form-alert admin-message" role="alert">{error}</p>}
                {notice && <p className="admin-notice" role="status">{notice}</p>}

                <div className="admin-tabs" role="tablist" aria-label="카탈로그 관리">
                    <button className={section === 'companies' ? 'admin-tab active' : 'admin-tab'} type="button"
                            role="tab" aria-selected={section === 'companies'} onClick={() => setSection('companies')}>
                        기업 <span>{companies.length}</span>
                    </button>
                    <button className={section === 'postings' ? 'admin-tab active' : 'admin-tab'} type="button"
                            role="tab" aria-selected={section === 'postings'} onClick={() => setSection('postings')}>
                        채용공고 <span>{postings.length}</span>
                    </button>
                </div>

                {section === 'companies' && (
                    <section className="admin-panel" role="tabpanel">
                        <div className="admin-panel-heading">
                            <div><h2>기업 목록</h2><p>기업 정보 등록 및 수정</p></div>
                            <button className="primary-button admin-primary" type="button" onClick={startNewCompany}>기업
                                등록 <span>＋</span></button>
                        </div>
                        <form className="admin-search" onSubmit={(event) => {
                            event.preventDefault();
                            setError('');
                            setLoading(true);
                            setCompanyPageNumber(0);
                            setCompanyKeyword(companySearchInput.trim())
                        }}>
                            <input aria-label="기업 검색" placeholder="기업명 검색" value={companySearchInput}
                                   onChange={(event) => setCompanySearchInput(event.target.value)}/>
                            <button className="secondary-button" type="submit">검색</button>
                        </form>
                        {companyFormOpen && (
                            <form className="admin-editor" onSubmit={submitCompany}>
                                <div className="admin-editor-heading">
                                    <h3>{companyId === null ? '새 기업 등록' : '기업 정보 수정'}</h3>
                                    <button className="text-button" type="button"
                                            onClick={() => setCompanyFormOpen(false)}>닫기
                                    </button>
                                </div>
                                <div className="admin-form-grid">
                                    <label className="form-field">기업명<input maxLength={100} required
                                                                            value={companyForm.name}
                                                                            onChange={(event) => setCompanyForm({
                                                                                ...companyForm,
                                                                                name: event.target.value
                                                                            })}/></label>
                                    <label className="form-field">산업 분야<input maxLength={100}
                                                                              value={companyForm.industry}
                                                                              onChange={(event) => setCompanyForm({
                                                                                  ...companyForm,
                                                                                  industry: event.target.value
                                                                              })}/></label>
                                    <label className="form-field">위치<input maxLength={200} value={companyForm.location}
                                                                           onChange={(event) => setCompanyForm({
                                                                               ...companyForm,
                                                                               location: event.target.value
                                                                           })}/></label>
                                    <label className="form-field">홈페이지 URL<input type="url" maxLength={2048}
                                                                                 value={companyForm.websiteUrl}
                                                                                 onChange={(event) => setCompanyForm({
                                                                                     ...companyForm,
                                                                                     websiteUrl: event.target.value
                                                                                 })}/></label>
                                    <label className="form-field admin-form-wide">기업 소개<textarea maxLength={20000}
                                                                                                 required rows={5}
                                                                                                 value={companyForm.description}
                                                                                                 onChange={(event) => setCompanyForm({
                                                                                                     ...companyForm,
                                                                                                     description: event.target.value
                                                                                                 })}/></label>
                                </div>
                                <div className="admin-form-actions">
                                    <button className="secondary-button" type="button"
                                            onClick={() => setCompanyFormOpen(false)}>취소
                                    </button>
                                    <button className="primary-button admin-primary" type="submit"
                                            disabled={saving}>{saving ? '저장 중…' : '저장'}</button>
                                </div>
                            </form>
                        )}
                        {loading ?
                            <p className="admin-empty" role="status">기업 목록을 불러오는 중입니다.</p> : companies.length === 0 ?
                                <p className="admin-empty">등록된 기업이 없습니다.</p> : (
                                    <div className="admin-table-wrap">
                                        <table className="admin-table">
                                            <thead>
                                            <tr>
                                                <th>기업명</th>
                                                <th>산업 분야</th>
                                                <th>위치</th>
                                                <th>관리</th>
                                            </tr>
                                            </thead>
                                            <tbody>
                                            {companies.map((company) => <tr key={company.id}>
                                                <td><strong>{company.name}</strong></td>
                                                <td>{company.industry || '—'}</td>
                                                <td>{company.location || '—'}</td>
                                                <td className="admin-row-actions">
                                                    <button className="text-button" type="button"
                                                            onClick={() => void startEditCompany(company)}>수정
                                                    </button>
                                                    <button className="text-button danger-button" type="button"
                                                            onClick={() => void removeCompany(company)}>삭제
                                                    </button>
                                                </td>
                                            </tr>)}
                                            </tbody>
                                        </table>
                                    </div>
                                )}
                        {!loading && companyTotalPages > 1 && <div className="admin-pagination">
                            <button className="secondary-button" type="button" disabled={companyPageNumber === 0}
                                    onClick={() => {
                                        setLoading(true);
                                        setCompanyPageNumber((page) => page - 1)
                                    }}>이전
                            </button>
                            <span>{companyPageNumber + 1} / {companyTotalPages}</span>
                            <button className="secondary-button" type="button"
                                    disabled={companyPageNumber + 1 >= companyTotalPages}
                                    onClick={() => {
                                        setLoading(true);
                                        setCompanyPageNumber((page) => page + 1)
                                    }}>다음
                            </button>
                        </div>}
                    </section>
                )}

                {section === 'postings' && (
                    <section className="admin-panel" role="tabpanel">
                        <div className="admin-panel-heading">
                            <div><h2>채용공고 목록</h2><p>공고 등록 및 모집 상태 관리</p></div>
                            <button className="primary-button admin-primary" type="button" onClick={startNewPosting}
                                    disabled={companyOptions.length === 0}>공고 등록 <span>＋</span></button>
                        </div>
                        {companyOptions.length === 0 &&
                            <p className="admin-inline-help">공고를 등록하기 전에 기업을 먼저 등록해 주세요.</p>}
                        <form className="admin-search" onSubmit={(event) => {
                            event.preventDefault();
                            setError('');
                            setLoading(true);
                            setPostingPageNumber(0);
                            setPostingKeyword(postingSearchInput.trim())
                        }}>
                            <input aria-label="공고 검색" placeholder="공고명 또는 직무 검색" value={postingSearchInput}
                                   onChange={(event) => setPostingSearchInput(event.target.value)}/>
                            <button className="secondary-button" type="submit">검색</button>
                        </form>
                        {postingFormOpen && (
                            <form className="admin-editor" onSubmit={submitPosting}>
                                <div className="admin-editor-heading">
                                    <h3>{postingId === null ? '새 공고 등록' : '공고 정보 수정'}</h3>
                                    <button className="text-button" type="button"
                                            onClick={() => setPostingFormOpen(false)}>닫기
                                    </button>
                                </div>
                                <div className="admin-form-grid">
                                    <label className="form-field">기업<select required disabled={postingId !== null}
                                                                            value={postingForm.companyId ?? ''}
                                                                            onChange={(event) => setPostingForm({
                                                                                ...postingForm,
                                                                                companyId: Number(event.target.value)
                                                                            })}>
                                        <option value="" disabled>기업 선택</option>
                                        {companyOptions.map((company) => <option key={company.id}
                                                                                 value={company.id}>{company.name}</option>)}
                                    </select></label>
                                    <label className="form-field">공고 제목<input maxLength={200} required
                                                                              value={postingForm.title}
                                                                              onChange={(event) => setPostingForm({
                                                                                  ...postingForm,
                                                                                  title: event.target.value
                                                                              })}/></label>
                                    <label className="form-field">직무<input maxLength={100} required
                                                                           value={postingForm.jobRole}
                                                                           onChange={(event) => setPostingForm({
                                                                               ...postingForm,
                                                                               jobRole: event.target.value
                                                                           })}/></label>
                                    <label className="form-field">고용 형태<select value={postingForm.employmentType}
                                                                               onChange={(event) => setPostingForm({
                                                                                   ...postingForm,
                                                                                   employmentType: event.target.value as EmploymentType
                                                                               })}>{employmentTypes.map((item) =>
                                        <option key={item.value}
                                                value={item.value}>{item.label}</option>)}</select></label>
                                    <label className="form-field">근무지<input maxLength={200} value={postingForm.location}
                                                                            onChange={(event) => setPostingForm({
                                                                                ...postingForm,
                                                                                location: event.target.value
                                                                            })}/></label>
                                    <label className="form-field">출처 URL<input type="url" maxLength={2048}
                                                                               value={postingForm.sourceUrl}
                                                                               onChange={(event) => setPostingForm({
                                                                                   ...postingForm,
                                                                                   sourceUrl: event.target.value
                                                                               })}/></label>
                                    <label className="form-field">모집 시작<input type="datetime-local"
                                                                              value={toDateTimeInput(postingForm.opensAt)}
                                                                              onChange={(event) => setPostingForm({
                                                                                  ...postingForm,
                                                                                  opensAt: event.target.value || null
                                                                              })}/></label>
                                    <label className="form-field">모집 종료<input type="datetime-local"
                                                                              value={toDateTimeInput(postingForm.closesAt)}
                                                                              onChange={(event) => setPostingForm({
                                                                                  ...postingForm,
                                                                                  closesAt: event.target.value || null
                                                                              })}/></label>
                                    <label className="form-field admin-form-wide">공고 본문<textarea maxLength={30000}
                                                                                                 required rows={7}
                                                                                                 value={postingForm.description}
                                                                                                 onChange={(event) => setPostingForm({
                                                                                                     ...postingForm,
                                                                                                     description: event.target.value
                                                                                                 })}/></label>
                                    <label className="admin-checkbox admin-form-wide"><input type="checkbox"
                                                                                             checked={postingForm.manuallyClosed}
                                                                                             onChange={(event) => setPostingForm({
                                                                                                 ...postingForm,
                                                                                                 manuallyClosed: event.target.checked
                                                                                             })}/>모집을 수동으로 마감</label>
                                </div>
                                <div className="admin-form-actions">
                                    <button className="secondary-button" type="button"
                                            onClick={() => setPostingFormOpen(false)}>취소
                                    </button>
                                    <button className="primary-button admin-primary" type="submit"
                                            disabled={saving}>{saving ? '저장 중…' : '저장'}</button>
                                </div>
                            </form>
                        )}
                        {loading ?
                            <p className="admin-empty" role="status">공고 목록을 불러오는 중입니다.</p> : postings.length === 0 ?
                                <p className="admin-empty">등록된 채용공고가 없습니다.</p> : (
                                    <div className="admin-table-wrap">
                                        <table className="admin-table">
                                            <thead>
                                            <tr>
                                                <th>공고</th>
                                                <th>기업</th>
                                                <th>고용 형태</th>
                                                <th>상태</th>
                                                <th>관리</th>
                                            </tr>
                                            </thead>
                                            <tbody>
                                            {postings.map((posting) => <tr key={posting.id}>
                                                <td><strong>{posting.title}</strong><small>{posting.jobRole}</small>
                                                </td>
                                                <td>{posting.companyName}</td>
                                                <td>{employmentTypes.find((item) => item.value === posting.employmentType)?.label ?? posting.employmentType}</td>
                                                <td><span
                                                    className={`status-pill status-${posting.status.toLowerCase()}`}>{statusLabels[posting.status]}</span>
                                                </td>
                                                <td className="admin-row-actions">
                                                    <button className="text-button" type="button"
                                                            onClick={() => void startEditPosting(posting)}>수정
                                                    </button>
                                                    <button className="text-button danger-button" type="button"
                                                            onClick={() => void removePosting(posting)}>삭제
                                                    </button>
                                                </td>
                                            </tr>)}
                                            </tbody>
                                        </table>
                                    </div>
                                )}
                        {!loading && postingTotalPages > 1 && <div className="admin-pagination">
                            <button className="secondary-button" type="button" disabled={postingPageNumber === 0}
                                    onClick={() => {
                                        setLoading(true);
                                        setPostingPageNumber((page) => page - 1)
                                    }}>이전
                            </button>
                            <span>{postingPageNumber + 1} / {postingTotalPages}</span>
                            <button className="secondary-button" type="button"
                                    disabled={postingPageNumber + 1 >= postingTotalPages}
                                    onClick={() => {
                                        setLoading(true);
                                        setPostingPageNumber((page) => page + 1)
                                    }}>다음
                            </button>
                        </div>}
                    </section>
                )}
            </section>
        </main>
    )
}
