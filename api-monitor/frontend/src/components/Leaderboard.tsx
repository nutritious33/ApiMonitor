import type { ApiEndpoint } from '../types'

interface Props {
  apis: ApiEndpoint[]
}

export default function Leaderboard({ apis }: Props) {
  const top3 = apis
    .filter(a => a.isActive && a.currentStatus === 'UP' && a.lastLatencyMs != null)
    .sort((a, b) => (a.lastLatencyMs ?? Infinity) - (b.lastLatencyMs ?? Infinity))
    .slice(0, 3)

  if (top3.length === 0) {
    return (
      <p className="text-muted text-sm">
        No active APIs with latency data available.
      </p>
    )
  }
  
  return (
    <div className="grid grid-cols-[repeat(auto-fill,minmax(280px,1fr))] gap-4">
      {top3.map((api, index) => {
        const rankColor = ['text-rank-gold', 'text-rank-silver', 'text-rank-bronze'][index];
        return (
          <div
            key={api.id}
            className="flex items-center bg-card border border-line rounded-lg px-4 py-3 gap-3"
          >
            <span className={`text-base font-bold ${rankColor} w-6 shrink-0`}>
              #{index + 1}
            </span>
            <span className="font-medium grow truncate">{api.name}</span>
            <span className="font-mono text-up shrink-0">
              {api.lastLatencyMs} ms
            </span>
          </div>
        )
      })}
    </div>
  )

}
