import {
    type CompanyDetails,
    type CompanySummary,
    getCompany,
    getJobPosting,
    type JobPostingDetails,
    type JobPostingStatus,
    type JobPostingSummary,
    listCompanies,
    listCompanyJobPostings,
    listFavoriteCompanies,
    listJobPostings,
    type Page,
    setCompanyFavorite
} from "../api/catalogApi";
import {ApiError} from "../api/ApiError";
import {type SubmitEvent, useEffect, useState} from "react";
import {Link} from "react-router-dom";

type Tab = 'companies' | 'postings' | 'favorites'
type Detail =
    | { kind: 'company'; value: CompanyDetails; postings: JobPostingSummary[] }
    | { kind: 'posting'; value: JobPostingDetails }

const statusLabels: Record<JobPostingStatus, string> = {
    SCHEDULED: '모집 예정',
    OPEN: '모집 중',
    CLOSED: '마감',
}

const employmentLabels = {
    FULL_TIME: '정규직',
    CONTRACT: '계약직',
    INTERN: '인턴',
    PART_TIME: '파트타임',
    OTHER: '기타',
} as const

function messageOf(error: unknown): string {
    if (error instanceof ApiError) return error.message

    return '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'
}

function formatDate(value: string | null): string {
    if (!value) return '미정'

    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return '미정'

    return new Intl.DateTimeFormat('ko-KR', {dateStyle: 'medium'}).format(date)
}

