import {getInterviewGrowthAnalysis, type InterviewGrowthAnalysis} from "../api/interviewGrowthApi.ts";
import {ApiError} from "../api/ApiError.ts";
import {useEffect, useState} from "react";
import {Link} from "react-router-dom";

interface GrowthFilters {
    from: string;
    to: string;
    jobRole: string;
    companyId: string;
    jobPostingId: string;
}

type GrowthLoadState =
    | { filtersKey: string; analysis: InterviewGrowthAnalysis }
    | { filtersKey: string; error: string }

const questionTypeLabels: Record<string, string> = {
    TECHNICAL: "기술",
    BEHAVIORAL: "인성",
    FOLLOW_UP: "꼬리 질문",
};

const dimensionLabels: Record<string, string> = {
    STAR: "STAR 구성",
    LOGIC: "논리성",
    JOB_FIT: "직무 적합성",
};

function toDateInputValue(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');

    return `${year}-${month}-${day}`;
}

function defaultFilters(): GrowthFilters {
    const to = new Date();
    const from = new Date();
    from.setDate(from.getDate() - 89);

    return {
        from: toDateInputValue(from),
        to: toDateInputValue(to),
        jobRole: "",
        companyId: "",
        jobPostingId: "",
    };
}

function dateDifferenceInDays(from: string, to: string): number | null {
    if (!from || !to) return null;

    const fromTime = Date.parse(`${from}T00:00:00Z`);
    const toTime = Date.parse(`${to}T00:00:00Z`);

    if (!Number.isFinite(fromTime) || !Number.isFinite(toTime)) return null;

    return (toTime - fromTime) / (24 * 60 * 60 * 1000);
}

function filtersKey(filters: GrowthFilters): string {
    return JSON.stringify(filters);
}

function formatScore(score: number | null | undefined): string {
    return score === null || score === undefined ? "-" : `${score.toFixed(1)}점`;
}

function formatDate(value: string): string {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;

    return new Intl.DateTimeFormat("ko-KR", {
        year: "numeric",
        month: "short",
        day: "numeric",
    }).format(date);
}

function errorMessageOf(error: unknown): string {
    if (error instanceof ApiError) return error.message;

    return "성장 분석을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.";
}

function ScoreCard({label, score, featured = false}: {
    label: string;
    score: number | null;
    featured?: boolean;
}) {
    return (
        <div className={featured ? "growth-score-card growth-score-featured" : "growth-score-card"}>
            <span>{label}</span>
            <strong>{formatScore(score)}</strong>
        </div>
    );
}

