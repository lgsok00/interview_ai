import {accessTokenStore} from '../auth/accessTokenStore'
import {ApiError, type ApiErrorResponse} from './ApiError'
import {env} from "../config/env";

export interface ApiRequestOptions extends Omit<RequestInit, 'body'> {
    authenticated?: boolean
    body?: BodyInit | null
    json?: unknown
}

function createHeaders(options: ApiRequestOptions): Headers {
    const headers = new Headers(options.headers)

    if (options.json !== undefined && !headers.has('Content-Type')) {
        headers.set('Content-Type', 'application/json')
    }

    if (options.authenticated) {
        const accessToken = accessTokenStore.get()

        if (accessToken) {
            headers.set('Authorization', `Bearer ${accessToken}`)
        }
    }

    return headers
}

function createBody(options: ApiRequestOptions): BodyInit | null | undefined {
    if (options.body !== undefined && options.json !== undefined) {
        throw new Error('Specify either body or json, not both.')
    }

    if (options.json !== undefined) {
        return JSON.stringify(options.json)
    }

    return options.body
}

async function readResponseBody(response: Response): Promise<unknown> {
    const text = await response.text()

    if (!text) {
        return undefined
    }

    const contentType = response.headers.get('Content-Type')

    if (contentType?.includes('application/json')) {
        try {
            return JSON.parse(text)

        } catch {
            throw new ApiError(response.status, '서버가 올바르지 않은 JSON 응답을 반환했습니다.',)
        }
    }

    return text
}

function isApiErrorResponse(body: unknown): body is ApiErrorResponse {
    if (typeof body !== 'object' || body === null) {
        return false
    }

    const candidate = body as Partial<ApiErrorResponse>

    return (
        typeof candidate.code === 'string'
        && typeof candidate.message === 'string'
        && typeof candidate.errors === 'object'
        && candidate.errors !== null
    )
}

function toApiError(response: Response, body: unknown): ApiError {
    if (isApiErrorResponse(body)) {
        return new ApiError(response.status, body.message, body.code, body.errors,)
    }

    const message = typeof body === 'string' && body.trim()
        ? body
        : `API 요청에 실패했습니다. (${response.status})`

    return new ApiError(response.status, message)
}

export async function apiRequest<T>(
    path: string,
    options: ApiRequestOptions = {},
): Promise<T> {
    const {
        authenticated: _authenticated,
        body: _body,
        json: _json,
        ...requestInit
    } = options

    const response = await fetch(`${env.apiBaseUrl}${path}`, {
        ...requestInit,
        headers: createHeaders(options),
        body: createBody(options),
        credentials: 'include',
    })

    const responseBody = await readResponseBody(response)

    if (!response.ok) {
        throw toApiError(response, responseBody)
    }

    return responseBody as T
}