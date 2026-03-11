interface Props {
  status: string | null
}

export default function StatusBadge({ status }: Props) {
  const isUp = status === 'UP'
  return (
    <span
      className={`shrink-0 px-2.5 py-1 rounded-full text-xs font-bold uppercase tracking-wide ${
        isUp
          ? 'bg-up-glow text-up'
          : 'bg-down-glow text-down'
      }`}
    >
      {status ?? 'Pending'}
    </span>
  )
}