export function InterviewGrowthPage() {
    const [filters, setFilters] = useState<GrowthFilters>(defaultFilters);
    const [submittedFilters, setSubmittedFilters] = useState<GrowthFilters>(defaultFilters);
    const [loadState, setLoadState] = useState<GrowthLoadState | null>(null);
    const [retryCount, setRetryCount] = useState(0);

    const currentKey = filtersKey(submittedFilters);
    const currentState = loadState?.filtersKey === currentKey ? loadState : null;
    const analysis = currentState && "analysis" in currentState ? currentState.analysis : null;
    const errorMessage = currentState && "error" in currentState ? currentState.error : "";
    const loading = currentState === null;

    useEffect(() => {
        let active = true;

        void getInterviewGrowthAnalysis({
            from: submittedFilters.from,
            to: submittedFilters.to,
            jobRole: submittedFilters.jobRole || undefined,
            companyId: submittedFilters.companyId ? Number(submittedFilters.companyId) : undefined,
            jobPostingId: submittedFilters.jobPostingId ? Number(submittedFilters.jobPostingId) : undefined,
        })
            .then((response) => {
                if (active) {
                    setLoadState({
                        filtersKey: filtersKey(submittedFilters),
                        analysis: response,
                    });
                }
            })
            .catch((error: unknown) => {
                if (active) {
                    setLoadState({
                        filtersKey: filtersKey(submittedFilters),
                        error: errorMessageOf(error),
                    });
                }
            });

        return () => {
            active = false;
        };

    }, [submittedFilters, retryCount]);

    function updateFilter<K extends keyof GrowthFilters>(key: K, value: GrowthFilters[K]) {
        setFilters((current) => ({...current, [key]: value}));
    }

    function resetFilters() {
        setFilters(defaultFilters());
    }

    const invalidRange = Boolean(filters.from && filters.to && filters.from > filters.to);
    const rangeTooLong = (dateDifferenceInDays(filters.from, filters.to) ?? 0) > 365;
    const invalidIds =
        (filters.companyId && (!Number.isSafeInteger(Number(filters.companyId)) || Number(filters.companyId) < 1))
        || (filters.jobPostingId && (!Number.isSafeInteger(Number(filters.jobPostingId)) || Number(filters.jobPostingId) < 1));

    const canSubmit = !loading
        && !invalidRange
        && !rangeTooLong
        && !invalidIds
        && Boolean(filters.from)
        && Boolean(filters.to);

    return (
        <main className="workspace growth-workspace">
            <header className="workspace-header">
                <Link className="brand" to="/">
                    <span className="brand-mark">i</span>
                    <span>interview<span className="brand-accent">AI</span></span>
                </Link>
                <div className="workspace-header-actions">
                    <Link className="secondary-button" to="/catalog">채용공고</Link>
                </div>
            </header>

            <section className="growth-content">
                <p className="eyebrow">YOUR GROWTH</p>
                <h1>연습의 변화를 확인해요.</h1>
                <p className="workspace-intro">
                    면접 결과를 모아 강점과 다음 연습 목표를 살펴보세요.
                </p>

                <form
                    className="growth-filter-card"
                    onSubmit={(event) => {
                        event.preventDefault();

                        if (canSubmit) {
                            setLoadState(null);
                            setSubmittedFilters({...filters});
                        }
                    }}
                >
                    <div className="growth-filter-grid">
                        <label>
                            시작일
                            <input
                                type="date"
                                value={filters.from}
                                max={filters.to || undefined}
                                onChange={(event) => updateFilter("from", event.target.value)}
                            />
                        </label>
                        <label>
                            종료일
                            <input
                                type="date"
                                value={filters.to}
                                min={filters.from || undefined}
                                onChange={(event) => updateFilter("to", event.target.value)}
                            />
                        </label>
                        <label>
                            직무
                            <input
                                type="text"
                                value={filters.jobRole}
                                maxLength={100}
                                placeholder="예: 백엔드 개발"
                                onChange={(event) => updateFilter("jobRole", event.target.value)}
                            />
                        </label>
                        <label>
                            기업 ID
                            <input
                                type="number"
                                min="1"
                                step="1"
                                value={filters.companyId}
                                placeholder="선택"
                                onChange={(event) => updateFilter("companyId", event.target.value)}
                            />
                        </label>
                        <label>
                            공고 ID
                            <input
                                type="number"
                                min="1"
                                step="1"
                                value={filters.jobPostingId}
                                placeholder="선택"
                                onChange={(event) => updateFilter("jobPostingId", event.target.value)}
                            />
                        </label>
                    </div>
                    {
                        rangeTooLong && (
                            <p className="form-alert" role="alert">
                                분석 기간은 최대 365일까지 선택할 수 있습니다.
                            </p>
                        )
                    }
                    {
                        invalidIds && <p className="form-alert" role="alert">기업 ID와 공고 ID는 1 이상의 정수여야 합니다.</p>
                    }
                    <div className="growth-filter-actions">
                        <button className="secondary-button" type="button" onClick={resetFilters}>
                            기본값으로 초기화
                        </button>
                        <button className="primary-button" type="submit" disabled={!canSubmit}>
                            {loading ? "분석 중…" : "분석하기"}
                        </button>
                    </div>
                </form>

                {
                    loading && !analysis && <p className="growth-message" role="status">성장 분석을 불러오고 있습니다.</p>
                }
                {
                    errorMessage && (
                        <div className="growth-message growth-error" role="alert">
                            <p>{errorMessage}</p>
                            <button
                                className="secondary-button"
                                type="button"
                                onClick={() => {
                                    setLoadState(null);
                                    setRetryCount((count) => count + 1);
                                }}
                            >
                                다시 시도
                            </button>
                        </div>
                    )
                }

                {
                    analysis && (
                        <>
                            <section className="growth-section" aria-label="기간 요약">
                                <div className="growth-section-heading">
                                    <div>
                                        <p className="card-kicker">OVERVIEW</p>
                                        <h2>분석 요약</h2>
                                    </div>
                                    <span>{analysis.period.from} – {analysis.period.to}</span>
                                </div>
                                <div className="growth-score-grid">
                                    <ScoreCard label="종합 평균" score={analysis.summary.averageScore} featured/>
                                    <ScoreCard label="STAR 구성" score={analysis.summary.starScore}/>
                                    <ScoreCard label="논리성" score={analysis.summary.logicScore}/>
                                    <ScoreCard label="직무 적합성" score={analysis.summary.jobFitScore}/>
                                </div>
                                <p className="growth-meta">
                                    면접 {analysis.summary.sessionCount}회 · 평가 답변 {analysis.summary.evaluatedAnswerCount}개
                                    {analysis.summary.excludedSessionCount > 0
                                        && ` · 평가가 없어 제외된 면접 ${analysis.summary.excludedSessionCount}회`}
                                </p>
                            </section>

                            {analysis.insufficientData && (
                                <p className="growth-notice">
                                    아직 분석 표본이 충분하지 않습니다. 평가 완료 답변이
                                    {" "}{analysis.minimumEvaluatedAnswerCount}개 이상 쌓이면 강점과 보완점을 더 정확히 볼 수 있어요.
                                </p>
                            )}

                            <section className="growth-section">
                                <div className="growth-section-heading">
                                    <div>
                                        <p className="card-kicker">TREND</p>
                                        <h2>면접별 점수 추이</h2>
                                    </div>
                                </div>
                                {analysis.trends.length === 0 ? (
                                    <p className="growth-empty">선택한 조건에 해당하는 평가 완료 면접이 없습니다.</p>
                                ) : (
                                    <ol className="growth-trend-list">
                                        {analysis.trends.map((trend) => (
                                            <li key={trend.sessionId}>
                                                <Link to={`/interviews/${trend.sessionId}/result`}
                                                      className="growth-trend-link">
                                                <span
                                                    className="growth-trend-date">{formatDate(trend.completedAt)}</span>
                                                    <span className="growth-trend-info">
                                                    <strong>{trend.companyName}</strong>
                                                    <small>{trend.jobRole} · 평가 답변 {trend.evaluatedAnswerCount}개</small>
                                                </span>
                                                    <span
                                                        className="growth-trend-score">{formatScore(trend.averageScore)}</span>
                                                </Link>
                                            </li>
                                        ))}
                                    </ol>
                                )}
                            </section>

                            {analysis.change && (
                                <section className="growth-section">
                                    <div className="growth-section-heading">
                                        <div>
                                            <p className="card-kicker">CHANGE</p>
                                            <h2>최근 변화</h2>
                                        </div>
                                    </div>
                                    <p className="growth-notice">
                                        최근 {analysis.change.recentSessionCount}회와 이전 {analysis.change.previousSessionCount}회의
                                        평균을 비교했어요.
                                    </p>
                                    <div className="growth-score-grid">
                                        <ChangeCard label="종합 평균" score={analysis.change.averageScore}/>
                                        <ChangeCard label="STAR 구성" score={analysis.change.starScore}/>
                                        <ChangeCard label="논리성" score={analysis.change.logicScore}/>
                                        <ChangeCard label="직무 적합성" score={analysis.change.jobFitScore}/>
                                    </div>
                                </section>
                            )}

                            <section className="growth-section">
                                <div className="growth-section-heading">
                                    <div>
                                        <p className="card-kicker">INSIGHTS</p>
                                        <h2>강점과 보완점</h2>
                                    </div>
                                </div>
                                <div className="growth-insight-grid">
                                    <InsightList title="강점" items={analysis.strengths} tone="strength"/>
                                    <InsightList title="더 키울 점" items={analysis.weaknesses} tone="weakness"/>
                                </div>
                            </section>

                            <section className="growth-section">
                                <div className="growth-section-heading">
                                    <div>
                                        <p className="card-kicker">NEXT STEP</p>
                                        <h2>학습 로드맵</h2>
                                    </div>
                                </div>
                                {analysis.learningRoadmap.length === 0 ? (
                                    <p className="growth-empty">현재 조건에서 제안할 학습 목표가 없습니다.</p>
                                ) : (
                                    <ol className="growth-roadmap-list">
                                        {analysis.learningRoadmap.map((item) => (
                                            <li className="growth-roadmap-card"
                                                key={`${item.priority}-${item.dimension}-${item.questionType}`}>
                                                <span className="growth-roadmap-priority">우선순위 {item.priority}</span>
                                                <h3>{item.title}</h3>
                                                <p className="growth-roadmap-meta">
                                                    {questionTypeLabels[item.questionType] ?? item.questionType}
                                                    {" · "}{dimensionLabels[item.dimension] ?? item.dimension}
                                                    {" · 현재 "}{formatScore(item.currentScore)}
                                                    {" → 목표 "}{formatScore(item.targetScore)}
                                                </p>
                                                <ul>
                                                    {item.actions.map((action) => <li key={action}>{action}</li>)}
                                                </ul>
                                                <p className="growth-roadmap-meta">
                                                    관련 답변 {item.evidence.sampleCount}개
                                                    {item.evidence.relatedSessionIds.length > 0 && (
                                                        <> · 관련 면접{" "}
                                                            {item.evidence.relatedSessionIds.map((id, index) => (
                                                                <span key={id}>
                                                                {index > 0 && ", "}
                                                                    <Link to={`/interviews/${id}/result`}>#{id}</Link>
                                                            </span>
                                                            ))}
                                                        </>
                                                    )}
                                                </p>
                                            </li>
                                        ))}
                                    </ol>
                                )}
                            </section>
                        </>
                    )
                }
            </section>
        </main>
    )
        ;
}

