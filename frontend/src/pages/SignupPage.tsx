import {useAuth} from "../auth/useAuth";
import {type SubmitEvent, useRef, useState} from 'react'
import {Link, Navigate} from "react-router-dom";
import {signup} from "../auth/authApi";
import {ApiError} from "../api/ApiError";

interface SignupValues {
    email: string
    nickname: string
    password: string
    passwordConfirmation: string
}

type FieldErrors = Partial<Record<keyof SignupValues, string>>

const fields = [
    {
        name: 'email',
        label: '이메일',
        type: 'email',
        autoComplete: 'email',
        placeholder: 'name@example.com',
        maxLength: 255,
    },
    {
        name: 'nickname',
        label: '닉네임',
        type: 'text',
        autoComplete: 'nickname',
        placeholder: '2~50자로 입력하세요',
        maxLength: 50,
    },
    {
        name: 'password',
        label: '비밀번호',
        type: 'password',
        autoComplete: 'new-password',
        placeholder: '8~64자로 입력하세요',
        maxLength: 64,
    },
    {
        name: 'passwordConfirmation',
        label: '비밀번호 확인',
        type: 'password',
        autoComplete: 'new-password',
        placeholder: '비밀번호를 다시 입력하세요',
        maxLength: 64,
    },
] as const

export function SignupPage() {
    const {isAuthenticated, isInitializing} = useAuth()
    const submitting = useRef(false)
    const [values, setValues] = useState<SignupValues>({
        email: '',
        nickname: '',
        password: '',
        passwordConfirmation: '',
    })
    const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
    const [errorMessage, setErrorMessage] = useState('')
    const [isSubmitting, setIsSubmitting] = useState(false)
    const [isCompleted, setIsCompleted] = useState(false)

    if (isInitializing) {
        return <p role="status">로그인 상태를 확인하고 있습니다.</p>
    }

    if (isAuthenticated) {
        return <Navigate to="/" replace/>
    }

    async function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
        event.preventDefault()

        if (submitting.current || isCompleted) {
            return
        }

        const email = values.email.trim().toLowerCase()
        const nickname = values.nickname.trim()
        const errors: FieldErrors = {}

        if (!email) {
            errors.email = '이메일을 입력해 주세요.'

        } else if (email.length > 255) {
            errors.email = '이메일은 255자 이하여야 합니다.'

        } else if (!/^[^\s@]+@[^\s@]+$/.test(email)) {
            errors.email = '올바른 이메일 형식을 입력해 주세요.'
        }

        if (nickname.length < 2 || nickname.length > 50) {
            errors.nickname = '닉네임은 앞뒤 공백을 제외하고 2~50자여야 합니다.'
        }

        if (!values.password.trim()) {
            errors.password = '비밀번호를 입력해 주세요.'

        } else if (values.password.length < 8 || values.password.length > 64) {
            errors.password = '비밀번호는 8~64자여야 합니다.'
        }

        if (!values.passwordConfirmation) {
            errors.passwordConfirmation = '비밀번호를 다시 입력해 주세요.'

        } else if (values.password !== values.passwordConfirmation) {
            errors.passwordConfirmation = '비밀번호가 일치하지 않습니다.'
        }

        setFieldErrors(errors)
        setErrorMessage('')

        if (Object.keys(errors).length > 0) {
            const firstInvalid = fields.find((field) => errors[field.name])

            if (firstInvalid) {
                document.getElementById(`signup-${firstInvalid.name}`)?.focus()
            }

            return
        }

        submitting.current = true
        setIsSubmitting(true)

        try {
            await signup({
                email,
                nickname,
                password: values.password,
            })

            setValues({
                email: '',
                nickname: '',
                password: '',
                passwordConfirmation: '',
            })

            setIsCompleted(true)

        } catch (error) {
            if (error instanceof ApiError) {
                const serverErrors: FieldErrors = {}

                for (const field of fields) {
                    const message = error.errors[field.name]

                    if (typeof message === 'string') {
                        serverErrors[field.name] = message
                    }
                }

                if (error.code === 'DUPLICATE_EMAIL') {
                    serverErrors.email = '이미 가입된 이메일입니다. 로그인해 주세요.'
                }

                setFieldErrors(serverErrors)
                setErrorMessage(error.message)

            } else {
                setErrorMessage(
                    '가입 결과를 확인하지 못했습니다. 이미 가입되었을 수 있으니 '
                    + '로그인을 먼저 확인하고, 가입되지 않았다면 다시 시도해 주세요.',
                )
            }

        } finally {
            submitting.current = false
            setIsSubmitting(false)
        }
    }

    return (
        <main className="auth-shell">
            <section className="auth-visual">
                <Link className="brand brand-light" to="/login">
                    <span className="brand-mark">i</span>
                    <span>interview<span className="brand-accent">AI</span></span>
                </Link>

                <div className="visual-copy">
                    <p className="eyebrow">START YOUR NEXT STEP</p>
                    <h2>
                        나의 경험을,
                        <br/>
                        다음 기회로.
                    </h2>
                    <p className="visual-description">
                        자기소개서와 이력서를 정리하고,
                        <br/>
                        나에게 맞는 면접 연습을 시작하세요.
                    </p>
                </div>

                <p className="visual-footer">당신의 다음 기회를 준비하는 공간</p>
            </section>

            <section className="auth-panel">
                <div className="auth-card">
                    <Link className="brand brand-mobile" to="/login">
                        <span className="brand-mark">i</span>
                        <span>interview<span className="brand-accent">AI</span></span>
                    </Link>

                    {isCompleted ? (
                        <>
                            <p className="eyebrow">WELCOME</p>
                            <h1>가입이 완료되었습니다</h1>
                            <p className="auth-subtitle" role="status">
                                가입한 이메일과 비밀번호로 로그인해 주세요.
                            </p>
                            <Link className="primary-button auth-action-link" to="/login">
                                로그인하러 가기
                                <span aria-hidden="true">→</span>
                            </Link>
                        </>
                    ) : (
                        <>
                            <p className="eyebrow">CREATE YOUR ACCOUNT</p>
                            <h1>면접 준비를 시작하세요</h1>
                            <p className="auth-subtitle">
                                이메일로 새 계정을 만들 수 있습니다.
                            </p>

                            <form
                                className="auth-form"
                                onSubmit={handleSubmit}
                                noValidate
                                aria-busy={isSubmitting}
                            >
                                <fieldset className="signup-fields" disabled={isSubmitting}>
                                    <legend className="signup-legend">회원가입 정보</legend>

                                    {fields.map((field) => (
                                        <label className="form-field" key={field.name}>
                                            <span>{field.label}</span>
                                            <input
                                                id={`signup-${field.name}`}
                                                name={field.name}
                                                type={field.type}
                                                autoComplete={field.autoComplete}
                                                placeholder={field.placeholder}
                                                maxLength={field.maxLength}
                                                value={values[field.name]}
                                                onChange={(event) => {
                                                    const value = event.target.value

                                                    setValues((previous) => ({
                                                        ...previous,
                                                        [field.name]: value,
                                                    }))
                                                }}
                                                required
                                                aria-invalid={Boolean(fieldErrors[field.name])}
                                                aria-describedby={
                                                    fieldErrors[field.name]
                                                        ? `signup-${field.name}-error`
                                                        : undefined
                                                }
                                            />
                                            {fieldErrors[field.name] && (
                                                <span
                                                    id={`signup-${field.name}-error`}
                                                    className="signup-field-error"
                                                >
                                                    {fieldErrors[field.name]}
                                                </span>
                                            )}
                                        </label>
                                    ))}

                                    {errorMessage && (
                                        <p className="form-alert" role="alert">
                                            {errorMessage}
                                        </p>
                                    )}

                                    {Object.keys(fieldErrors).length > 0 && !errorMessage && (
                                        <p className="form-alert" role="alert">
                                            입력 항목을 확인해 주세요.
                                        </p>
                                    )}

                                    <button
                                        className="primary-button"
                                        type="submit"
                                        disabled={isSubmitting}
                                    >
                                        {isSubmitting ? '가입 중…' : '회원가입'}
                                        <span aria-hidden="true">→</span>
                                    </button>
                                </fieldset>
                            </form>

                            <p className="auth-account-link">
                                이미 계정이 있나요? <Link to="/login">로그인</Link>
                            </p>
                        </>
                    )}
                </div>
            </section>
        </main>
    )
}