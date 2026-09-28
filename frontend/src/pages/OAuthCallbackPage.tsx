import {useAuth} from "../auth/useAuth";
import {useNavigate} from "react-router-dom";
import {useEffect, useRef} from "react";

function getOAuthReturnPath(): string {
    const savedPath = sessionStorage.getItem('oauth_return_to')
    sessionStorage.removeItem('oauth_return_to')

    if (!savedPath || !savedPath.startsWith('/') || savedPath.startsWith('//')) {
        return '/'
    }

    return savedPath
}

export function OAuthCallbackPage() {
    const {refresh, isInitializing, isAuthenticated} = useAuth()
    const navigate = useNavigate()
    const started = useRef(false)

    useEffect(() => {
        if (isInitializing || started.current) {
            return
        }

        started.current = true

        if (isAuthenticated) {
            navigate(getOAuthReturnPath(), {replace: true})
            return
        }

        void refresh()
            .then(() => {
                navigate(getOAuthReturnPath(), {replace: true})
            })
            .catch(() => {
                navigate('/login', {
                    replace: true,
                    state: {oauthError: 'OAuth 로그인에 실패했습니다. 다시 시도해 주세요.'},
                })
            })
    }, [isAuthenticated, isInitializing, navigate, refresh])

    return (
        <main className="callback-shell">
            <div className="callback-card">
                <span className="brand-mark callback-mark">i</span>
                <p className="eyebrow">SECURE SIGN IN</p>
                <h1>로그인 정보를 확인하고 있어요</h1>
                <p role="status">잠시만 기다려 주세요. 준비가 끝나면 자동으로 이동합니다.</p>
                <span className="loading-indicator" aria-hidden="true"/>
            </div>
        </main>
    )
}