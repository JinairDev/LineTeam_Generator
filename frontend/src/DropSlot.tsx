import { memo } from 'react'
import { useDroppable } from '@dnd-kit/core'

interface DropSlotProps {
  teamId: string
  index: number
  /** 팀 하단 여유 드롭 영역 */
  trailing?: boolean
}

function DropSlotInner({ teamId, index, trailing }: DropSlotProps) {
  const { setNodeRef, isOver } = useDroppable({
    id: `${teamId}::${index}`,
  })

  return (
    <div
      ref={setNodeRef}
      className={`drop-slot${trailing ? ' drop-slot-trailing' : ''} ${isOver ? 'drop-slot-over' : ''}`}
      aria-hidden
    />
  )
}

export const DropSlot = memo(DropSlotInner)
