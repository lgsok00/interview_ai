import {afterEach, describe, expect, it, vi} from 'vitest'
import {accessTokenStore} from '../auth/accessTokenStore'
import {ApiError} from './ApiError'
import {apiRequest} from './client'

describe('apiRequest', () => {
    afterEach(() => {
        accessTokenStore.clear()
        vi.unstubAllGlobals()
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
