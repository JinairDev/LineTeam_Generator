import { memo } from 'react'
import { useDroppable } from '@dnd-kit/core'
import type { CrewMember, LineTeam } from './types'
import { MemberCard } from './MemberCard'
import { DropSlot } from './DropSlot'
import { formatTeamBalanceIssue, type TeamBalanceIssue } from './teamBalanceFlags'

function isTP(m: CrewMember) {
  return m.positionCode?.includes('TP') || m.grade?.includes('TP')
}
function isTS(m: CrewMember) {
  return m.positionCode?.includes('TS') || m.grade?.includes('TS')
}

interface TeamColumnProps {
  team: LineTeam
  otherTeams: LineTeam[]
  /** 사전 배정: 빈 팀 카드 전체를 드롭 영역으로 사용 */
  preAssignMode?: boolean
  isDropHighlighted?: boolean
  tpSwapTarget?: boolean
  /** 균등 분배 검증에서 이탈한 항목 */
  balanceIssues?: TeamBalanceIssue[]
  /** 편성 결과: 헤더 클릭 시 상단 버블로 접기 */
  collapsible?: boolean
  onCollapse?: () => void
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

function TeamColumnInner({
  team,
  otherTeams,
  onOpenMoveMenu,
  preAssignMode,
  isDropHighlighted,
  tpSwapTarget,
  balanceIssues,
  collapsible,
  onCollapse,
}: TeamColumnProps) {
  const isEmpty = team.members.length === 0
  const hasBalanceIssues = Boolean(balanceIssues?.length)
  const teamDropEnabled = Boolean(preAssignMode)
  const { setNodeRef: setTeamDropRef, isOver: isTeamOver } = useDroppable({
    id: team.teamId,
  })
  const showDropTarget = isTeamOver || Boolean(isDropHighlighted)

  const tpMembers = team.members.filter(isTP)
  const tsMembers = team.members.filter(isTS)
  const restMembers = team.members.filter((m) => !isTP(m) && !isTS(m))

  return (
    <div
      ref={setTeamDropRef}
      className={`team-column card team-card${showDropTarget ? ' drop-target' : ''}${tpSwapTarget ? ' tp-swap-target' : ''}${hasBalanceIssues ? ' team-column--balance-warn' : ''}${teamDropEnabled && isEmpty ? ' team-column-empty-preassign' : ''}${!teamDropEnabled ? ' team-column-droppable' : ''}`}
    >
      {collapsible ? (
        <button
          type="button"
          className="team-header team-header--toggle"
          onClick={onCollapse}
          title="접어서 위로 보내기"
        >
          <span className="team-header-main">
            <span className="team-collapse-chevron" aria-hidden>
              ▴
            </span>
            <span className="team-id">{team.teamId}</span>
          </span>
          <span className="team-count">{team.members.length}명</span>
        </button>
      ) : (
        <div className="team-header">
          <span className="team-id">{team.teamId}</span>
          <span className="team-count">{team.members.length}명</span>
        </div>
      )}
      {hasBalanceIssues && (
        <div className="team-balance-flags" role="note">
          {balanceIssues!.map((issue: TeamBalanceIssue) => (
            <span
              key={`${issue.key}-${issue.kind}`}
              className={`team-balance-flag team-balance-flag--${issue.kind}`}
              title={`${issue.label} 팀당 기준 ${issue.min}~${issue.max}명`}
            >
              {formatTeamBalanceIssue(issue)}
            </span>
          ))}
        </div>
      )}
      <div
        className={`member-list${teamDropEnabled && isEmpty ? ' member-list-empty-drop' : ''}${teamDropEnabled && !isEmpty ? ' member-list-pre-assign' : ''}`}
      >
        {teamDropEnabled && isEmpty && (
          <p className="empty-team-drop-hint">TP/TS를 여기에 놓으세요</p>
        )}
        {tpSwapTarget && (
          <p className="tp-swap-hint">TP 교체</p>
        )}
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
        {!teamDropEnabled && (
          <DropSlot teamId={team.teamId} index={team.members.length} trailing />
        )}
        {teamDropEnabled && !isEmpty && (
          <DropSlot teamId={team.teamId} index={team.members.length} trailing />
        )}
      </div>
    </div>
  )
}

export const TeamColumn = memo(TeamColumnInner)
