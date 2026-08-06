import { memo } from 'react'
import type { CrewMember } from './types'
import { memberHasLeaveStyleStatus, memberStatusBadgeText } from './memberStatusUi'
import { MemberMetaBadges, FromBadge } from './memberMetaBadges'
import { nameBgStyle } from './nameBgColor'

interface MemberCardPreviewProps {
  member: CrewMember
}

function MemberCardPreviewInner({ member }: MemberCardPreviewProps) {
  const isTP = member.positionCode?.includes('TP') || member.grade?.includes('TP')
  const isTS = member.positionCode?.includes('TS') || member.grade?.includes('TS')
  const leaveStyle = memberHasLeaveStyleStatus(member.status)
  const statusBadge = memberStatusBadgeText(member.status)

  return (
    <div
      className={`member-card member-card-floating ${isTP ? 'tp' : ''} ${isTS ? 'ts' : ''} ${leaveStyle ? 'member-card-leave' : ''}`}
      role="presentation"
    >
      <div className="member-card-floating-badge">이동 중</div>
      {statusBadge && <span className="member-card-status-badge">{statusBadge}</span>}
      <div className="member-id-grade">
        <FromBadge member={member} />
        <span>
          {member.employeeId} · {member.gender || '-'} · {member.grade || '-'}
        </span>
      </div>
      <div
        className="member-name member-name-floating"
        style={nameBgStyle(member.nameBgColor)}
      >
        {member.name || '-'}
      </div>
      <MemberMetaBadges member={member} />
    </div>
  )
}

export const MemberCardPreview = memo(MemberCardPreviewInner)
