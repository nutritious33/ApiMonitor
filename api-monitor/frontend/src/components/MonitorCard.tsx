import { useSortable } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import type { ApiEndpoint } from '../types'
import StatusBadge from './StatusBadge'

interface Props {
  api: ApiEndpoint
  onRemove: (id: number) => void
}

export default function MonitorCard({ api, onRemove }: Props) {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: api.id })

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
  }

  const uptime =
    api.totalChecks > 0
      ? ((api.successfulChecks / api.totalChecks) * 100).toFixed(2)
      : '100.00'

  const timeString = api.lastCheckedAt
    ? new Date(api.lastCheckedAt + 'Z').toLocaleTimeString()
    : 'N/A'

  return (
    <div
      ref={setNodeRef}
      style={style}
      className={`group relative bg-card border border-line rounded-xl p-6 cursor-grab transition-shadow
        ${isDragging ? 'opacity-40 shadow-none' : 'hover:shadow-[0_0_15px_#262626]'}`}
      {...attributes}
      {...listeners}
    >
      {/* Remove button — appears on hover, click stops drag propagation */}
      <button
        className="absolute top-2 right-2 w-6 h-6 rounded-full bg-white/10 text-muted
          opacity-0 group-hover:opacity-100 transition-opacity hover:bg-down hover:text-white
          flex items-center justify-center text-base leading-none"
        title="Remove from tracker"
        onPointerDown={e => e.stopPropagation()}
        onClick={() => onRemove(api.id)}
      >
        ×
      </button>

      {/* Card header */}
      <div className="flex justify-between items-start mb-4 gap-2">
        <div className="text-[1.05rem] font-semibold pr-2 min-w-0">
          <a
            href={api.url}
            target="_blank"
            rel="noopener noreferrer"
            className="text-neutral-100 hover:underline break-words"
            onPointerDown={e => e.stopPropagation()}
          >
            {api.name}
          </a>
        </div>
        <StatusBadge status={api.currentStatus} />
      </div>

      {/* Metrics */}
      <div className="space-y-1.5 text-sm text-muted">
        <Metric label="Uptime" value={`${uptime}%`} />
        <Metric
          label="Latency"
          value={api.lastLatencyMs != null ? `${api.lastLatencyMs} ms` : '--'}
        />
        <Metric
          label="Checks (S/T)"
          value={`${api.successfulChecks}/${api.totalChecks}`}
        />
        <Metric label="Last Checked" value={timeString} />
      </div>
    </div>
  )
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between">
      <span>{label}</span>
      <span className="text-neutral-100 font-medium font-mono">{value}</span>
    </div>
  )
}
