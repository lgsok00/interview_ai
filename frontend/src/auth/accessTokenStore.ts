let accessToken: string | null = null

export const accessTokenStore = {
    get(): string | null {
        return accessToken
    },

    set(token: string): void {
        const normalizedToken = token.trim()

        if (!normalizedToken) {
            throw new Error('Access Token cannot be empty.')
        }

        accessToken = normalizedToken
    },

    clear(): void {
        accessToken = null
    },
}