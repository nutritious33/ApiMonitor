import { useState } from 'react'
import { pingAdminKey } from '../api'
import { checkLockout, clearAuthAttempts, recordFailedAttempt } from '../auth'

// Re-export so existing Dashboard imports keep working without touching that file's imports.
export { checkLockout, clearAuthAttempts }

// ── Component ──────────────────────────────────────────────────────────────────

interface Props {
  isOpen: boolean
  /** Called after a successful ping (session cookie already set by server). */
  onSuccess: () => void
  onClose: () => void
  /** Called when the rate-limit threshold is exceeded (parent should hide the button). */
  onLockout: () => void
}

export default function AdminLoginModal({ isOpen, onSuccess, onClose, onLockout }: Props) {
  const [key, setKey]         = useState('')
  const [error, setError]     = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  if (!isOpen) return null

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!key.trim() || loading) return

    setLoading(true)
    setError(null)

    try {
      await pingAdminKey(key.trim())
      // 204 — success; server has set the httpOnly admin_session cookie
      setKey('')
      onSuccess()
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
