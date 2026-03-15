import { useState, useEffect, useRef } from 'react'
import { submitEndpoint, getSubmissionStatus } from '../api'
import type { SubmissionStatusResponse } from '../types'

// ── Shared field component ─────────────────────────────────────────────────────

function FieldInput({
  id,
  label,
  hint,
  type = 'text',
  value,
  onChange,
  placeholder,
  maxLength,
}: {
  id: string
  label: string
  hint?: string
  type?: string
  value: string
  onChange: (v: string) => void
  placeholder: string
  maxLength: number
}) {
  return (
    <div>
      <label className="block text-sm text-muted mb-1" htmlFor={id}>
        {label}{hint && <span className="text-neutral-500"> {hint}</span>}
      </label>
      <input
        id={id}
        type={type}
        value={value}
        onChange={e => onChange(e.target.value)}
        placeholder={placeholder}
        maxLength={maxLength}
        required
        className="w-full bg-neutral-800 border border-line rounded-md px-3 py-2
          text-neutral-100 text-sm placeholder-neutral-500
          focus:outline-none focus:border-neutral-500"
      />
    </div>
  )
}

// ── Admin variant: direct POST /api/custom-endpoints ──────────────────────────

interface AdminProps {
  isOpen: boolean
  isPending: boolean
  onConfirm: (name: string, url: string) => void
  onCancel: () => void
  errorMessage?: string | null
}

function AdminModal({ isOpen, isPending, onConfirm, onCancel, errorMessage }: AdminProps) {
  const [name, setName] = useState('')
  const [url, setUrl] = useState('')

  useEffect(() => {
    if (!isOpen) { setName(''); setUrl('') }
  }, [isOpen])

  if (!isOpen) return null

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (name.trim() && url.trim()) onConfirm(name.trim(), url.trim())
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60">
      <div className="bg-card border border-line rounded-xl p-8 w-full max-w-md shadow-2xl">
        <h2 className="text-lg font-semibold text-neutral-100 mb-6">Add Custom API</h2>

        <form onSubmit={handleSubmit} className="space-y-4">
          <FieldInput
            id="admin-custom-name"
            label="Name"
            value={name}
            onChange={setName}
            placeholder="My API"
            maxLength={100}
          />
          <FieldInput
            id="admin-custom-url"
            label="URL"
            hint="(HTTPS only)"
            type="url"
            value={url}
            onChange={setUrl}
            placeholder="https://api.example.com/health"
            maxLength={500}
          />

          {errorMessage && <p className="text-down text-sm">{errorMessage}</p>}

          <div className="flex justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={() => { setName(''); setUrl(''); onCancel() }}
              className="px-4 py-2 rounded-md text-sm text-muted hover:text-neutral-100 transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isPending || !name.trim() || !url.trim()}
              className="px-5 py-2 bg-neutral-100 hover:bg-white text-neutral-900 font-semibold
                rounded-md transition-colors text-sm disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {isPending ? 'Adding…' : 'Add'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

// ── Public variant: POST /api/submissions + poll for status ───────────────────

type PublicPhase = 'form' | 'polling'

function PublicModal({ isOpen, onCancel }: { isOpen: boolean; onCancel: () => void }) {
  const [name, setName] = useState('')
  const [url, setUrl] = useState('')
  const [phase, setPhase] = useState<PublicPhase>('form')
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [submission, setSubmission] = useState<SubmissionStatusResponse | null>(null)
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null)

  // Reset everything when modal closes
  useEffect(() => {
    if (!isOpen) {
      if (pollRef.current) { clearInterval(pollRef.current); pollRef.current = null }
      setName(''); setUrl(''); setPhase('form')
      setSubmitting(false); setSubmitError(null); setSubmission(null)
    }
  }, [isOpen])

  // Stop polling once a terminal status is reached
  useEffect(() => {
    if (submission?.status === 'APPROVED' || submission?.status === 'DENIED') {
      if (pollRef.current) { clearInterval(pollRef.current); pollRef.current = null }
    }
  }, [submission?.status])

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!name.trim() || !url.trim()) return
    setSubmitting(true)
    setSubmitError(null)
    try {
      const result = await submitEndpoint(name.trim(), url.trim())
      setSubmission(result)
      setPhase('polling')
      // Poll every 5 s
      pollRef.current = setInterval(async () => {
        try {
          const updated = await getSubmissionStatus(result.token)
          setSubmission(updated)
        } catch {
          // ignore transient poll failures
        }
      }, 5_000)
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data
          ?.message ?? 'Failed to submit. Please try again.'
      setSubmitError(msg)
    } finally {
      setSubmitting(false)
    }
  }

  if (!isOpen) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60">
      <div className="bg-card border border-line rounded-xl p-8 w-full max-w-md shadow-2xl">
        <h2 className="text-lg font-semibold text-neutral-100 mb-1">Suggest an API</h2>
        <p className="text-sm text-muted mb-6">
          Submissions are reviewed before being added to the tracker.
        </p>

        {phase === 'form' ? (
          <form onSubmit={handleSubmit} className="space-y-4">
            <FieldInput
              id="pub-custom-name"
              label="Name"
              value={name}
              onChange={setName}
              placeholder="My API"
              maxLength={100}
            />
            <FieldInput
              id="pub-custom-url"
              label="URL"
              hint="(HTTPS only)"
              type="url"
              value={url}
              onChange={setUrl}
              placeholder="https://api.example.com/health"
              maxLength={500}
            />

            {submitError && <p className="text-down text-sm">{submitError}</p>}

            <div className="flex justify-end gap-3 pt-2">
              <button
                type="button"
                onClick={onCancel}
                className="px-4 py-2 rounded-md text-sm text-muted hover:text-neutral-100 transition-colors"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={submitting || !name.trim() || !url.trim()}
                className="px-5 py-2 bg-neutral-100 hover:bg-white text-neutral-900 font-semibold
                  rounded-md transition-colors text-sm disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {submitting ? 'Submitting…' : 'Submit for Review'}
              </button>
            </div>
          </form>
        ) : (
          <PollingStatus submission={submission} onClose={onCancel} />
        )}
      </div>
    </div>
  )
}

