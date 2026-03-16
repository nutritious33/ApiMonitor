/**
 * auth.ts — Admin authentication helpers.
 *
 * Session lifetime is enforced server-side via the httpOnly admin_session
 * cookie (8-hour sliding window). This file only manages the client-side
 * lockout mechanism (UX friction against brute-force) and provides async
 * wrappers around the session status / logout API calls.
 *
 * SECURITY NOTES
 * ──────────────
 * • The client-side lockout (ATTEMPTS_KEY / LOCKOUT_KEY) is UX friction only.
 *   A determined attacker can bypass it by clearing localStorage or using a
 *   different browser. The server-side rate limit on POST /api/auth/ping is
 *   the actual security control.
 *
 * • Session expiry is enforced server-side. The admin_session cookie expires
 *   after 8 hours of inactivity; the server extends it on every authenticated
 *   request. No client-side timer or localStorage entry is needed.
 */

import { getAdminStatus, logoutAdmin } from './api'

// ── Storage keys ──────────────────────────────────────────────────────────────

const ATTEMPTS_KEY = 'admin_auth_attempts'   // { count: number, windowStart: number }
const LOCKOUT_KEY  = 'admin_auth_lockout'    // expiry timestamp (ms)

// ── Rate-limit constants ───────────────────────────────────────────────────────

const MAX_ATTEMPTS = 10
const WINDOW_MS    = 10 * 60 * 1000   // 10 minutes
const LOCKOUT_MS   = 60 * 60 * 1000   // 1 hour

// ── Lockout helpers ───────────────────────────────────────────────────────────

/** Returns true if the user is currently locked out. Clears an expired lockout automatically. */
export function checkLockout(): boolean {
  const raw = localStorage.getItem(LOCKOUT_KEY)
  if (!raw) return false
  if (Date.now() > Number(raw)) {
    localStorage.removeItem(LOCKOUT_KEY)
    localStorage.removeItem(ATTEMPTS_KEY)
    return false
  }
  return true
}

/** Clears attempt counters and any lockout — call on successful authentication. */
export function clearAuthAttempts() {
  localStorage.removeItem(ATTEMPTS_KEY)
  localStorage.removeItem(LOCKOUT_KEY)
}

function getAttempts(): { count: number; windowStart: number } {
  try {
    return JSON.parse(localStorage.getItem(ATTEMPTS_KEY) ?? 'null') ??
      { count: 0, windowStart: Date.now() }
  } catch {
    return { count: 0, windowStart: Date.now() }
  }
}

// ── Session helpers ────────────────────────────────────────────────────────────

/**
 * Checks whether the browser currently has a valid admin session cookie.
 * Calls GET /api/auth/status. Returns false on any network or server error.
 */
export async function isAdminSession(): Promise<boolean> {
  try {
    return await getAdminStatus()
  } catch {
    return false
  }
}

/**
 * Ends the admin session server-side (clears the httpOnly cookie).
 * Best-effort — the cookie will expire naturally if the request fails.
 */
export async function logout(): Promise<void> {
  try {
    await logoutAdmin()
  } catch {
    // best-effort: session expires naturally after 8 hours of inactivity
  }
}

// ── Attempt helpers ────────────────────────────────────────────────────────────

/**
 * Records one failed attempt.
 * Returns true if the limit is now exceeded (caller should trigger lockout).
 * Only call this on an explicit 401 — network errors do NOT count.
 */
export function recordFailedAttempt(): boolean {
  const now  = Date.now()
  const data = getAttempts()

  const fresh = now - data.windowStart > WINDOW_MS
    ? { count: 1, windowStart: now }          // reset: previous window has expired
    : { ...data, count: data.count + 1 }       // accumulate within current window

  localStorage.setItem(ATTEMPTS_KEY, JSON.stringify(fresh))

  if (fresh.count >= MAX_ATTEMPTS) {
    localStorage.setItem(LOCKOUT_KEY, String(now + LOCKOUT_MS))
    return true
  }
  return false
}
