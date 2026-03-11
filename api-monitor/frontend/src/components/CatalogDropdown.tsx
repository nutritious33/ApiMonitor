import { useState, useEffect } from 'react'
import type { ApiEndpoint } from '../types'

interface Props {
  apis: ApiEndpoint[]
  onActivate: (id: number) => void
  isPending: boolean
}

export default function CatalogDropdown({ apis, onActivate, isPending }: Props) {
  const inactive = apis.filter(a => !a.isActive)
  const [selectedId, setSelectedId] = useState<number | null>(
    inactive[0]?.id ?? null,
  )

  // When the inactive list changes, keep selection valid
  useEffect(() => {
    const ids = inactive.map(a => a.id)
    if (selectedId === null || !ids.includes(selectedId)) {
      setSelectedId(ids[0] ?? null)
    }
  }, [apis]) // eslint-disable-line react-hooks/exhaustive-deps

  const isEmpty = inactive.length === 0

  return (
    <div className="flex gap-3">
      <select
        className="grow bg-card text-neutral-100 border border-line rounded-md px-3 py-2
          outline-none focus:border-accent disabled:opacity-50"
        value={selectedId ?? ''}
        disabled={isEmpty}
        onChange={e => setSelectedId(Number(e.target.value))}
      >
        {isEmpty ? (
          <option>All APIs are active</option>
        ) : (
          inactive.map(api => (
            <option key={api.id} value={api.id}>
              {api.name}
            </option>
          ))
        )}
      </select>

      <button
        className="px-5 py-2 bg-accent hover:bg-accent-hover text-white font-semibold
          rounded-md transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        disabled={isEmpty || isPending || selectedId === null}
        onClick={() => selectedId !== null && onActivate(selectedId)}
      >
        {isPending ? 'Adding…' : 'Add to Tracker'}
      </button>
    </div>
  )
}