function ChangeCard({label, score}: { label: string; score: number | null }) {
    const text = score === null ? "-" : `${score > 0 ? "+" : ""}${score.toFixed(1)}점`;
    const tone = score === null ? "" : score > 0 ? "growth-change-up" : score < 0 ? "growth-change-down" : "";

    return (
        <div className="growth-score-card">
            <span>{label}</span>
            <strong className={tone}>{text}</strong>
        </div>
    );
}

function InsightList({title, items, tone}: {
    title: string;
    items: InterviewGrowthAnalysis["strengths"];
    tone: "strength" | "weakness";
}) {
    return (
        <section className={`growth-insight-card growth-insight-${tone}`}>
            <h3>{title}</h3>
            {items.length === 0 ? (
                <p className="growth-empty">표시할 항목이 없습니다.</p>
            ) : (
                <ul>
                    {items.map((item, index) => (
                        <li key={`${item.dimension}-${item.questionType}-${index}`}>
                            <div className="growth-insight-heading">
                                <strong>
                                    {questionTypeLabels[item.questionType] ?? item.questionType}
                                    {" · "}{dimensionLabels[item.dimension] ?? item.dimension}
                                </strong>
                                <span>{formatScore(item.score)}</span>
                            </div>
                            <p>{item.reason}</p>
                            <small>표본 {item.sampleCount}개</small>
                        </li>
                    ))}
                </ul>
            )}
        </section>
    );
}