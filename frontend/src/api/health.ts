import {env} from '../config/env'

interface HealthResponse {
    status: string
}

export async function getHealth(): Promise<string> {
    const response = await fetch(`${env.apiBaseUrl}/actuator/health`)

    if (!response.ok) {
        throw new Error(`Health check failed: ${response.status}`)
    }

    const body: HealthResponse = await response.json()
    return body.status
}