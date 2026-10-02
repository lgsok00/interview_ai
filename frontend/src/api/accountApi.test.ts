import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {accessTokenStore} from '../auth/accessTokenStore'
import {changePassword, type CurrentUser, deleteAccount, getCurrentUser, updateNickname,} from './accountApi'

vi.mock('../config/env', () => ({env: {apiBaseUrl: 'https://interview.test'}}))

const user: CurrentUser = {
    id: 17,
    email: 'user@example.com',
    nickname: '테스트유저',
    provider: 'LOCAL',
    role: 'USER',
}

const passwords = {currentPassword: 'oldPassword123', newPassword: 'newPassword123'}

function jsonResponse(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), {
        status,
        headers: {'Content-Type': 'application/json'},
    })
}

describe('내 계정 API와 공통 HTTP client', () => {
    const fetchMock = vi.fn<typeof fetch>()

    beforeEach(() => {
        fetchMock.mockReset()
        vi.stubGlobal('fetch', fetchMock)
        accessTokenStore.set('access-token')
    })

    afterEach(() => {
        accessTokenStore.clear()
        vi.unstubAllGlobals()
    })

    function expectRequest(path: string, method: string, body?: unknown, index = 0) {
        const [url, init] = fetchMock.mock.calls[index]!
        expect(url).toBe(`https://interview.test${path}`)
        expect(init?.method ?? 'GET').toBe(method)
        expect(init?.credentials).toBe('include')
        expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer access-token')

        if (body === undefined) {
            expect(init?.body).toBeUndefined()
            expect(new Headers(init?.headers).has('Content-Type')).toBe(false)
        } else {
            expect(new Headers(init?.headers).get('Content-Type')).toBe('application/json')
            expect(JSON.parse(init?.body as string)).toEqual(body)
        }
    }

    it.each(['LOCAL', 'GOOGLE', 'GITHUB'] as const)(
        '%s 사용자 조회에 Bearer·cookie를 보내고 계정 정보를 반환한다',
        async (provider) => {
            const response = {...user, provider}
            fetchMock.mockResolvedValueOnce(jsonResponse(response))

            await expect(getCurrentUser()).resolves.toEqual(response)

            expectRequest('/api/users/me', 'GET')
            expect(fetchMock).toHaveBeenCalledTimes(1)
        },
    )

    it('관리자 역할과 서버 이메일을 변경 없이 반환한다', async () => {
        const response = {...user, role: 'ADMIN' as const}
        fetchMock.mockResolvedValueOnce(jsonResponse(response))

        await expect(getCurrentUser()).resolves.toEqual(response)
    })

    it('닉네임 공백을 정리해 PUT하며 이메일·역할·provider를 보내지 않는다', async () => {
        fetchMock.mockResolvedValueOnce(jsonResponse({...user, nickname: '새 닉네임'}))

        await expect(updateNickname(' \t새 닉네임\n '))
            .resolves.toEqual({...user, nickname: '새 닉네임'})

        expectRequest('/api/users/me', 'PUT', {nickname: '새 닉네임'})
        expect(fetchMock).toHaveBeenCalledTimes(1)
    })

    it.each([2, 50])('닉네임 %i자 경계값을 잘라내지 않고 전송한다', async (length) => {
        const nickname = '가'.repeat(length)
        fetchMock.mockResolvedValueOnce(jsonResponse({...user, nickname}))

        await expect(updateNickname(` ${nickname} `)).resolves.toEqual({...user, nickname})

        expectRequest('/api/users/me', 'PUT', {nickname})
    })

    it('닉네임 validation 오류의 field·메시지를 보존하고 인증을 유지한다', async () => {
        const body = {
            code: 'VALIDATION_ERROR',
            message: '입력값이 올바르지 않습니다.',
            errors: {nickname: '닉네임은 2자 이상 50자 이하여야 합니다.'},
        }
        fetchMock.mockResolvedValueOnce(jsonResponse(body, 400))

        await expect(updateNickname('가')).rejects.toMatchObject({status: 400, ...body})

        expect(fetchMock).toHaveBeenCalledTimes(1)
        expect(accessTokenStore.get()).toBe('access-token')
    })

    it('비밀번호 변경은 인증 PUT이며 204 빈 응답을 처리한다', async () => {
        fetchMock.mockResolvedValueOnce(new Response(null, {status: 204}))

        await expect(changePassword(passwords)).resolves.toBeUndefined()

        expectRequest('/api/users/me/password', 'PUT', passwords)
        expect(fetchMock).toHaveBeenCalledTimes(1)
    })

    it('현재·새 비밀번호의 공백과 대소문자를 보존하고 호출자 객체를 변경하지 않는다', async () => {
        const values = Object.freeze({
            currentPassword: '  OldPassword123  ',
            newPassword: '  NewPassword123  ',
        })
        fetchMock.mockResolvedValueOnce(new Response(null, {status: 204}))

        await changePassword(values)

        expectRequest('/api/users/me/password', 'PUT', values)
        expect(values).toEqual({
            currentPassword: '  OldPassword123  ',
            newPassword: '  NewPassword123  ',
        })
    })

    it.each([8, 64])('새 비밀번호 %i자 경계값을 손실 없이 전송한다', async (length) => {
        const values = {...passwords, newPassword: 'a'.repeat(length)}
        fetchMock.mockResolvedValueOnce(new Response(null, {status: 204}))

        await expect(changePassword(values)).resolves.toBeUndefined()

        expectRequest('/api/users/me/password', 'PUT', values)
    })

    it('잘못된 현재 비밀번호의 401은 refresh·재전송·토큰 삭제를 하지 않는다', async () => {
        const body = {
            code: 'INVALID_CURRENT_PASSWORD',
            message: '현재 비밀번호가 일치하지 않습니다.',
            errors: {},
        }
        fetchMock.mockResolvedValueOnce(jsonResponse(body, 401))

        await expect(changePassword(passwords)).rejects.toMatchObject({status: 401, ...body})

        expect(fetchMock).toHaveBeenCalledTimes(1)
        expectRequest('/api/users/me/password', 'PUT', passwords)
        expect(accessTokenStore.get()).toBe('access-token')
    })

    it.each([
        {code: 'SAME_PASSWORD', message: '현재 비밀번호와 다른 비밀번호를 입력해 주세요.'},
        {code: 'PASSWORD_CHANGE_NOT_SUPPORTED', message: '소셜 계정은 비밀번호를 변경할 수 없습니다.'},
        {code: 'VALIDATION_ERROR', message: '입력값이 올바르지 않습니다.'},
    ])('$code 오류를 재전송 없이 반환하고 인증을 유지한다', async ({code, message}) => {
        const errors = code === 'VALIDATION_ERROR' ? {newPassword: '8~64자로 입력해 주세요.'} : {}
        fetchMock.mockResolvedValueOnce(jsonResponse({code, message, errors}, 400))

        await expect(changePassword(passwords))
            .rejects.toMatchObject({status: 400, code, message, errors})

        expect(fetchMock).toHaveBeenCalledTimes(1)
        expect(accessTokenStore.get()).toBe('access-token')
    })

    it('탈퇴는 본문 없는 인증 DELETE이며 204 빈 응답을 처리한다', async () => {
        fetchMock.mockResolvedValueOnce(new Response(null, {status: 204}))

        await expect(deleteAccount()).resolves.toBeUndefined()

        expectRequest('/api/users/me', 'DELETE')
        expect(fetchMock).toHaveBeenCalledTimes(1)
    })

    it('마지막 관리자 탈퇴 거부의 409 코드·메시지를 보존하고 인증을 유지한다', async () => {
        const body = {
            code: 'LAST_ADMIN_DELETE_NOT_ALLOWED',
            message: '마지막 활성 관리자는 탈퇴할 수 없습니다.',
            errors: {},
        }
        fetchMock.mockResolvedValueOnce(jsonResponse(body, 409))

        await expect(deleteAccount()).rejects.toMatchObject({status: 409, ...body})

        expect(fetchMock).toHaveBeenCalledTimes(1)
        expect(accessTokenStore.get()).toBe('access-token')
    })

    it('조회 대상이 없으면 USER_NOT_FOUND를 반환하고 refresh하지 않는다', async () => {
        fetchMock.mockResolvedValueOnce(jsonResponse({
            code: 'USER_NOT_FOUND', message: '사용자를 찾을 수 없습니다.', errors: {},
        }, 404))

        await expect(getCurrentUser()).rejects.toMatchObject({status: 404, code: 'USER_NOT_FOUND'})

        expect(fetchMock).toHaveBeenCalledTimes(1)
    })

    it('만료 토큰의 비밀번호 변경은 refresh 후 같은 본문으로 한 번 재시도한다', async () => {
        fetchMock
            .mockResolvedValueOnce(jsonResponse({
                code: 'INVALID_ACCESS_TOKEN', message: '인증이 필요합니다.', errors: {},
            }, 401))
            .mockResolvedValueOnce(jsonResponse({accessToken: 'renewed-token'}))
            .mockResolvedValueOnce(new Response(null, {status: 204}))

        await expect(changePassword(passwords)).resolves.toBeUndefined()

        expect(fetchMock).toHaveBeenCalledTimes(3)
        expectRequest('/api/users/me/password', 'PUT', passwords)
        const [refreshUrl, refreshInit] = fetchMock.mock.calls[1]!
        expect(refreshUrl).toBe('https://interview.test/api/auth/refresh')
        expect(refreshInit).toMatchObject({method: 'POST', credentials: 'include'})
        expect(new Headers(refreshInit?.headers).has('Authorization')).toBe(false)
        const [retryUrl, retryInit] = fetchMock.mock.calls[2]!
        expect(retryUrl).toBe('https://interview.test/api/users/me/password')
        expect(retryInit?.method).toBe('PUT')
        expect(retryInit?.body).toBe(fetchMock.mock.calls[0]![1]?.body)
        expect(new Headers(retryInit?.headers).get('Authorization')).toBe('Bearer renewed-token')
        expect(accessTokenStore.get()).toBe('renewed-token')
    })

    it('재발급 후 현재 비밀번호가 틀려도 다시 refresh하거나 새 토큰을 삭제하지 않는다', async () => {
        fetchMock
            .mockResolvedValueOnce(new Response(null, {status: 401}))
            .mockResolvedValueOnce(jsonResponse({accessToken: 'renewed-token'}))
            .mockResolvedValueOnce(jsonResponse({
                code: 'INVALID_CURRENT_PASSWORD', message: '현재 비밀번호가 일치하지 않습니다.', errors: {},
            }, 401))

        await expect(changePassword(passwords))
            .rejects.toMatchObject({status: 401, code: 'INVALID_CURRENT_PASSWORD'})

        expect(fetchMock).toHaveBeenCalledTimes(3)
        expect(accessTokenStore.get()).toBe('renewed-token')
    })

    it('탈퇴 요청의 refresh 실패는 DELETE를 반복하지 않고 로컬 토큰을 삭제한다', async () => {
        fetchMock
            .mockResolvedValueOnce(new Response(null, {status: 401}))
            .mockResolvedValueOnce(jsonResponse({
                code: 'INVALID_REFRESH_TOKEN', message: '다시 로그인해 주세요.', errors: {},
            }, 401))

        await expect(deleteAccount()).rejects.toMatchObject({status: 401})

        expect(fetchMock).toHaveBeenCalledTimes(2)
        expectRequest('/api/users/me', 'DELETE')
        expect(accessTokenStore.get()).toBeNull()
    })

    it.each([
        {name: '조회', request: () => getCurrentUser()},
        {name: '닉네임 수정', request: () => updateNickname('새 닉네임')},
        {name: '비밀번호 변경', request: () => changePassword(passwords)},
        {name: '탈퇴', request: () => deleteAccount()},
    ])('$name 네트워크 실패는 자동 반복하지 않고 오류와 인증을 유지한다', async ({request}) => {
        const error = new TypeError('Failed to fetch')
        fetchMock.mockRejectedValueOnce(error)

        await expect(request()).rejects.toBe(error)

        expect(fetchMock).toHaveBeenCalledTimes(1)
        expect(accessTokenStore.get()).toBe('access-token')
    })
})
