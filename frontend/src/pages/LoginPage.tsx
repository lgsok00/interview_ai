import {useAuth} from "../auth/useAuth";
import {Link, Navigate, useLocation, useNavigate} from "react-router-dom";
import {type SubmitEvent, useState} from "react";
import {ApiError} from "../api/ApiError";
import {env} from "../config/env";

interface RedirectState {
    from?: {
        pathname?: string
        search?: string
        hash?: string
    }
    oauthError?: string
    accountMessage?: string
}

export function LoginPage() {
    const {isAuthenticated, isInitializing, login} = useAuth()
    const location = useLocation()
    const navigate = useNavigate()
    const redirectState = location.state as RedirectState | null
    const destination = redirectState?.from
    const [email, setEmail] = useState('')
    const [password, setPassword] = useState('')
    const [errorMessage, setErrorMessage] = useState(
        () => redirectState?.oauthError ?? '',
    )
    const [isSubmitting, setIsSubmitting] = useState(false)

    if (isInitializing) {
        return <p role="status">로그인 상태를 확인하고 있습니다.</p>
    }

    if (isAuthenticated) {
        return <Navigate to="/" replace/>
    }

    async function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
        event.preventDefault()
        setErrorMessage('')
        setIsSubmitting(true)

        try {
            await login({email, password})
            const path = destination?.pathname ?? '/'

            navigate(`${path}${destination?.search ?? ''}${destination?.hash ?? ''}`, {
                replace: true,
            })

        } catch (error) {
            setErrorMessage(
                error instanceof ApiError
                    ? error.message
                    : '로그인 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.',
            )

        } finally {
            setIsSubmitting(false)
        }
    }

    function startOAuth(provider: 'google' | 'github') {
        const path = destination?.pathname ?? '/'
        const returnTo = `${path}${destination?.search ?? ''}${destination?.hash ?? ''}`

        sessionStorage.setItem('oauth_return_to', returnTo)
        window.location.assign(
            `${env.apiBaseUrl}/oauth2/authorization/${provider}`,
        )
    }

    return (
        <main className="auth-shell">
            <section className="auth-visual" aria-label="Interview AI 소개">
                <Link className="brand brand-light" to="/">
                    <span className="brand-mark">i</span>
                    <span>interview<span className="brand-accent">AI</span></span>
                </Link>

                <div className="visual-copy">
                    <p className="eyebrow">PREPARE WITH PURPOSE</p>
                    <h2>
                        준비한 만큼,
                        <br/>
                        면접은 선명해집니다.
                    </h2>
                    <p className="visual-description">
                        나만의 경험을 바탕으로 연습하고,
                        <br/>
                        다음 면접을 자신 있게 준비하세요.
                    </p>
                </div>

                <div className="practice-card" aria-label="면접 준비 요약">
                    <div className="practice-card-top">
                        <span className="practice-label">이번 주 준비 현황</span>
                        <span className="practice-dot"/>
                    </div>
                    <div className="practice-stat">
                        <strong>꾸준히</strong>
                        <span>쌓아가는 나의 면접 연습</span>
                    </div>
                    <div className="practice-bars" aria-hidden="true">
                        <i/>
                        <i/>
                        <i/>
                        <i/>
                        <i/>
                        <i/>
                        <i/>
                    </div>
                    <div className="practice-card-bottom">
                        <span>자기소개서</span>
                        <span>면접 질문</span>
                        <span>성장 기록</span>
                    </div>
                </div>

                <p className="visual-footer">당신의 다음 기회를 준비하는 공간</p>
            </section>

            <section className="auth-panel">
                <div className="auth-card">
                    <div className="brand brand-mobile">
                        <span className="brand-mark">i</span>
                        <span>interview<span className="brand-accent">AI</span></span>
                    </div>

                    <p className="eyebrow">WELCOME BACK</p>
                    <h1>다시 만나 반가워요</h1>
                    <p className="auth-subtitle">계정에 로그인해 면접 준비를 이어가세요.</p>

                    {redirectState?.accountMessage && (
                        <p className="account-notice" role="status">
                            {redirectState.accountMessage}
                        </p>
                    )}

                    <form className="auth-form" onSubmit={handleSubmit}>
                        <label className="form-field">
                            <span>이메일</span>
                            <input
                                type="email"
                                name="email"
                                autoComplete="email"
                                placeholder="name@example.com"
                                value={email}
                                onChange={(event) => setEmail(event.target.value)}
                                required
                            />
                        </label>

                        <label className="form-field">
                            <span>비밀번호</span>
                            <input
                                type="password"
                                name="password"
                                autoComplete="current-password"
                                placeholder="비밀번호를 입력하세요"
                                value={password}
                                onChange={(event) => setPassword(event.target.value)}
                                required
                            />
                        </label>

                        {errorMessage && (
                            <p className="form-alert" role="alert">
                                {errorMessage}
                            </p>
                        )}

                        <button className="primary-button" type="submit" disabled={isSubmitting}>
                            {isSubmitting ? '로그인 중…' : '로그인'}
                            <span aria-hidden="true">→</span>
                        </button>
                    </form>

                    <div className="auth-divider">
                        <span>또는</span>
                    </div>

                    <div className="oauth-buttons" aria-label="소셜 로그인">
                        <button
                            className="oauth-button"
                            type="button"
                            onClick={() => startOAuth('google')}
                        >
                            <span className="google-mark" aria-hidden="true">G</span>
                            Google로 계속하기
                        </button>
                        <button
                            className="oauth-button"
                            type="button"
                            onClick={() => startOAuth('github')}
                        >
                            <span className="github-mark" aria-hidden="true">●</span>
                            GitHub로 계속하기
                        </button>
                    </div>

                    <p className="auth-account-link">
                        아직 계정이 없나요? <Link to="/signup">회원가입</Link>
                    </p>

                    <p className="auth-legal">
                        로그인하면 서비스 이용 약관 및 개인정보 처리방침에 동의한 것으로 간주됩니다.
                    </p>
                </div>
            </section>
        </main>
    )
}