import {useAuth} from "../auth/useAuth.ts";
import {Link, useNavigate} from "react-router-dom";
import {type SubmitEvent, useEffect, useRef, useState} from 'react'
import {changePassword, type CurrentUser, deleteAccount, getCurrentUser, updateNickname} from "../api/accountApi";
import {ApiError} from "../api/ApiError";
import {logout as logoutRequest} from "../auth/authApi";

const providerLabels = {
    LOCAL: '이메일',
    GOOGLE: 'Google',
    GITHUB: 'GitHub',
}

const passwordFields = [
    {
        name: 'currentPassword',
        label: '현재 비밀번호',
        autoComplete: 'current-password',
    },
    {
        name: 'newPassword',
        label: '새 비밀번호',
        autoComplete: 'new-password',
    },
    {
        name: 'confirmation',
        label: '새 비밀번호 확인',
        autoComplete: 'new-password',
    },
] as const

interface PasswordValues {
    currentPassword: string
    newPassword: string
    confirmation: string
}

type FieldErrors = Partial<Record<'nickname' | keyof PasswordValues, string>>

export function AccountPage() {
    const {clearSession} = useAuth()
    const navigate = useNavigate()
    const requestLocked = useRef(false)
    const [user, setUser] = useState<CurrentUser | null>(null)
    const [nickname, setNickname] = useState('')
    const [passwords, setPasswords] = useState<PasswordValues>({
        currentPassword: '',
        newPassword: '',
        confirmation: '',
    })
    const [loading, setLoading] = useState(true)
    const [loadVersion, setLoadVersion] = useState(0)
    const [busy, setBusy] = useState(false)
    const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
    const [errorMessage, setErrorMessage] = useState('')
    const [successMessage, setSuccessMessage] = useState('')
    const [deleteConfirmed, setDeleteConfirmed] = useState(false)

    useEffect(() => {
        let active = true

        void getCurrentUser()
            .then((response) => {
                if (!active) return

                setUser(response)
                setNickname(response.nickname)
            })
            .catch((error: unknown) => {
                if (!active) return

                if (error instanceof ApiError && (error.status === 401 || error.code === 'USER_NOT_FOUND')) {
                    navigate('/login', {
                        replace: true,
                        state: {
                            accountMessage: '로그인 상태를 확인할 수 없습니다. 다시 로그인해 주세요.',
                        },
                    })

                    clearSession()
                    return
                }

                setErrorMessage(
                    error instanceof ApiError
                        ? error.message
                        : '계정 정보를 불러오지 못했습니다. 다시 시도해 주세요.',
                )
            })
            .finally(() => {
                if (active) setLoading(false)
            })

        return () => {
            active = false
        }
    }, [loadVersion, navigate, clearSession])

    function showFailure(error: unknown, fallback: string) {
        if (error instanceof ApiError) {
            if ((error.status === 401 && error.code !== 'INVALID_CURRENT_PASSWORD') || error.code === 'USER_NOT_FOUND') {
                navigate('/login', {
                    replace: true,
                    state: {
                        accountMessage: '로그인 상태를 확인할 수 없습니다. 다시 로그인해 주세요.',
                    },
                })

                clearSession()
                return
            }

            const errors: FieldErrors = {}

            for (const name of [
                'nickname',
                'currentPassword',
                'newPassword',
            ] as const) {
                if (typeof error.errors[name] === 'string') {
                    errors[name] = error.errors[name]
                }
            }

            if (error.code === 'INVALID_CURRENT_PASSWORD') {
                errors.currentPassword = error.message
            }

            if (error.code === 'SAME_PASSWORD') {
                errors.newPassword = error.message
            }

            setFieldErrors(errors)
            setErrorMessage(error.message)

            return
        }

        setErrorMessage(fallback)
    }

    function startRequest() {
        if (requestLocked.current) return false

        requestLocked.current = true
        setBusy(true)
        setFieldErrors({})
        setErrorMessage('')
        setSuccessMessage('')

        return true
    }

    function endRequest() {
        requestLocked.current = false
        setBusy(false)
    }

    async function finishSession(message: string) {
        let notice = message

        try {
            await logoutRequest()

        } catch {
            notice += ' 서버 로그아웃 정리는 확인하지 못했습니다. '
                + '이 브라우저의 인증 정보는 삭제했습니다.'
        }

        navigate('/login', {
            replace: true,
            state: {accountMessage: notice},
        })

        clearSession()
    }

    async function handleNickname(event: SubmitEvent<HTMLFormElement>) {
        event.preventDefault()
        if (!user || !startRequest()) return

        try {
            const value = nickname.trim()

            if (value.length < 2 || value.length > 50) {
                setFieldErrors({nickname: '닉네임은 앞뒤 공백을 제외하고 2~50자여야 합니다.'})
                setErrorMessage('닉네임을 확인해 주세요.')
                document.getElementById('account-nickname')?.focus()

                return
            }

            const response = await updateNickname(value)

            setUser(response)
            setNickname(response.nickname)
            setSuccessMessage('닉네임을 저장했습니다.')

        } catch (error) {
            showFailure(
                error,
                '저장 결과를 확인하지 못했습니다. '
                + '페이지를 다시 열어 닉네임을 확인해 주세요.',
            )

        } finally {
            endRequest()
        }
    }

    async function handlePassword(event: SubmitEvent<HTMLFormElement>) {
        event.preventDefault()
        if (user?.provider !== 'LOCAL' || !startRequest()) return

        try {
            const errors: FieldErrors = {}

            if (!passwords.currentPassword.trim()) {
                errors.currentPassword = '현재 비밀번호를 입력해 주세요.'
            }

            if (!passwords.newPassword.trim() || passwords.newPassword.length < 8 || passwords.newPassword.length > 64) {
                errors.newPassword = '새 비밀번호는 8~64자여야 합니다.'

            } else if (passwords.newPassword === passwords.currentPassword) {
                errors.newPassword = '현재 비밀번호와 다른 비밀번호를 입력해 주세요.'
            }

            if (!passwords.confirmation || passwords.confirmation !== passwords.newPassword) {
                errors.confirmation = '새 비밀번호와 일치하지 않습니다.'
            }

            if (Object.keys(errors).length > 0) {
                setFieldErrors(errors)
                setErrorMessage('비밀번호 입력을 확인해 주세요.')

                const first = passwordFields.find((field) => errors[field.name])

                if (first) {
                    document.getElementById(`account-${first.name}`)?.focus()
                }

                return
            }

            await changePassword({
                currentPassword: passwords.currentPassword,
                newPassword: passwords.newPassword,
            })

            setPasswords({
                currentPassword: '',
                newPassword: '',
                confirmation: '',
            })

            await finishSession('비밀번호를 변경했습니다. 새 비밀번호로 다시 로그인해 주세요.')

        } catch (error) {
            showFailure(
                error,
                '변경 결과를 확인하지 못했습니다. '
                + '로그아웃 후 새 비밀번호로 로그인을 확인해 주세요.',
            )

        } finally {
            endRequest()
        }
    }

    async function handleDelete(event: SubmitEvent<HTMLFormElement>) {
        event.preventDefault()
        if (!user || !deleteConfirmed || !startRequest()) return

        try {
            await deleteAccount()
            await finishSession('회원 탈퇴가 완료되었습니다.')

        } catch (error) {
            showFailure(
                error,
                '탈퇴 결과를 확인하지 못했습니다. '
                + '계정 상태를 다시 확인해 주세요.',
            )

        } finally {
            endRequest()
        }
    }

    return (
        <main className="workspace">
            <header className="workspace-header">
                <Link className="brand" to="/">
                    <span className="brand-mark">i</span>
                    <span>interview<span className="brand-accent">AI</span></span>
                </Link>
                <Link className="secondary-button" to="/">홈으로</Link>
            </header>

            <section className="workspace-content account-content">
                <p className="eyebrow">MY ACCOUNT</p>
                <h1>내 계정</h1>
                <p className="workspace-intro">
                    계정 정보를 확인하고 닉네임과 로그인 정보를 관리하세요.
                </p>

                {errorMessage && (
                    <p className="form-alert" role="alert">{errorMessage}</p>
                )}
                {successMessage && (
                    <p className="account-notice" role="status">
                        {successMessage}
                    </p>
                )}

                {loading ? (
                    <p role="status">계정 정보를 불러오는 중입니다.</p>
                ) : !user ? (
                    <button
                        className="secondary-button"
                        type="button"
                        onClick={() => {
                            setErrorMessage('')
                            setLoading(true)
                            setLoadVersion((value) => value + 1)
                        }}
                    >
                        다시 불러오기
                    </button>
                ) : (
                    <div className="account-sections" aria-busy={busy}>
                        <section className="account-section">
                            <h2>기본 정보</h2>
                            <dl className="account-details">
                                <div>
                                    <dt>이메일</dt>
                                    <dd>{user.email}</dd>
                                </div>
                                <div>
                                    <dt>로그인 방식</dt>
                                    <dd>{providerLabels[user.provider]}</dd>
                                </div>
                                <div>
                                    <dt>계정 권한</dt>
                                    <dd>{user.role === 'ADMIN' ? '관리자' : '일반 사용자'}</dd>
                                </div>
                            </dl>
                            <p className="account-help">
                                이메일 변경과 로그인 계정 연결은 현재 지원하지 않습니다.
                            </p>

                            <form onSubmit={handleNickname} noValidate>
                                <fieldset disabled={busy} className="account-fields">
                                    <legend className="signup-legend">닉네임 수정</legend>
                                    <label className="form-field">
                                        <span>닉네임</span>
                                        <input
                                            id="account-nickname"
                                            name="nickname"
                                            autoComplete="nickname"
                                            value={nickname}
                                            onChange={(event) => setNickname(event.target.value)}
                                            required
                                            aria-invalid={Boolean(fieldErrors.nickname)}
                                            aria-describedby={
                                                fieldErrors.nickname
                                                    ? 'account-nickname-error'
                                                    : undefined
                                            }
                                        />
                                        {fieldErrors.nickname && (
                                            <span
                                                id="account-nickname-error"
                                                className="signup-field-error"
                                            >
                                                {fieldErrors.nickname}
                                            </span>
                                        )}
                                    </label>
                                    <button
                                        className="primary-button"
                                        type="submit"
                                        disabled={busy || nickname.trim() === user.nickname}
                                    >
                                        닉네임 저장
                                    </button>
                                </fieldset>
                            </form>
                        </section>

                        <section className="account-section">
                            <h2>비밀번호</h2>
                            {user.provider === 'LOCAL' ? (
                                <>
                                    <p className="account-help">
                                        변경하면 모든 기기의 자동 로그인 정보가 폐기되며,
                                        현재 기기에서도 다시 로그인해야 합니다.
                                        다른 기기의 기존 로그인은 Access Token 만료 전까지
                                        유지될 수 있습니다.
                                    </p>
                                    <p className="account-help">
                                        비밀번호 찾기는 현재 지원하지 않습니다.
                                        비밀번호를 안전하게 보관해 주세요.
                                    </p>
                                    <form onSubmit={handlePassword} noValidate>
                                        <fieldset
                                            disabled={busy}
                                            className="account-fields"
                                        >
                                            <legend className="signup-legend">
                                                비밀번호 변경
                                            </legend>
                                            {passwordFields.map((field) => (
                                                <label
                                                    className="form-field"
                                                    key={field.name}
                                                >
                                                    <span>{field.label}</span>
                                                    <input
                                                        id={`account-${field.name}`}
                                                        name={field.name}
                                                        type="password"
                                                        autoComplete={field.autoComplete}
                                                        value={passwords[field.name]}
                                                        required
                                                        onChange={(event) => {
                                                            const value = event.target.value
                                                            setPasswords((previous) => ({
                                                                ...previous,
                                                                [field.name]: value,
                                                            }))
                                                        }}
                                                        aria-invalid={
                                                            Boolean(fieldErrors[field.name])
                                                        }
                                                        aria-describedby={
                                                            fieldErrors[field.name]
                                                                ? `account-${field.name}-error`
                                                                : undefined
                                                        }
                                                    />
                                                    {fieldErrors[field.name] && (
                                                        <span
                                                            id={`account-${field.name}-error`}
                                                            className="signup-field-error"
                                                        >
                                                            {fieldErrors[field.name]}
                                                        </span>
                                                    )}
                                                </label>
                                            ))}
                                            <button
                                                className="primary-button"
                                                type="submit"
                                                disabled={busy}
                                            >
                                                비밀번호 변경
                                            </button>
                                        </fieldset>
                                    </form>
                                </>
                            ) : (
                                <p className="account-help">
                                    {providerLabels[user.provider]} 계정으로 로그인하고 있습니다.
                                    비밀번호는 해당 서비스에서 관리해 주세요.
                                </p>
                            )}
                        </section>

                        <section className="account-section account-danger">
                            <h2>회원 탈퇴</h2>
                            <p className="account-help">
                                탈퇴하면 자기소개서·이력서·면접 기록 등 개인 데이터가
                                삭제되며 복구할 수 없습니다.
                                소셜 로그인 서비스의 앱 연결은 자동 해제되지 않습니다.
                            </p>
                            <form onSubmit={handleDelete}>
                                <fieldset disabled={busy} className="account-fields">
                                    <legend className="signup-legend">회원 탈퇴 확인</legend>
                                    <label className="account-confirmation">
                                        <input
                                            type="checkbox"
                                            checked={deleteConfirmed}
                                            onChange={(event) => {
                                                setDeleteConfirmed(event.target.checked)
                                            }}
                                        />
                                        <span>개인 데이터 삭제를 확인했으며 탈퇴에 동의합니다.</span>
                                    </label>
                                    <button
                                        className="secondary-button account-delete-button"
                                        type="submit"
                                        disabled={busy || !deleteConfirmed}
                                    >
                                        회원 탈퇴
                                    </button>
                                </fieldset>
                            </form>
                        </section>
                    </div>
                )}
            </section>
        </main>
    )
}