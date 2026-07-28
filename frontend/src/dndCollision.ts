import { closestCenter, pointerWithin } from '@dnd-kit/core'
import type { CollisionDetection } from '@dnd-kit/core'

/**
 * 팀 카드·풀(팀 ID) 우선, 슬롯(teamId::index)은 삽입 위치 지정용.
 * 포인터가 아무 영역에도 안 맞으면 closestCenter로 폴백.
 */
export const boardCollisionDetection: CollisionDetection = (args) => {
  const pointerHits = pointerWithin(args).filter((c) => c.id !== args.active.id)
  if (pointerHits.length > 0) {
    const slots = pointerHits.filter((c) => String(c.id).includes('::'))
    const zones = pointerHits.filter((c) => !String(c.id).includes('::'))
    if (zones.length > 0) return zones
    if (slots.length > 0) return slots
    return pointerHits
  }
  return closestCenter(args).filter((c) => c.id !== args.active.id)
}

export function resolveBoardDropTarget(overId: string): {
  toTeamId: string
  toIndex: number | undefined
} {
  if (overId.includes('::')) {
    const [teamId, indexStr] = overId.split('::')
    const n = parseInt(indexStr, 10)
    return {
      toTeamId: teamId,
      toIndex: Number.isNaN(n) ? undefined : n,
    }
  }
  return { toTeamId: overId, toIndex: undefined }
}
