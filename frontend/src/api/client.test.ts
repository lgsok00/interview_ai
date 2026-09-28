import {afterEach, describe, expect, it, vi} from 'vitest'
import {accessTokenStore} from '../auth/accessTokenStore'
import {ApiError} from './ApiError'
import {apiRequest} from './client'

describe('apiRequest', () => {
    afterEach(() => {
        accessTokenStore.clear()
        vi.unstubAllGlobals()
    })

    it('인증 요청이 401이면 refresh cookie로 재발급하고 원 요청을 한 번 재시도한다', async () => {
        accessTokenStore.set('expired-access-token')
        const fetchMock = vi.fn()
            .mockResolvedValueOnce(new Response(null, {status: 401}))
            .mockResolvedValueOnce(new Response(JSON.stringify({
                accessToken: 'renewed-access-token',
                tokenType: 'Bearer',
                expiresIn: 900,
            }), {
                status: 200,
                headers: {'Content-Type': 'application/json'},
            }))
            .mockResolvedValueOnce(new Response(JSON.stringify({result: 'ok'}), {
                status: 200,
                headers: {'Content-Type': 'application/json'},
            }))
        vi.stubGlobal('fetch', fetchMock)

        await expect(apiRequest<{result: string}>('/api/protected', {
            authenticated: true,
        })).resolves.toEqual({result: 'ok'})

        expect(fetchMock).toHaveBeenCalledTimes(3)
        const [initialUrl, initialInit] = fetchMock.mock.calls[0] as [string, RequestInit]
        const [refreshUrl, refreshInit] = fetchMock.mock.calls[1] as [string, RequestInit]
        const [retryUrl, retryInit] = fetchMock.mock.calls[2] as [string, RequestInit]

        expect(initialUrl).toMatch(/\/api\/protected$/)
        expect(new Headers(initialInit.headers).get('Authorization'))
            .toBe('Bearer expired-access-token')
        expect(refreshUrl).toMatch(/\/api\/auth\/refresh$/)
        expect(refreshInit.method).toBe('POST')
        expect(refreshInit.credentials).toBe('include')
        expect(retryUrl).toMatch(/\/api\/protected$/)
        expect(new Headers(retryInit.headers).get('Authorization'))
            .toBe('Bearer renewed-access-token')
        expect(accessTokenStore.get()).toBe('renewed-access-token')
    })

    it('재발급에 실패하면 원 요청을 반복하지 않고 401을 반환한다', async () => {
        accessTokenStore.set('expired-access-token')
        const fetchMock = vi.fn()
            .mockResolvedValueOnce(new Response(null, {status: 401}))
            .mockResolvedValueOnce(new Response(JSON.stringify({
                code: 'INVALID_REFRESH_TOKEN',
                message: 'Refresh Token이 유효하지 않습니다.',
                errors: {},
            }), {
                status: 401,
                headers: {'Content-Type': 'application/json'},
            }))
        vi.stubGlobal('fetch', fetchMock)

        await expect(apiRequest('/api/protected', {
            authenticated: true,
        })).rejects.toMatchObject({status: 401})

        expect(fetchMock).toHaveBeenCalledTimes(2)
        expect(accessTokenStore.get()).toBeNull()
    })

    it('재시도한 보호 요청도 401이면 추가 재발급을 시도하지 않는다', async () => {
        accessTokenStore.set('expired-access-token')
        const fetchMock = vi.fn()
            .mockResolvedValueOnce(new Response(null, {status: 401}))
            .mockResolvedValueOnce(new Response(JSON.stringify({
                accessToken: 'renewed-access-token',
                tokenType: 'Bearer',
                expiresIn: 900,
            }), {
                status: 200,
                headers: {'Content-Type': 'application/json'},
            }))
            .mockResolvedValueOnce(new Response(null, {status: 401}))
            .mockResolvedValueOnce(new Response(JSON.stringify({
                code: 'INVALID_REFRESH_TOKEN',
                message: 'Refresh Token이 유효하지 않습니다.',
                errors: {},
            }), {
                status: 401,
                headers: {'Content-Type': 'application/json'},
            }))
        vi.stubGlobal('fetch', fetchMock)

        await expect(apiRequest('/api/protected', {
            authenticated: true,
        })).rejects.toMatchObject({status: 401})

        expect(fetchMock).toHaveBeenCalledTimes(3)
    })

    it('인증 JSON 요청에 cookie, Bearer Token과 JSON 본문을 적용한다', async () => {
        accessTokenStore.set('access-token')
        const fetchMock = vi.fn().mockResolvedValue(
            new Response(JSON.stringify({id: 1}), {
                status: 200,
                headers: {'Content-Type': 'application/json'},
            }),
        )
        vi.stubGlobal('fetch', fetchMock)

        await expect(apiRequest<{id: number}>('/api/example', {
            method: 'POST',
            authenticated: true,
            json: {name: '테스트'},
        })).resolves.toEqual({id: 1})

        expect(fetchMock).toHaveBeenCalledOnce()
        const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
        const headers = new Headers(init.headers)

        expect(url).toMatch(/\/api\/example$/)
        expect(init.credentials).toBe('include')
        expect(init.body).toBe(JSON.stringify({name: '테스트'}))
        expect(headers.get('Authorization')).toBe('Bearer access-token')
        expect(headers.get('Content-Type')).toBe('application/json')
    })

    it('공개 요청에는 Authorization 헤더를 추가하지 않는다', async () => {
        accessTokenStore.set('access-token')
        const fetchMock = vi.fn().mockResolvedValue(
            new Response(null, {status: 204}),
        )
        vi.stubGlobal('fetch', fetchMock)

        await apiRequest<void>('/actuator/health')

        const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
        expect(new Headers(init.headers).has('Authorization')).toBe(false)
        expect(init.credentials).toBe('include')
    })

    it('백엔드 오류 응답을 ApiError로 변환한다', async () => {
        const fetchMock = vi.fn().mockResolvedValue(
            new Response(JSON.stringify({
                code: 'VALIDATION_ERROR',
                message: '입력값이 올바르지 않습니다.',
                errors: {email: '올바른 이메일 형식이 아닙니다.'},
            }), {
                status: 400,
                headers: {'Content-Type': 'application/json'},
            }),
        )
        vi.stubGlobal('fetch', fetchMock)

        const request = apiRequest('/api/example')

        await expect(request).rejects.toMatchObject<ApiError>({
            name: 'ApiError',
            status: 400,
            code: 'VALIDATION_ERROR',
            message: '입력값이 올바르지 않습니다.',
            errors: {email: '올바른 이메일 형식이 아닙니다.'},
        })
    })

    it('body와 json을 동시에 지정하면 요청을 보내지 않고 거부한다', async () => {
        const fetchMock = vi.fn()
        vi.stubGlobal('fetch', fetchMock)

        await expect(apiRequest('/api/example', {
            body: 'raw-body',
            json: {name: '테스트'},
        })).rejects.toThrow('Specify either body or json, not both.')
        expect(fetchMock).not.toHaveBeenCalled()
    })
})