export function CatalogPage() {
    const [tab, setTab] = useState<Tab>('postings')
    const [keywordInput, setKeywordInput] = useState('')
    const [keyword, setKeyword] = useState('')
    const [status, setStatus] = useState<JobPostingStatus | ''>('')
    const [companyFilter, setCompanyFilter] = useState<number | ''>('')
    const [companyItems, setCompanyItems] = useState<CompanySummary[]>([])
    const [companyOptions, setCompanyOptions] = useState<CompanySummary[]>([])
    const [postingItems, setPostingItems] = useState<JobPostingSummary[]>([])
    const [totalElements, setTotalElements] = useState(0)
    const [totalPages, setTotalPages] = useState(0)
    const [page, setPage] = useState(0)
    const [detail, setDetail] = useState<Detail | null>(null)
    const [loadedRequestKey, setLoadedRequestKey] = useState('')
    const [busyId, setBusyId] = useState<number | null>(null)
    const [error, setError] = useState('')

    const requestKey = `${tab}:${keyword}:${status}:${companyFilter}:${page}`
    const loading = loadedRequestKey !== requestKey

    useEffect(() => {
        let active = true

        const request = tab === 'companies'
            ? listCompanies({keyword, page})
            : tab === 'favorites'
                ? listFavoriteCompanies({page})
                : listJobPostings({
                    keyword,
                    status: status || undefined,
                    companyId: companyFilter || undefined,
                    page,
                })

        void request
            .then((result) => {
                if (!active) return

                setError('')
                setCompanyItems(tab === 'postings' ? [] : result.items as CompanySummary[])
                setPostingItems(tab === 'postings' ? result.items as JobPostingSummary[] : [])
                setTotalElements(result.totalElements)
                setTotalPages(result.totalPages)
            })
            .catch((loadError: unknown) => {
                if (active) setError(messageOf(loadError))
            })
            .finally(() => {
                if (active) setLoadedRequestKey(requestKey)
            })

        return () => {
            active = false
        }
    }, [tab, keyword, status, companyFilter, page, requestKey])

    useEffect(() => {
        let active = true

        void listCompanies({page: 0, size: 100})
            .then((result: Page<CompanySummary>) => {
                if (active) setCompanyOptions(result.items)
            })
            .catch((loadError: unknown) => {
                if (active) setError(messageOf(loadError))
            })

        return () => {
            active = false
        }
    }, [])

    function changeTab(nextTab: Tab) {
        setTab(nextTab)
        setPage(0)
        setDetail(null)
        setError('')
    }

    function submitSearch(event: SubmitEvent) {
        event.preventDefault()
        setPage(0)
        setKeyword(keywordInput.trim())
    }

    async function openCompany(company: CompanySummary) {
        setError('')

        try {
            const [companyDetails, postingsPage] = await Promise.all([
                getCompany(company.id),
                listCompanyJobPostings(company.id, {page: 0, size: 5}),
            ])

            setDetail({kind: 'company', value: companyDetails, postings: postingsPage.items,})
            setError('')

        } catch (loadError) {
            setError(messageOf(loadError))
        }
    }

    async function openPosting(posting: JobPostingSummary) {
        setError('')

        try {
            const result = await getJobPosting(posting.id)
            setDetail({kind: 'posting', value: result})

        } catch (loadError) {
            setError(messageOf(loadError))
        }
    }

    async function toggleFavorite(company: CompanySummary) {
        setBusyId(company.id)
        setError('')

        try {
            await setCompanyFavorite(company.id, !company.favorite)

            if (tab === 'favorites' && company.favorite) {
                setCompanyItems((items) =>
                    items.filter((item) => item.id !== company.id))
                setTotalElements((count) => Math.max(0, count - 1))

            } else {
                setCompanyItems((items) => items.map((item) =>
                    item.id === company.id ? {...item, favorite: !item.favorite} : item,
                ))
            }

            if (detail?.kind === 'company' && detail.value.id === company.id) {
                setDetail({
                    kind: 'company',
                    value: {...detail.value, favorite: !company.favorite},
                    postings: detail.postings,
                })
            }

        } catch (saveError) {
            setError(messageOf(saveError))

        } finally {
            setBusyId(null)
        }
    }

    async function toggleFavoriteFromDetail() {
        if (detail?.kind !== 'company') return

        await toggleFavorite(detail.value)
    }

    return (
        <main className="workspace catalog-workspace">
            <header className="workspace-header">
                <Link className="brand" to="/">
                    <span className="brand-mark">i</span>
                    <span>interview<span className="brand-accent">AI</span></span>
                </Link>
                <div className="workspace-header-actions">
                    <Link className="secondary-button" to="/">홈</Link>
                </div>
            </header>

            <section className="catalog-content">
                <p className="eyebrow">COMPANY & JOB EXPLORER</p>
                <div className="catalog-title-row">
                    <div>
                        <h1>나에게 맞는 기회를 찾아보세요.</h1>
                        <p className="workspace-intro">기업과 채용공고를 살펴보고 관심 기업을 모아보세요.</p>
                    </div>
                    <span className="admin-count">검색 결과 {totalElements}건</span>
                </div>

                {error && <p className="form-alert admin-message" role="alert">{error}</p>}

                <div className="admin-tabs" role="tablist" aria-label="기업·채용공고 탐색">
                    <button className={tab === 'postings' ? 'admin-tab active' : 'admin-tab'}
                            type="button" role="tab" aria-selected={tab === 'postings'}
                            onClick={() => changeTab('postings')}>채용공고
                    </button>
                    <button className={tab === 'companies' ? 'admin-tab active' : 'admin-tab'}
                            type="button" role="tab" aria-selected={tab === 'companies'}
                            onClick={() => changeTab('companies')}>기업
                    </button>
                    <button className={tab === 'favorites' ? 'admin-tab active' : 'admin-tab'}
                            type="button" role="tab" aria-selected={tab === 'favorites'}
                            onClick={() => changeTab('favorites')}>관심 기업
                    </button>
                </div>

                <section className="catalog-panel">
                    <form className="catalog-filters" onSubmit={submitSearch}>
                        <label className="catalog-search">
                            <span className="sr-only">검색어</span>
                            <input value={keywordInput}
                                   onChange={(event) => setKeywordInput(event.target.value)}
                                   placeholder={tab === 'companies' ? '기업명 검색' : '공고 제목 검색'}
                                   disabled={tab === 'favorites'}/>
                        </label>
                        {tab === 'postings' && (
                            <select aria-label="모집 상태" value={status}
                                    onChange={(event) => {
                                        setPage(0)
                                        setStatus(event.target.value as JobPostingStatus | '')
                                    }}>
                                <option value="">모든 상태</option>
                                <option value="OPEN">모집 중</option>
                                <option value="SCHEDULED">모집 예정</option>
                                <option value="CLOSED">마감</option>
                            </select>
                        )}
                        {tab === 'postings' && (
                            <select
                                aria-label="기업별 공고 필터"
                                value={companyFilter}
                                onChange={(event) => {
                                    setPage(0)
                                    setCompanyFilter(event.target.value ? Number(event.target.value) : '')
                                }}
                            >
                                <option value="">모든 기업</option>
                                {companyOptions.map((company) => (
                                    <option key={company.id} value={company.id}>
                                        {company.name}
                                    </option>
                                ))}
                            </select>
                        )}
                        <button className="primary-button catalog-search-button" type="submit">검색</button>
                    </form>

                    {loading ? (
                        <p className="catalog-state" role="status">목록을 불러오고 있습니다.</p>
                    ) : tab === 'postings' ? (
                        postingItems.length === 0
                            ? <p className="catalog-state">조건에 맞는 채용공고가 없습니다.</p>
                            : <div className="catalog-list">
                                {postingItems.map((posting) => (
                                    <article className="catalog-card" key={posting.id}>
                                        <button className="catalog-card-main" type="button"
                                                onClick={() => void openPosting(posting)}>
                                            <span className={`status-pill status-${posting.status.toLowerCase()}`}>
                                                {statusLabels[posting.status]}
                                            </span>
                                            <h2>{posting.title}</h2>
                                            <p className="catalog-company">{posting.companyName} · {posting.jobRole}</p>
                                            <p className="catalog-meta">
                                                {employmentLabels[posting.employmentType]} · {posting.location || '근무지 미정'}
                                            </p>
                                            <small>마감일 {formatDate(posting.closesAt)}</small>
                                        </button>
                                        <button className="text-button" type="button"
                                                onClick={() => void openPosting(posting)}>상세 보기
                                        </button>
                                    </article>
                                ))}
                            </div>
                    ) : companyItems.length === 0 ? (
                        <p className="catalog-state">
                            {tab === 'favorites' ? '등록한 관심 기업이 없습니다.' : '검색 결과가 없습니다.'}
                        </p>
                    ) : (
                        <div className="catalog-list">
                            {companyItems.map((company) => (
                                <article className="catalog-card company-card" key={company.id}>
                                    <button className="catalog-card-main" type="button"
                                            onClick={() => void openCompany(company)}>
                                        <span className="catalog-company-mark" aria-hidden="true">
                                            {company.name.slice(0, 1)}
                                        </span>
                                        <h2>{company.name}</h2>
                                        <p className="catalog-meta">
                                            {[company.industry, company.location].filter(Boolean).join(' · ') || '기업 정보'}
                                        </p>
                                    </button>
                                    <button
                                        className={company.favorite ? 'favorite-button selected' : 'favorite-button'}
                                        type="button" disabled={busyId === company.id}
                                        aria-label={company.favorite ? `${company.name} 관심 해제` : `${company.name} 관심 등록`}
                                        onClick={() => void toggleFavorite(company)}>
                                        {company.favorite ? '♥ 관심' : '♡ 관심'}
                                    </button>
                                </article>
                            ))}
                        </div>
                    )}

                    {totalPages > 1 && (
                        <nav className="admin-pagination" aria-label="목록 페이지">
                            <button className="secondary-button" type="button" disabled={page === 0 || loading}
                                    onClick={() => setPage((current) => current - 1)}>이전
                            </button>
                            <span>{page + 1} / {totalPages}</span>
                            <button className="secondary-button" type="button"
                                    disabled={page + 1 >= totalPages || loading}
                                    onClick={() => setPage((current) => current + 1)}>다음
                            </button>
                        </nav>
                    )}
                </section>
            </section>

            {detail && (
                <div className="catalog-overlay" role="presentation" onMouseDown={(event) => {
                    if (event.target === event.currentTarget) setDetail(null)
                }}>
                    <section className="catalog-detail" role="dialog" aria-modal="true"
                             aria-labelledby="catalog-detail-title">
                        <button className="catalog-close" type="button" aria-label="상세 닫기"
                                onClick={() => setDetail(null)}>×
                        </button>
                        {detail.kind === 'company' ? (
                            <>
                                <p className="eyebrow">COMPANY</p>
                                <h2 id="catalog-detail-title">{detail.value.name}</h2>
                                <p className="catalog-meta">
                                    {[detail.value.industry, detail.value.location].filter(Boolean).join(' · ')}
                                </p>
                                <p className="catalog-description">{detail.value.description || '등록된 기업 소개가 없습니다.'}</p>
                                {detail.value.websiteUrl && (
                                    <a href={detail.value.websiteUrl} target="_blank" rel="noreferrer">
                                        홈페이지 방문 ↗
                                    </a>
                                )}
                                <h3>채용공고</h3>
                                {detail.postings.length === 0 ? (
                                    <p className="catalog-meta">등록된 채용공고가 없습니다.</p>
                                ) : (
                                    <ul className="company-posting-list">
                                        {detail.postings.map((posting) => (
                                            <li key={posting.id}>
                                                <button type="button" onClick={() => void openPosting(posting)}>
                                                    <span>{posting.title}</span>
                                                    <span>{statusLabels[posting.status]}</span>
                                                </button>
                                            </li>
                                        ))}
                                    </ul>
                                )}
                                <button className="secondary-button catalog-detail-action" type="button"
                                        onClick={() => void toggleFavoriteFromDetail()}>
                                    {detail.value.favorite ? '♥ 관심 기업 해제' : '♡ 관심 기업 등록'}
                                </button>
                            </>
                        ) : (
                            <>
                                <p className="eyebrow">{statusLabels[detail.value.status]}</p>
                                <h2 id="catalog-detail-title">{detail.value.title}</h2>
                                <p className="catalog-company">{detail.value.companyName} · {detail.value.jobRole}</p>
                                <p className="catalog-meta">
                                    {employmentLabels[detail.value.employmentType]} · {detail.value.location || '근무지 미정'}
                                </p>
                                <p className="catalog-meta">
                                    모집 기간 {formatDate(detail.value.opensAt)} – {formatDate(detail.value.closesAt)}
                                </p>
                                <p className="catalog-description">{detail.value.description}</p>
                                {detail.value.sourceUrl && (
                                    <a href={detail.value.sourceUrl} target="_blank" rel="noreferrer">
                                        채용 페이지 방문 ↗
                                    </a>
                                )}
                            </>
                        )}
                    </section>
                </div>
            )}
        </main>
    )
}