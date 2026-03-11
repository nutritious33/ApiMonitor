interface Props {
  isOpen: boolean
  isPending: boolean
  onConfirm: () => void
  onCancel: () => void
}

export default function ClearAllModal({
  isOpen,
  isPending,
  onConfirm,
  onCancel,
}: Props) {
  if (!isOpen) return null

  return (
    <div
      className="fixed inset-0 bg-black/70 flex justify-center items-center z-50"
      onClick={onCancel}
    >
      <div
        className="bg-card border border-line rounded-xl p-8 text-center max-w-sm w-full mx-4"
        onClick={e => e.stopPropagation()}
      >
        <h3 className="text-lg font-semibold mt-0 mb-6">
          Are you sure you want to clear all?
        </h3>
        <div className="flex gap-3 justify-center">
          <button
            className="px-5 py-2 bg-down hover:bg-down-hover text-white font-semibold
              rounded-md transition-colors disabled:opacity-50"
            disabled={isPending}
            onClick={onConfirm}
          >
            {isPending ? 'Clearing…' : 'Yes, clear all'}
          </button>
          <button
            className="px-5 py-2 bg-surface hover:bg-neutral-600 text-neutral-100
              font-semibold rounded-md transition-colors"
            onClick={onCancel}
          >
            Cancel
          </button>
        </div>
      </div>
    </div>
  )
}
