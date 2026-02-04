import { memo } from 'react'
import type { CrewMember, LineTeam } from './types'
import { MemberCard } from './MemberCard'
import { DropSlot } from './DropSlot'

function isTP(m: CrewMember) {
  return m.positionCode?.includes('TP') || m.grade?.includes('TP')
}
function isTS(m: CrewMember) {
  return m.positionCode?.includes('TS') || m.grade?.includes('TS')
}

interface TeamColumnProps {
  team: LineTeam
  otherTeams: LineTeam[]
  onMoveMember: (
    employeeId: string,
    fromTeamId: string,
    toTeamId: string,
    toIndex?: number
  ) => void
  onOpenMoveMenu: (
    e: React.MouseEvent,
    employeeId: string,
    fromTeamId: string,
    otherTeams: LineTeam[]
  ) => void
}

function TeamColumnInner({ team, otherTeams, onMoveMember, onOpenMoveMenu }: TeamColumnProps) {
  const tpMembers = team.members.filter(isTP)
  const tsMembers = team.members.filter(isTS)
  const restMembers = team.members.filter((m) => !isTP(m) && !isTS(m))

  return (
    <div className="team-column card team-card">
      <div className="team-header">
        <span className="team-id">{team.teamId}</span>
        <span className="team-count">{team.members.length}명</span>
      </div>
      <div className="member-list">
        {tpMembers.length > 0 && (
          <div className="member-group">
            <div className="member-group-label">팀장</div>
            {tpMembers.map((member, i) => (
              <span key={member.employeeId} className="member-row">
                <DropSlot teamId={team.teamId} index={i} />
                <MemberCard
                  member={member}
                  teamId={team.teamId}
                  otherTeams={otherTeams}
                  onOpenMoveMenu={onOpenMoveMenu}
                />
              </span>
            ))}
          </div>
        )}
        {tsMembers.length > 0 && (
          <div className="member-group">
            <div className="member-group-label">사무장 – {tsMembers.length}명</div>
            {tsMembers.map((member, i) => (
              <span key={member.employeeId} className="member-row">
                <DropSlot
                  teamId={team.teamId}
                  index={tpMembers.length + i}
                />
                <MemberCard
                  member={member}
                  teamId={team.teamId}
                  otherTeams={otherTeams}
                  onOpenMoveMenu={onOpenMoveMenu}
                />
              </span>
            ))}
          </div>
        )}
        {restMembers.length > 0 && (
          <div className="member-group">
            <div className="member-group-label">팀원 – {restMembers.length}명</div>
            {restMembers.map((member, i) => (
              <span key={member.employeeId} className="member-row">
                <DropSlot
                  teamId={team.teamId}
                  index={tpMembers.length + tsMembers.length + i}
                />
                <MemberCard
                  member={member}
                  teamId={team.teamId}
                  otherTeams={otherTeams}
                  onOpenMoveMenu={onOpenMoveMenu}
                />
              </span>
            ))}
          </div>
        )}
        <DropSlot teamId={team.teamId} index={team.members.length} />
      </div>
    </div>
  )
}

export const TeamColumn = memo(TeamColumnInner)
