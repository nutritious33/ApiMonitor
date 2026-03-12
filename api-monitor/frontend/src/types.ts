export type ApiEndpointSource = 'BUILTIN' | 'CUSTOM'

export interface ApiEndpoint {
  id: number
  name: string
  url: string
  isActive: boolean
  currentStatus: string | null
  lastLatencyMs: number | null
  successfulChecks: number
  totalChecks: number
  lastCheckedAt: string | null
  source: ApiEndpointSource
}
