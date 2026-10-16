import {type AiUsage, aiUsageLabels, getAiUsage} from "../api/aiUsageApi";
import {useEffect, useState} from "react";

type UsageState =
    | { status: 'loading' }
    | { status: 'error' }
    | { status: 'ready'; usage: AiUsage }

export function AiUsageNoticePage() {
    const [state, setState] = useState<UsageState>({status: 'loading'})
    const [revision, setRevision] = useState(0)

    useEffect(() => {
        const controller = new AbortController()
        let sequence = 0

        async function load() {
            const requestId = ++sequence
            setState({status: 'loading'})

            try {
                const usage = await getAiUsage(controller.signal)

                if (!controller.signal.aborted && requestId === sequence) {
                    setState({status: 'ready', usage})
                }

            } catch {
                if (!controller.signal.aborted && requestId === sequence) {
                    setState({status: 'error'})
                }
            }
        }

        function handleVisibility() {
            if (document.visibilityState === 'visible') {
                void load()
            }
        }

        void load()
        document.addEventListener('visibilitychange', handleVisibility)

        return () => {
            controller.abort()
            document.removeEventListener('visibilitychange', handleVisibility)
        }
    }, [revision]);

    const usage = state.status === 'ready' ? state.usage : null

    return (
        <section className="ai-usage-notice" aria-label="AI 사용량 안내">
            <div className="ai-usage-heading">
                <h2>오늘의 AI 사용량</h2>
                <button
                    className="secondary-button"
                    type="button"
                    disabled={state.status === 'loading'}
                    onClick={() => setRevision(value => value + 1)}
                >
                    새로고침
                </button>
            </div>

            {state.status === 'loading' && (
                <p role="status">AI 사용량을 확인하고 있습니다.</p>
            )}

            {state.status === 'error' && (
                <p role="alert">
                    AI 사용량을 확인할 수 없습니다.
                    새 AI 요청은 잠시 멈추고 새로고침해 주세요.
                </p>
            )}

            {usage && !usage.enabled && (
                <p>현재 환경에서는 AI 사용량 제한이 비활성화되어 있습니다.</p>
            )}

            {usage?.enabled && (
                <>
                    {!usage.chatAdmissionEnabled && (
                        <p role="status">
                            새 AI 작업 접수가 일시 중단되었습니다.
                            접수 재개 후 다시 요청해 주세요.
                        </p>
                    )}

                    <ul>
                        {usage.features.map(item => (
                            <li key={item.feature}>
                                <strong>{aiUsageLabels[item.feature]}</strong>
                                <span>
                                    오늘 {item.used} / {item.limit}회 접수
                                    · {item.remaining}회 남음
                                </span>
                            </li>
                        ))}
                    </ul>

                    <p>
                        대기·실행 중 {usage.active} / {usage.activeLimit}건
                    </p>

                    {usage.active >= usage.activeLimit && (
                        <p role="status">
                            진행 중인 AI 작업이 끝난 뒤 새 작업을 요청해 주세요.
                        </p>
                    )}

                    <p>
                        다음 횟수 초기화:{' '}
                        {new Intl.DateTimeFormat('ko-KR', {
                            timeZone: 'Asia/Seoul',
                            month: 'long',
                            day: 'numeric',
                            hour: '2-digit',
                            minute: '2-digit',
                            hourCycle: 'h23',
                        }).format(new Date(usage.resetAt))}
                        {' '}(한국 시간)
                    </p>

                    <p>
                        접수 후 생성에 실패해도 횟수는 반환되지 않습니다.
                        수동 재시도는 새 접수이며, 자동 재시도는 추가 차감하지 않습니다.
                        남은 횟수가 있어도 전체 서비스 상황에 따라 접수가 제한될 수 있습니다.
                    </p>
                </>
            )}

            <p>
                답변 저장·기존 결과 조회·초안 검토와 적용은 Chat 횟수를 차감하지 않습니다.
            </p>
        </section>
    )
}