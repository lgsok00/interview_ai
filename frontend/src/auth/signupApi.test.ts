import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {ApiError} from '../api/ApiError'
import {accessTokenStore} from './accessTokenStore'
import {signup, type SignupCredentials} from './authApi'

vi.mock('../config/env', () => ({env: {apiBaseUrl: 'https://interview.test'}}))

const credentials: SignupCredentials = {
    email: 'user@example.com',
    nickname: '테스트유저',
    password: 'password123',
}

const createdUser = {id: 17, email: credentials.email, nickname: credentials.nickname}

function jsonResponse(body: unknown, status: number): Response {
    return new Response(JSON.stringify(body), {
        status,
        headers: {'Content-Type': 'application/json'},
    })
}

describe('회원가입 API와 공통 HTTP client', () => {
    const fetchMock = vi.fn<typeof fetch>()

    beforeEach(() => {
        fetchMock.mockReset()
        vi.stubGlobal('fetch', fetchMock)
        accessTokenStore.clear()
    })

    afterEach(() => {
        accessTokenStore.clear()
        vi.unstubAllGlobals()
    })

    function sentBody(): Record<string, string> {
        const [, init] = fetchMock.mock.calls[0]!
        return JSON.parse(init!.body as string) as Record<string, string>
    }

    it('201 가입 응답을 반환하고 JSON·cookie를 포함한 공개 POST를 전송한다', async () => {
        fetchMock.mockResolvedValueOnce(jsonResponse(createdUser, 201))

        await expect(signup(credentials)).resolves.toEqual(createdUser)

        expect(fetchMock).toHaveBeenCalledTimes(1)
        const [url, init] = fetchMock.mock.calls[0]!
        expect(url).toBe('https://interview.test/api/auth/signup')
        expect(init).toMatchObject({method: 'POST', credentials: 'include'})
        expect(new Headers(init?.headers).get('Content-Type')).toBe('application/json')
        expect(new Headers(init?.headers).has('Authorization')).toBe(false)
        expect(sentBody()).toEqual(credentials)
        expect(accessTokenStore.get()).toBeNull()
    })

    it('이메일의 공백·대문자와 닉네임 공백을 정리하고 호출자의 입력은 보존한다', async () => {
        const input = Object.freeze({
            email: ' \tUSER@EXAMPLE.COM\n',
            nickname: '  테스트유저  ',
            password: 'password123',
        })
        fetchMock.mockResolvedValueOnce(jsonResponse(createdUser, 201))

        await signup(input)

        expect(sentBody()).toEqual(credentials)
        expect(input.email).toBe(' \tUSER@EXAMPLE.COM\n')
        expect(input.nickname).toBe('  테스트유저  ')
    })

    it('비밀번호의 앞뒤 공백과 대소문자를 그대로 전송한다', async () => {
        const password = '  Password123  '
        fetchMock.mockResolvedValueOnce(jsonResponse(createdUser, 201))

        await signup({...credentials, password})

        expect(sentBody().password).toBe(password)
    })

    it.each([
        {name: '닉네임 2자·비밀번호 8자', nickname: '가나', password: 'a'.repeat(8)},
        {name: '닉네임 50자·비밀번호 64자', nickname: '가'.repeat(50), password: 'a'.repeat(64)},
    ])('$name 경계값을 잘라내지 않고 전송한다', async ({nickname, password}) => {
        fetchMock.mockResolvedValueOnce(jsonResponse({...createdUser, nickname}, 201))

        await expect(signup({...credentials, nickname, password}))
            .resolves.toEqual({...createdUser, nickname})

        expect(sentBody()).toEqual({...credentials, nickname, password})
    })

    it('255자 이메일을 정규화 후 길이 손실 없이 전송한다', async () => {
        const email = `${'a'.repeat(64)}@${'b'.repeat(63)}.${'c'.repeat(63)}.${'d'.repeat(58)}.com`
        expect(email).toHaveLength(255)
        fetchMock.mockResolvedValueOnce(jsonResponse({...createdUser, email}, 201))

        await signup({...credentials, email: ` ${email.toUpperCase()} `})

        expect(sentBody().email).toBe(email)
    })

    it('추가 입력에 비밀번호 확인이 있어도 API 계약의 세 필드만 전송한다', async () => {
        const input = {...credentials, passwordConfirmation: credentials.password}
        fetchMock.mockResolvedValueOnce(jsonResponse(createdUser, 201))

        await signup(input)

        expect(sentBody()).toEqual(credentials)
        expect(sentBody()).not.toHaveProperty('passwordConfirmation')
    })

    it('가입 요청에는 기존 Access Token을 보내지 않고 로그인 상태도 변경하지 않는다', async () => {
        accessTokenStore.set('existing-access-token')
        fetchMock.mockResolvedValueOnce(jsonResponse(createdUser, 201))

        await signup(credentials)

        const [, init] = fetchMock.mock.calls[0]!
        expect(new Headers(init?.headers).has('Authorization')).toBe(false)
        expect(accessTokenStore.get()).toBe('existing-access-token')
        expect(fetchMock).toHaveBeenCalledTimes(1)
    })

    it('중복 이메일의 409 코드·메시지를 ApiError로 전달하고 재전송하지 않는다', async () => {
        fetchMock.mockResolvedValueOnce(jsonResponse({
            code: 'DUPLICATE_EMAIL',
            message: '이미 사용 중인 이메일입니다.',
            errors: {},
        }, 409))

        await expect(signup(credentials)).rejects.toMatchObject({
            name: 'ApiError',
            status: 409,
            code: 'DUPLICATE_EMAIL',
            message: '이미 사용 중인 이메일입니다.',
            errors: {},
        })
        expect(fetchMock).toHaveBeenCalledTimes(1)
    })

    it.each([
        {field: 'email', message: '올바른 이메일 형식이 아닙니다.'},
        {field: 'nickname', message: '닉네임은 2자 이상 50자 이하여야 합니다.'},
        {field: 'password', message: '비밀번호는 8자 이상 64자 이하여야 합니다.'},
    ])('서버의 $field validation 오류를 필드명과 함께 보존한다', async ({field, message}) => {
        const errors = {[field]: message}
        fetchMock.mockResolvedValueOnce(jsonResponse({
            code: 'VALIDATION_ERROR', message: '입력값을 확인해 주세요.', errors,
        }, 400))

        await expect(signup(credentials)).rejects.toMatchObject({
            status: 400, code: 'VALIDATION_ERROR', errors,
        })
        expect(fetchMock).toHaveBeenCalledTimes(1)
    })

    it('여러 필드의 오류를 하나도 누락하지 않고 전달한다', async () => {
        const errors = {
            email: '이메일을 확인해 주세요.',
            nickname: '닉네임을 확인해 주세요.',
            password: '비밀번호를 확인해 주세요.',
        }
        fetchMock.mockResolvedValueOnce(jsonResponse({
            code: 'VALIDATION_ERROR', message: '입력값을 확인해 주세요.', errors,
        }, 400))

        await expect(signup(credentials)).rejects.toMatchObject({errors})
    })

    it('공개 가입 요청의 401에는 토큰 재발급이나 자동 재시도를 하지 않는다', async () => {
        accessTokenStore.set('existing-access-token')
        fetchMock.mockResolvedValueOnce(jsonResponse({
            code: 'SIGNUP_UNAVAILABLE', message: '가입할 수 없습니다.', errors: {},
        }, 401))

        await expect(signup(credentials)).rejects.toMatchObject({status: 401})

        expect(fetchMock).toHaveBeenCalledTimes(1)
        expect(accessTokenStore.get()).toBe('existing-access-token')
    })

    it.each([429, 500, 503])('%i 오류를 전달하고 중복 가입 위험이 있는 자동 재시도를 하지 않는다', async (status) => {
        fetchMock.mockResolvedValueOnce(jsonResponse({
            code: 'SIGNUP_UNAVAILABLE', message: '잠시 후 다시 시도해 주세요.', errors: {},
        }, status))

        await expect(signup(credentials)).rejects.toMatchObject({
            status, code: 'SIGNUP_UNAVAILABLE', message: '잠시 후 다시 시도해 주세요.',
        })
        expect(fetchMock).toHaveBeenCalledTimes(1)
    })

    it('JSON이 아닌 서버 장애 응답도 ApiError로 전달한다', async () => {
        fetchMock.mockResolvedValueOnce(new Response('Service unavailable', {
            status: 503, headers: {'Content-Type': 'text/plain'},
        }))

        await expect(signup(credentials)).rejects.toMatchObject({
            name: 'ApiError', status: 503, code: null, message: 'Service unavailable',
        })
        expect(fetchMock).toHaveBeenCalledTimes(1)
    })

    it('201의 잘못된 JSON은 가입 성공으로 처리하지 않고 파싱 오류를 전달한다', async () => {
        fetchMock.mockResolvedValueOnce(new Response('{', {
            status: 201, headers: {'Content-Type': 'application/json'},
        }))

        await expect(signup(credentials)).rejects.toBeInstanceOf(ApiError)
        expect(fetchMock).toHaveBeenCalledTimes(1)
        expect(accessTokenStore.get()).toBeNull()
    })

    it('네트워크 실패를 그대로 전달하며 자동 가입 재전송이나 토큰 변경을 하지 않는다', async () => {
        const failure = new TypeError('Failed to fetch')
        fetchMock.mockRejectedValueOnce(failure)

        await expect(signup(credentials)).rejects.toBe(failure)

        expect(fetchMock).toHaveBeenCalledTimes(1)
        expect(accessTokenStore.get()).toBeNull()
    })
})
