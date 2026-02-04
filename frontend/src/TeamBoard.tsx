import { useState, useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
import {
  DndContext,
  DragEndEvent,
  DragOverlay,
  PointerSensor,
  useSensor,
  useSensors,
  pointerWithin,
} from '@dnd-kit/core'
import type { CrewMember, LineTeam } from './types'
import { TeamColumn } from './TeamColumn'
import { MemberCardPreview } from './MemberCardPreview'
import { centerUnderCursor } from './dndModifiers'

interface TeamBoardProps {
  teams: LineTeam[]
  onMoveMember: (
    employeeId: string,
    fromTeamId: string,
    toTeamId: string,
    toIndex?: number
  ) => void
}

type MoveContextMenu = {
  clientX: number
  clientY: number
  employeeId: string
  fromTeamId: string
  otherTeams: LineTeam[]
}

export function TeamBoard({ teams, onMoveMember }: TeamBoardProps) {
  const [moveMenu, setMoveMenu] = useState<MoveContextMenu | null>(null)
  const menuRef = useRef<HTMLDivElement>(null)

  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: { distance: 2 },
    })
  )

  const openMoveMenu = (
    e: React.MouseEvent,
    employeeId: string,
    fromTeamId: string,
    otherTeams: LineTeam[]
  ) => {
    e.preventDefault()
    e.stopPropagation()
    setMoveMenu({
      clientX: e.clientX,
      clientY: e.clientY,
      employeeId,
      fromTeamId,
      otherTeams,
    })
  }

  useEffect(() => {
    if (!moveMenu) return
    const close = (e: MouseEvent) => {
      if (menuRef.current?.contains(e.target as Node)) return
      setMoveMenu(null)
    }
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [moveMenu])

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event
    if (!over) return
    const payload = active.data.current as { member: CrewMember; teamId: string } | undefined
    if (!payload) return
    const overStr = String(over.id)
    let toTeamId: string
    let toIndex: number | undefined
    if (overStr.includes('::')) {
      const [teamId, indexStr] = overStr.split('::')
      toTeamId = teamId
      const n = parseInt(indexStr, 10)
      if (!Number.isNaN(n)) toIndex = n
    } else {
      toTeamId = overStr
      toIndex = undefined
    }
    onMoveMember(payload.member.employeeId, payload.teamId, toTeamId, toIndex)
  }

  const overlayContent = (active: { id: string; data: { current: unknown } } | null) => {
    const payload = active?.data.current as { member: CrewMember; teamId: string } | undefined
    if (!payload) return null
    return <MemberCardPreview member={payload.member} />
  }

  return (
    <DndContext
      sensors={sensors}
      onDragEnd={handleDragEnd}
      collisionDetection={pointerWithin}
    >
      <div className="team-board">
        {teams.map((team) => (
          <TeamColumn
            key={team.teamId}
            team={team}
            otherTeams={teams.filter((t) => t.teamId !== team.teamId)}
            onMoveMember={onMoveMember}
            onOpenMoveMenu={openMoveMenu}
          />
        ))}
      </div>
      {moveMenu && createPortal(
        <div
          ref={menuRef}
          className="member-move-context-menu"
          style={{ left: moveMenu.clientX, top: moveMenu.clientY }}
        >
          <div className="member-move-dropdown-title">다른 팀으로 이동</div>
          {moveMenu.otherTeams.map((t) => (
            <button
              key={t.teamId}
              type="button"
              className="member-move-dropdown-item"
              onClick={() => {
                onMoveMember(moveMenu.employeeId, moveMenu.fromTeamId, t.teamId)
                setMoveMenu(null)
              }}
            >
              {t.teamId} <span className="member-move-dropdown-count">({t.members.length}명)</span>
            </button>
          ))}
        </div>,
        document.body
      )}
      {createPortal(
        <DragOverlay
          dropAnimation={null}
          modifiers={[centerUnderCursor]}
          zIndex={10000}
        >
          {(active) => overlayContent(active)}
        </DragOverlay>,
        document.body
      )}
    </DndContext>
  )
}
