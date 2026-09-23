export interface ApiErrorResponse {
    code: string
    message: string
    errors: Record<string, string>
}


export class ApiError extends Error {
    readonly status: number
    readonly code: string | null
    readonly errors: Record<string, string>

    constructor(
        status: number,
        message: string,
        code: string | null = null,
        errors: Record<string, string> = {},
    ) {
        super(message)
        this.name = 'ApiError'
        this.status = status
        this.code = code
        this.errors = errors
    }
}