import { useState } from 'react'
import { pingAdminKey } from '../api'

// ── localStorage keys ──────────────────────────────────────────────────────────

const ATTEMPTS_KEY = 'admin_auth_attempts'   // { count: number, windowStart: number }
const LOCKOUT_KEY  = 'admin_auth_lockout'    // expiry timestamp (ms)

const MAX_ATTEMPTS   = 10
const WINDOW_MS      = 10 * 60 * 1000   // 10 minutes
const LOCKOUT_MS     = 60 * 60 * 1000   // 1 hour

// ── Helpers (exported for Dashboard to use on mount) ──────────────────────────

/** Returns true if the user is currently locked out. Clears an expired lockout. */
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

// ── Internal helpers ───────────────────────────────────────────────────────────

function getAttempts(): { count: number; windowStart: number } {
  try {
    return JSON.parse(localStorage.getItem(ATTEMPTS_KEY) ?? 'null') ??
      { count: 0, windowStart: Date.now() }
  } catch {
    return { count: 0, windowStart: Date.now() }
  }
}

/**
 * Records one failed attempt.
 * Returns true if the limit is now exceeded (caller should trigger lockout).
 * Only call this on an explicit 401 — network errors do NOT count.
 */
function recordFailedAttempt(): boolean {
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

// ── Component ──────────────────────────────────────────────────────────────────

interface Props {
  isOpen: boolean
  /** Called with the validated key so the parent can store it and update state. */
  onSuccess: (key: string) => void
  onClose: () => void
  /** Called when the rate-limit threshold is exceeded (parent should hide the button). */
  onLockout: () => void
}

export default function AdminLoginModal({ isOpen, onSuccess, onClose, onLockout }: Props) {
  const [key, setKey]       = useState('')
  const [error, setError]   = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  if (!isOpen) return null

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!key.trim() || loading) return

    setLoading(true)
    setError(null)

    try {
      await pingAdminKey(key.trim())
      // 204 — success
      clearAuthAttempts()
      setKey('')
      onSuccess(key.trim())
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status

      if (status === 401) {
        // Count only explicit server rejections — not network/timeout errors
        const lockedOut = recordFailedAttempt()
        if (lockedOut) {
          setKey('')
          onLockout()   // parent hides the button and closes this modal
        } else {
          setError('Incorrect key')
        }
      }
      // Network/server errors (no status): silently ignore — not a failed auth attempt
    } finally {
      setLoading(false)
    }
  }

  function handleClose() {
    setKey('')
    setError(null)
    onClose()
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60">
      <div className="bg-card border border-line rounded-xl p-7 w-full max-w-xs shadow-2xl">
        <h2 className="text-base font-semibold text-neutral-100 mb-5">Admin sign in</h2>

        <form onSubmit={handleSubmit} className="space-y-4">
          <input
            type="password"
            autoFocus
            value={key}
            onChange={e => { setKey(e.target.value); setError(null) }}
            placeholder="API key"
            autoComplete="current-password"
            className="w-full bg-neutral-800 border border-line rounded-md px-3 py-2
              text-neutral-100 text-sm placeholder-neutral-500
              focus:outline-none focus:border-neutral-500"
          />

          {error && <p className="text-down text-sm">{error}</p>}

          <div className="flex justify-end gap-3">
            <button
              type="button"
              onClick={handleClose}
              className="px-4 py-2 rounded-md text-sm text-muted hover:text-neutral-100 transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={loading || !key.trim()}
              className="px-5 py-2 bg-neutral-100 hover:bg-white text-neutral-900 font-semibold
                rounded-md transition-colors text-sm disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? 'Checking…' : 'Sign in'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
