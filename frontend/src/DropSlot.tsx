import { memo } from 'react'
import { useDroppable } from '@dnd-kit/core'

interface DropSlotProps {
  teamId: string
  index: number
}

function DropSlotInner({ teamId, index }: DropSlotProps) {
  const { setNodeRef, isOver } = useDroppable({
    id: `${teamId}::${index}`,
  })

  return (
    <div
      ref={setNodeRef}
      className={`drop-slot ${isOver ? 'drop-slot-over' : ''}`}
      aria-hidden
    />
  )
}

export const DropSlot = memo(DropSlotInner)
