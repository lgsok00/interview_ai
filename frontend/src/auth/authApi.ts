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

export interface SignupCredentials {
    email: string
    password: string
    nickname: string
}

export interface SignupResponse {
    id: number
    email: string
    nickname: string
}

export function signup(credentials: SignupCredentials): Promise<SignupResponse> {
    return apiRequest<SignupResponse>('/api/auth/signup', {
        method: 'POST',
        json: {
            email: credentials.email.trim().toLowerCase(),
            password: credentials.password,
            nickname: credentials.nickname.trim(),
        },
    })
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