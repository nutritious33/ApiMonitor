import axios from 'axios'
import type { ApiEndpoint, PendingSubmission, SubmissionStatusResponse } from './types'

// ── Admin auth ────────────────────────────────────────────────────────────────
// Admin: set key once via DevTools: localStorage.setItem('admin_key', 'your-key')
// Refresh the page after setting. The key is never baked into the bundle.
const ADMIN_KEY_STORAGE = 'admin_key'

export function getAdminKey(): string | null {
  return localStorage.getItem(ADMIN_KEY_STORAGE)
}

export function isAdmin(): boolean {
  return !!getAdminKey()
}

/**
 * Authenticated axios client — attaches X-API-Key from localStorage on every
 * request. Used for all admin/write operations.
 */
const client = axios.create()

client.interceptors.request.use(config => {
  const key = getAdminKey()
  if (key) config.headers['X-API-Key'] = key
  return config
})

// ── Metrics (public read) ─────────────────────────────────────────────────────

export const fetchMetrics = (): Promise<ApiEndpoint[]> =>
  axios.get<ApiEndpoint[]>('/api/health-metrics').then(r => r.data)

// ── Public: watchlist management (predefined catalog endpoints) ───────────────
// No API key required — anyone may activate, deactivate, or clear all.

export const activateEndpoint = (id: number): Promise<void> =>
  axios.post(`/api/health-metrics/activate/${id}`).then(() => undefined)

export const deactivateEndpoint = (id: number): Promise<void> =>
  axios.post(`/api/health-metrics/deactivate/${id}`).then(() => undefined)

export const deactivateAll = (): Promise<void> =>
  axios.post('/api/health-metrics/deactivate/all').then(() => undefined)

export const addCustomEndpoint = (name: string, url: string): Promise<void> =>
  client.post('/api/custom-endpoints', { name, url }).then(() => undefined)

export const deleteCustomEndpoint = (id: number): Promise<void> =>
  client.delete(`/api/custom-endpoints/${id}`).then(() => undefined)

// ── Admin: submission queue management ───────────────────────────────────────

export const fetchPendingSubmissions = (): Promise<PendingSubmission[]> =>
  client.get<PendingSubmission[]>('/api/submissions').then(r => r.data)

export const approveSubmission = (id: number): Promise<void> =>
  client.post(`/api/submissions/${id}/approve`).then(() => undefined)

export const denySubmission = (id: number): Promise<void> =>
  client.post(`/api/submissions/${id}/deny`).then(() => undefined)

// ── Public: submission flow ───────────────────────────────────────────────────

export const submitEndpoint = (
  name: string,
  url: string,
): Promise<SubmissionStatusResponse> =>
  axios.post<SubmissionStatusResponse>('/api/submissions', { name, url }).then(r => r.data)

export const getSubmissionStatus = (token: string): Promise<SubmissionStatusResponse> =>
  axios.get<SubmissionStatusResponse>(`/api/submissions/${token}`).then(r => r.data)

// ── Admin: key validation ──────────────────────────────────────────────────────

/**
 * Validates a candidate API key against the server.
 * Resolves (void) on 204 if the key is correct.
 * Rejects with an AxiosError (status 401) if the key is wrong.
 * Uses plain axios — not the interceptor-attached client — so the key under
 * test is sent explicitly rather than being pulled from localStorage.
 */
export const pingAdminKey = (key: string): Promise<void> =>
  axios.get('/api/auth/ping', { headers: { 'X-API-Key': key } }).then(() => undefined)