// ── Polling status display ─────────────────────────────────────────────────────

function PollingStatus({
  submission,
  onClose,
}: {
  submission: SubmissionStatusResponse | null
  onClose: () => void
}) {
  const status = submission?.status ?? 'PENDING'

  const cfg = {
    PENDING: {
      icon: '⏳',
      label: 'Pending review',
      color: 'text-neutral-400',
      detail: 'Your suggestion is in the queue. This page updates automatically every 5 seconds.',
    },
    APPROVED: {
      icon: '✅',
      label: 'Approved!',
      color: 'text-up',
      detail: 'Your API has been added to the tracker.',
    },
    DENIED: {
      icon: '❌',
      label: 'Not accepted',
      color: 'text-down',
      detail: 'Your suggestion was not accepted at this time.',
    },
  }[status]

  return (
    <div className="text-center space-y-3 py-2">
      <div className="text-4xl">{cfg.icon}</div>
      <p className={`text-base font-semibold ${cfg.color}`}>{cfg.label}</p>
      {submission && (
        <p className="text-sm text-neutral-300 font-medium">{submission.name}</p>
      )}
      <p className="text-sm text-muted">{cfg.detail}</p>

      <div className="pt-2">
        {status !== 'PENDING' ? (
          <button
            onClick={onClose}
            className="px-5 py-2 bg-neutral-100 hover:bg-white text-neutral-900
              font-semibold rounded-md transition-colors text-sm"
          >
            Close
          </button>
        ) : (
          <button
            onClick={onClose}
            className="px-4 py-2 rounded-md text-sm text-muted hover:text-neutral-100 transition-colors"
          >
            Dismiss
          </button>
        )}
      </div>
    </div>
  )
}

// ── Unified public export ──────────────────────────────────────────────────────

export interface AddCustomEndpointModalProps {
  isOpen: boolean
  isAdmin: boolean
  // Admin-only
  isPending?: boolean
  onConfirm?: (name: string, url: string) => void
  errorMessage?: string | null
  // Shared
  onCancel: () => void
}

export default function AddCustomEndpointModal({
  isOpen,
  isAdmin,
  isPending = false,
  onConfirm,
  errorMessage,
  onCancel,
}: AddCustomEndpointModalProps) {
  if (isAdmin) {
    return (
      <AdminModal
        isOpen={isOpen}
        isPending={isPending}
        onConfirm={onConfirm ?? (() => {})}
        onCancel={onCancel}
        errorMessage={errorMessage}
      />
    )
  }
  return <PublicModal isOpen={isOpen} onCancel={onCancel} />
}
