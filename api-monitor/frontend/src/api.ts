import axios from 'axios'
import type { ApiEndpoint } from './types'

/**
 * API key is injected at build time via VITE_API_KEY.
 * Falls back to 'dev-api-key' so local mvn spring-boot:run works without extra config.
 */
const API_KEY = import.meta.env.VITE_API_KEY ?? 'dev-api-key'

const client = axios.create({
  headers: { 'X-API-Key': API_KEY },
})

export const fetchMetrics = (): Promise<ApiEndpoint[]> =>
  client.get<ApiEndpoint[]>('/api/health-metrics').then(r => r.data)

export const activateEndpoint = (id: number): Promise<void> =>
  client.post(`/api/health-metrics/activate/${id}`).then(() => undefined)

export const deactivateEndpoint = (id: number): Promise<void> =>
  client.post(`/api/health-metrics/deactivate/${id}`).then(() => undefined)

export const deactivateAll = (): Promise<void> =>
  client.post('/api/health-metrics/deactivate/all').then(() => undefined)
