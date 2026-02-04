import { memo } from 'react'
import { useDraggable } from '@dnd-kit/core'
import type { CrewMember, LineTeam } from './types'

interface MemberCardProps {
  member: CrewMember
  teamId: string
  otherTeams: LineTeam[]
  onOpenMoveMenu: (
    e: React.MouseEvent,
    employeeId: string,
    fromTeamId: string,
    otherTeams: LineTeam[]
  ) => void
}

function MemberCardInner({
  member,
  teamId,
  otherTeams,
  onOpenMoveMenu,
}: MemberCardProps) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: member.employeeId,
    data: { member, teamId },
  })

  const isTP = member.positionCode?.includes('TP') || member.grade?.includes('TP')
  const isTS = member.positionCode?.includes('TS') || member.grade?.includes('TS')

  const handleContextMenu = (e: React.MouseEvent) => {
    if (otherTeams.length === 0) return
    e.preventDefault()
    onOpenMoveMenu(e, member.employeeId, teamId, otherTeams)
  }

  return (
    <div
      ref={setNodeRef}
      className={`member-card ${isDragging ? 'dragging' : ''} ${isTP ? 'tp' : ''} ${isTS ? 'ts' : ''}`}
      onContextMenu={handleContextMenu}
    >
      <div className="member-card-content">
        <div className="member-card-body" {...listeners} {...attributes}>
          <div className="member-id-grade">
            {member.employeeId} · {member.gender || '-'} · {member.grade || '-'}
          </div>
          <div className="member-name">{member.name || '-'}</div>
          <div className="member-meta">
            {[member.positionCode, member.rank].filter(Boolean).join(' · ')}
          </div>
        </div>
      </div>
    </div>
  )
}

export const MemberCard = memo(MemberCardInner)
