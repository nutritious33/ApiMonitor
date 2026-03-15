import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { fetchPendingSubmissions, approveSubmission, denySubmission } from '../api'
import type { PendingSubmission } from '../types'

export default function SubmissionsPanel() {
  const qc = useQueryClient()

  const { data: submissions = [], isError } = useQuery({
    queryKey: ['submissions'],
    queryFn: fetchPendingSubmissions,
    refetchInterval: 15_000,
  })

  const invalidate = () => qc.invalidateQueries({ queryKey: ['submissions'] })

  const approveMutation = useMutation({
    mutationFn: approveSubmission,
    onSuccess: () => {
      invalidate()
      qc.invalidateQueries({ queryKey: ['metrics'] })
    },
  })

  const denyMutation = useMutation({
    mutationFn: denySubmission,
    onSuccess: invalidate,
  })

  return (
    <section className="mb-10">
      <div className="flex justify-between items-center border-b border-line pb-2 mb-4">
        <h2 className="text-xl text-muted font-normal">
          Pending Submissions
          {submissions.length > 0 && (
            <span className="ml-2 inline-flex items-center justify-center
              w-5 h-5 rounded-full bg-accent text-white text-xs font-bold">
              {submissions.length}
            </span>
          )}
        </h2>
      </div>

      {isError && (
        <p className="text-down text-sm">⚠ Failed to load submissions.</p>
      )}

      {!isError && submissions.length === 0 && (
        <p className="text-muted text-sm">No pending submissions.</p>
      )}

      {submissions.length > 0 && (
        <div className="space-y-3">
          {submissions.map(sub => (
            <SubmissionRow
              key={sub.id}
              submission={sub}
              isApproving={approveMutation.isPending && approveMutation.variables === sub.id}
              isDenying={denyMutation.isPending && denyMutation.variables === sub.id}
              onApprove={() => approveMutation.mutate(sub.id)}
              onDeny={() => denyMutation.mutate(sub.id)}
            />
          ))}
        </div>
      )}
    </section>
  )
}

function SubmissionRow({
  submission,
  isApproving,
  isDenying,
  onApprove,
  onDeny,
}: {
  submission: PendingSubmission
  isApproving: boolean
  isDenying: boolean
  onApprove: () => void
  onDeny: () => void
}) {
  const submittedAt = new Date(submission.submittedAt).toLocaleString()
  const busy = isApproving || isDenying

  return (
    <div className="bg-card border border-line rounded-xl px-5 py-4 flex flex-col sm:flex-row
      sm:items-center gap-3">
      {/* Info */}
      <div className="flex-1 min-w-0">
        <p className="text-neutral-100 font-semibold truncate">{submission.name}</p>
        <a
          href={submission.url}
          target="_blank"
          rel="noopener noreferrer"
          className="text-sm text-accent hover:underline break-all"
        >
          {submission.url}
        </a>
        <p className="text-xs text-muted mt-1">Submitted {submittedAt}</p>
      </div>

      {/* Actions */}
      <div className="flex gap-2 shrink-0">
        <button
          onClick={onApprove}
          disabled={busy}
          className="px-4 py-1.5 bg-up hover:bg-up/80 text-white font-semibold
            rounded-md transition-colors text-sm disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {isApproving ? 'Approving…' : 'Approve'}
        </button>
        <button
          onClick={onDeny}
          disabled={busy}
          className="px-4 py-1.5 bg-neutral-700 hover:bg-neutral-600 text-neutral-100 font-semibold
            rounded-md transition-colors text-sm disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {isDenying ? 'Denying…' : 'Deny'}
        </button>
      </div>
    </div>
  )
}
