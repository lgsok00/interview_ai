import { afterEach, describe, expect, it, vi } from 'vitest'

const { apiRequestMock } = vi.hoisted(() => ({
  apiRequestMock: vi.fn(),
}))

vi.mock('../api/client', () => ({
  apiRequest: apiRequestMock,
}))

import { login, logout, refreshAccessToken } from './authApi'

describe('authApi', () => {
  afterEach(() => {
    apiRequestMock.mockReset()
  })

  it('로그인 자격 증명을 전송하고 Access Token 응답을 반환한다', async () => {
    const response = {
      accessToken: 'access-token',
      tokenType: 'Bearer' as const,
      expiresIn: 900,
    }
    apiRequestMock.mockResolvedValue(response)

    await expect(login({ email: 'user@example.com', password: 'password' }))
      .resolves.toEqual(response)

    expect(apiRequestMock).toHaveBeenCalledWith('/api/auth/login', {
      method: 'POST',
      json: { email: 'user@example.com', password: 'password' },
    })
  })

  it('refresh cookie를 사용해 Access Token을 재발급한다', async () => {
    const response = {
      accessToken: 'rotated-access-token',
      tokenType: 'Bearer' as const,
      expiresIn: 900,
    }
    apiRequestMock.mockResolvedValue(response)

    await expect(refreshAccessToken()).resolves.toEqual(response)
    expect(apiRequestMock).toHaveBeenCalledWith('/api/auth/refresh', {
      method: 'POST',
    })
  })

  it('로그아웃 endpoint를 호출하고 204 응답을 반환한다', async () => {
    apiRequestMock.mockResolvedValue(undefined)

    await expect(logout()).resolves.toBeUndefined()
    expect(apiRequestMock).toHaveBeenCalledWith('/api/auth/logout', {
      method: 'POST',
    })
  })

  it('인증 API 실패를 호출자에게 전달한다', async () => {
    const error = new Error('refresh token expired')
    apiRequestMock.mockRejectedValue(error)

    await expect(refreshAccessToken()).rejects.toBe(error)
  })
})
