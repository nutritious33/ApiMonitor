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

// ── Submission queue ──────────────────────────────────────────────────────────

export type SubmissionStatus = 'PENDING' | 'APPROVED' | 'DENIED'

/** Full submission record — returned to admin only. */
export interface PendingSubmission {
  id: number
  name: string
  url: string
  status: SubmissionStatus
  submittedAt: string
  submissionToken: string
}

/** Minimal status response — returned to the public submitter for polling. */
export interface SubmissionStatusResponse {
  token: string
  name: string
  status: SubmissionStatus
}
