import { useState, useEffect } from 'react'

interface Props {
  isOpen: boolean
  isPending: boolean
  onConfirm: (name: string, url: string) => void
  onCancel: () => void
  errorMessage?: string | null
}

export default function AddCustomEndpointModal({
  isOpen,
  isPending,
  onConfirm,
  onCancel,
  errorMessage,
}: Props) {
  const [name, setName] = useState('')
  const [url, setUrl] = useState('')

  // Reset fields whenever the modal is closed (success or cancel)
  useEffect(() => {
    if (!isOpen) {
      setName('')
      setUrl('')
    }
  }, [isOpen])

  if (!isOpen) return null

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (name.trim() && url.trim()) {
      onConfirm(name.trim(), url.trim())
    }
  }

  function handleCancel() {
    setName('')
    setUrl('')
    onCancel()
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60">
      <div className="bg-card border border-line rounded-xl p-8 w-full max-w-md shadow-2xl">
        <h2 className="text-lg font-semibold text-neutral-100 mb-6">
          Add Custom API
        </h2>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm text-muted mb-1" htmlFor="custom-name">
              Name
            </label>
            <input
              id="custom-name"
              type="text"
              value={name}
              onChange={e => setName(e.target.value)}
              placeholder="My API"
              maxLength={100}
              required
              className="w-full bg-neutral-800 border border-line rounded-md px-3 py-2
                text-neutral-100 text-sm placeholder-neutral-500
                focus:outline-none focus:border-neutral-500"
            />
          </div>

          <div>
            <label className="block text-sm text-muted mb-1" htmlFor="custom-url">
              URL <span className="text-neutral-500">(HTTPS only)</span>
            </label>
            <input
              id="custom-url"
              type="url"
              value={url}
              onChange={e => setUrl(e.target.value)}
              placeholder="https://api.example.com/health"
              maxLength={500}
              required
              className="w-full bg-neutral-800 border border-line rounded-md px-3 py-2
                text-neutral-100 text-sm placeholder-neutral-500
                focus:outline-none focus:border-neutral-500"
            />
          </div>

          {errorMessage && (
            <p className="text-down text-sm">{errorMessage}</p>
          )}

          <div className="flex justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={handleCancel}
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
