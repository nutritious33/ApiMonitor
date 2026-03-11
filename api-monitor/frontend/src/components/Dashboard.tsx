import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  DndContext,
  closestCenter,
  PointerSensor,
  useSensor,
  useSensors,
} from '@dnd-kit/core'
import type { DragEndEvent } from '@dnd-kit/core'
import { SortableContext, rectSortingStrategy, arrayMove } from '@dnd-kit/sortable'

import {
  fetchMetrics,
  activateEndpoint,
  deactivateEndpoint,
  deactivateAll,
} from '../api'
import Leaderboard from './Leaderboard'
import CatalogDropdown from './CatalogDropdown'
import MonitorCard from './MonitorCard'
import ClearAllModal from './ClearAllModal'

// ── Helpers ──────────────────────────────────────────────────────────────────

function loadOrder(): number[] {
  try {
    return JSON.parse(localStorage.getItem('apiOrder') ?? '[]')
  } catch {
    return []
  }
}

function saveOrder(order: number[]) {
  localStorage.setItem('apiOrder', JSON.stringify(order))
}

// ── Component ─────────────────────────────────────────────────────────────────

export default function Dashboard() {
  const qc = useQueryClient()

  // Fetch + auto-poll every 10 s
  const { data: apis = [], isError } = useQuery({
    queryKey: ['metrics'],
    queryFn: fetchMetrics,
    refetchInterval: 10_000,
  })

  // Ordered list of active API ids — drives both the DnD context and card render order
  const [cardOrder, setCardOrder] = useState<number[]>(loadOrder)

  // Reconcile order whenever the server data changes
  useEffect(() => {
    const activeIds = apis.filter(a => a.isActive).map(a => a.id)
    setCardOrder(prev => [
      ...prev.filter(id => activeIds.includes(id)),      // keep existing order
      ...activeIds.filter(id => !prev.includes(id)),     // append newly activated
    ])
  }, [apis])

  // Modal state
  const [modalOpen, setModalOpen] = useState(false)

  // ── Mutations ──────────────────────────────────────────────────────────────

  const invalidate = () => qc.invalidateQueries({ queryKey: ['metrics'] })

  const activateMutation = useMutation({
    mutationFn: activateEndpoint,
    onSuccess: invalidate,
  })

  const deactivateMutation = useMutation({
    mutationFn: deactivateEndpoint,
    onSuccess: invalidate,
  })

  const clearAllMutation = useMutation({
    mutationFn: deactivateAll,
    onSuccess: () => {
      setCardOrder([])
      saveOrder([])
      setModalOpen(false)
      invalidate()
    },
  })

  // ── Drag-and-drop ──────────────────────────────────────────────────────────

  const sensors = useSensors(
    useSensor(PointerSensor, {
      // Require 8 px movement before starting a drag — lets button clicks fire normally
      activationConstraint: { distance: 8 },
    }),
  )

  function handleDragEnd(event: DragEndEvent) {
    const { active, over } = event
    if (!over || active.id === over.id) return
    setCardOrder(prev => {
      const oldIndex = prev.indexOf(Number(active.id))
      const newIndex = prev.indexOf(Number(over.id))
      const next = arrayMove(prev, oldIndex, newIndex)
      saveOrder(next)
      return next
    })
  }

  // ── Derived data ───────────────────────────────────────────────────────────

  const activeApis = apis.filter(a => a.isActive)
  // Map to keep card data in the drag-sorted order
  const orderedCards = cardOrder
    .map(id => activeApis.find(a => a.id === id))
    .filter((a): a is NonNullable<typeof a> => a != null)

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <div className="max-w-6xl mx-auto px-6 py-8">

      {/* ── Page header ─────────────────────────────────────── */}
      <div className="flex justify-between items-center mb-8">
        <h1 className="text-[1.75rem] font-semibold">API Health Monitor</h1>
        {isError && (
          <span className="text-down text-sm">⚠ Unable to reach backend</span>
        )}
      </div>

      {/* ── Leaderboard ─────────────────────────────────────── */}
      <section className="mb-10">
        <SectionHeader title="Top 3 Fastest APIs" />
        <Leaderboard apis={apis} />
      </section>

      {/* ── Catalog ─────────────────────────────────────────── */}
      <section className="mb-10">
        <SectionHeader title="API Catalog" />
        <CatalogDropdown
          apis={apis}
          onActivate={id => activateMutation.mutate(id)}
          isPending={activateMutation.isPending}
        />
      </section>

      {/* ── Active Monitor ───────────────────────────────────── */}
      <section>
        <div className="flex justify-between items-center border-b border-line pb-2 mb-6">
          <h2 className="text-xl text-muted font-normal">Active Monitor</h2>
          {activeApis.length > 0 && (
            <button
              className="px-5 py-2 bg-down hover:bg-down-hover text-white font-semibold
                rounded-md transition-colors text-sm"
              onClick={() => setModalOpen(true)}
            >
              Clear All
            </button>
          )}
        </div>

        {activeApis.length === 0 ? (
          <p className="text-muted text-sm">
            No APIs being monitored. Add one from the catalog above.
          </p>
        ) : (
          <DndContext
            sensors={sensors}
            collisionDetection={closestCenter}
            onDragEnd={handleDragEnd}
          >
            <SortableContext items={cardOrder} strategy={rectSortingStrategy}>
              <div className="grid grid-cols-[repeat(auto-fill,minmax(320px,1fr))] gap-6">
                {orderedCards.map(api => (
                  <MonitorCard
                    key={api.id}
                    api={api}
                    onRemove={id => deactivateMutation.mutate(id)}
                  />
                ))}
              </div>
            </SortableContext>
          </DndContext>
        )}
      </section>

      {/* ── Confirmation modal ───────────────────────────────── */}
      <ClearAllModal
        isOpen={modalOpen}
        isPending={clearAllMutation.isPending}
        onConfirm={() => clearAllMutation.mutate()}
        onCancel={() => setModalOpen(false)}
      />
    </div>
  )
}

function SectionHeader({ title }: { title: string }) {
  return (
    <div className="flex justify-between items-center border-b border-line pb-2 mb-4">
      <h2 className="text-xl text-muted font-normal">{title}</h2>
    </div>
  )
}
