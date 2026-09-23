import {apiRequest} from './client'

interface HealthResponse {
    status: string
}

export async function getHealth(): Promise<string> {
    const response = await apiRequest<HealthResponse>('/actuator/health')
    return response.status
}