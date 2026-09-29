import {useAuth} from "../auth/useAuth";
import {useEffect, useState} from "react";
import {Link} from "react-router-dom";
import {apiRequest} from "../api/client";

interface CurrentUser {
    role: 'USER' | 'ADMIN'
}

export function HomePage() {
    const {logout} = useAuth()
    const [errorMessage, setErrorMessage] = useState('')
    const [isAdmin, setIsAdmin] = useState(false)

    useEffect(() => {
        let active = true

        void apiRequest<CurrentUser>('/api/users/me', {authenticated: true})
            .then((user) => {
                if (active) {
                    setIsAdmin(user.role === 'ADMIN')
                }
            })
            .catch(() => {
                if (active) {
                    setIsAdmin(false)
                }
            })

        return () => {
            active = false
        }
    }, [])

    async function handleLogout() {
        setErrorMessage('')

        try {
            await logout()

        } catch {
            setErrorMessage('서버 로그아웃 요청에 실패했습니다. 이 브라우저의 인증 정보는 삭제했습니다.',)
        }
    }

    return (
        <main className="workspace">
            <header className="workspace-header">
                <Link className="brand" to="/">
                    <span className="brand-mark">i</span>
                    <span>interview<span className="brand-accent">AI</span></span>
                </Link>

                <div className="workspace-header-actions">
                    <span className="account-label">내 면접 준비 공간</span>
                    {isAdmin && <Link className="secondary-button" to="/admin">관리자</Link>}
                    <button
                        className="secondary-button"
                        type="button"
                        onClick={() => void handleLogout()}
                    >
                        로그아웃
                    </button>
                </div>
            </header>

            <section className="workspace-content">
                <p className="eyebrow">YOUR NEXT MOVE STARTS HERE</p>
                <h1>오늘도 한 걸음씩 준비해요.</h1>
                <p className="workspace-intro">
                    나의 경험을 정리하고, 실전 면접을 연습할 준비가 되었습니다.
                </p>

                {errorMessage && (
                    <p className="form-alert workspace-alert" role="alert">
                        {errorMessage}
                    </p>
                )}

                <section className="workspace-grid" aria-label="면접 준비 메뉴">
                    <article className="workspace-card card-primary">
                        <span className="card-icon" aria-hidden="true">01</span>
                        <p className="card-kicker">PRACTICE</p>
                        <h2>면접 연습</h2>
                        <p>내 이력과 지원 공고를 바탕으로 질문에 답해보세요.</p>
                        <span className="card-arrow" aria-hidden="true">↗</span>
                    </article>

                    <article className="workspace-card">
                        <span className="card-icon card-icon-mint" aria-hidden="true">02</span>
                        <p className="card-kicker">MY STORY</p>
                        <h2>내 자료 정리</h2>
                        <p>자기소개서와 이력서를 한곳에서 관리하세요.</p>
                        <span className="card-arrow" aria-hidden="true">↗</span>
                    </article>

                    <Link className="workspace-card" to="/growth">
                        <span className="card-icon card-icon-lilac" aria-hidden="true">03</span>
                        <p className="card-kicker">GROWTH</p>
                        <h2>성장 기록</h2>
                        <p>연습 결과를 돌아보고 다음 목표를 세워보세요.</p>
                        <span className="card-arrow" aria-hidden="true">↗</span>
                    </Link>

                    <Link className="workspace-card catalog-home-card" to="/catalog">
                        <span className="card-icon card-icon-mint" aria-hidden="true">04</span>
                        <p className="card-kicker">EXPLORE</p>
                        <h2>기업·채용공고 탐색</h2>
                        <p>관심 있는 기업과 지금 모집 중인 공고를 찾아보세요.</p>
                        <span className="card-arrow" aria-hidden="true">↗</span>
                    </Link>
                </section>
            </section>
        </main>
    )
}
