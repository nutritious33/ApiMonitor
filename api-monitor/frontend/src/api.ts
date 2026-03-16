import axios from 'axios'
import type { ApiEndpoint, PendingSubmission, SubmissionStatusResponse } from './types'

// ── CSRF helper ────────────────────────────────────────────────────────────────

/** Reads a cookie value by name from document.cookie. Returns null if absent. */
function getCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'))
  return match ? decodeURIComponent(match[1]) : null
}

// ── Authenticated client ───────────────────────────────────────────────────────
//
// Sends the admin_session httpOnly cookie automatically (withCredentials) and
// adds the XSRF-TOKEN cookie value as X-XSRF-TOKEN for CSRF protection.
// The browser sets XSRF-TOKEN on the first GET response from the server.

const client = axios.create({ withCredentials: true })

client.interceptors.request.use(config => {
  const xsrf = getCookie('XSRF-TOKEN')
  if (xsrf) config.headers['X-XSRF-TOKEN'] = xsrf
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

// ── Admin: session management ─────────────────────────────────────────────────

/**
 * Validates a candidate API key against the server.
 * On 204 the server sets an httpOnly admin_session cookie.
 * Rejects with an AxiosError (status 401) if the key is wrong.
 * Uses the credentialed client so the Set-Cookie response is accepted by the
 * browser and the XSRF token is included for CSRF protection.
 */
export const pingAdminKey = (key: string): Promise<void> =>
  client.post('/api/auth/ping', null, { headers: { 'X-API-Key': key } }).then(() => undefined)

/**
 * Returns true if the current browser session has a valid admin_session cookie.
 * Called on page load to restore admin UI without storing the key in localStorage.
 */
export const getAdminStatus = (): Promise<boolean> =>
  client.get<{ admin: boolean }>('/api/auth/status').then(r => r.data.admin)

/** Ends the admin session server-side and clears the admin_session cookie. */
export const logoutAdmin = (): Promise<void> =>
  client.post('/api/auth/logout').then(() => undefined)
