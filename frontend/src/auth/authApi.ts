import {apiRequest} from "../api/client";

export interface LoginResponse {
    accessToken: string
    tokenType: 'Bearer'
    expiresIn: number
}

export interface LoginCredentials {
    email: string
    password: string
}

export function login(credentials: LoginCredentials): Promise<LoginResponse> {
    return apiRequest<LoginResponse>('/api/auth/login', {
        method: 'POST',
        json: credentials,
    })
}

export function refreshAccessToken(): Promise<LoginResponse> {
    return apiRequest<LoginResponse>('/api/auth/refresh', {
        method: 'POST',
    })
}

export function logout(): Promise<void> {
    return apiRequest<void>('/api/auth/logout', {
        method: 'POST',
    })
}