import {apiRequest} from "./client.ts";

export interface CurrentUser {
    id: number
    email: string
    nickname: string
    provider: 'LOCAL' | 'GOOGLE' | 'GITHUB'
    role: 'USER' | 'ADMIN'
}

export interface PasswordChange {
    currentPassword: string
    newPassword: string
}

export function getCurrentUser(): Promise<CurrentUser> {
    return apiRequest<CurrentUser>('/api/users/me', {
        authenticated: true
    })
}

export function updateNickname(nickname: string): Promise<CurrentUser> {
    return apiRequest<CurrentUser>('/api/users/me', {
        method: 'PUT',
        authenticated: true,
        json: {nickname: nickname.trim()},
    })
}

export function changePassword(values: PasswordChange): Promise<void> {
    return apiRequest<void>('/api/users/me/password', {
        method: 'PUT',
        authenticated: true,
        json: values,
    })
}

export function deleteAccount(): Promise<void> {
    return apiRequest<void>('/api/users/me', {
        method: 'DELETE',
        authenticated: true,
    })
}