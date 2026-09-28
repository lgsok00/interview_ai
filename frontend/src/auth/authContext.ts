import {createContext} from "react";
import type {LoginCredentials} from "./authApi";

export interface AuthContextValue {
    isAuthenticated: boolean
    isInitializing: boolean
    login: (credentials: LoginCredentials) => Promise<void>
    refresh: () => Promise<void>
    logout: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)