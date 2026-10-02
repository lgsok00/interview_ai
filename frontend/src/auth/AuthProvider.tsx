import {type ReactNode, useCallback, useEffect, useMemo, useRef, useState} from 'react'
import {login as loginRequest, type LoginCredentials, logout as logoutRequest, refreshAccessToken} from './authApi'
import {accessTokenStore} from './accessTokenStore'
import {AuthContext} from './authContext'

interface AuthProviderProps {
    children: ReactNode
}

export function AuthProvider({children}: AuthProviderProps) {
    const [isAuthenticated, setIsAuthenticated] = useState(
        () => accessTokenStore.get() !== null,
    )

    const [isInitializing, setIsInitializing] = useState(true)
    const initialized = useRef(false)

    const clearSession = useCallback(() => {
        accessTokenStore.clear()
        setIsAuthenticated(false)
    }, [])

    const refresh = useCallback(async () => {
        try {
            const response = await refreshAccessToken()
            accessTokenStore.set(response.accessToken)

            setIsAuthenticated(true)

        } catch (error) {
            accessTokenStore.clear()
            setIsAuthenticated(false)

            throw error
        }
    }, [])

    const login = useCallback(async (credentials: LoginCredentials) => {
        const response = await loginRequest(credentials)
        accessTokenStore.set(response.accessToken)

        setIsAuthenticated(true)
    }, [])

    const logout = useCallback(async () => {
        try {
            await logoutRequest()

        } finally {
            accessTokenStore.clear()
            setIsAuthenticated(false)
        }
    }, [])

    useEffect(() => {
        if (initialized.current) {
            return
        }

        initialized.current = true

        void refresh()
            .catch(() => {
                // 유효한 refresh cookie가 없으면 비로그인 상태로 시작한다.
            })
            .finally(() => {
                setIsInitializing(false)
            })
    }, [refresh])

    const value = useMemo(
        () => ({
            isAuthenticated,
            isInitializing,
            login,
            refresh,
            logout,
            clearSession,
        }),
        [
            isAuthenticated,
            isInitializing,
            login,
            refresh,
            logout,
            clearSession,
        ],
    )

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}