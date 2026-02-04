import { memo } from 'react'
import type { CrewMember } from './types'

interface MemberCardPreviewProps {
  member: CrewMember
}

function MemberCardPreviewInner({ member }: MemberCardPreviewProps) {
  const isTP = member.positionCode?.includes('TP') || member.grade?.includes('TP')
  const isTS = member.positionCode?.includes('TS') || member.grade?.includes('TS')

  return (
    <div
      className={`member-card member-card-floating ${isTP ? 'tp' : ''} ${isTS ? 'ts' : ''}`}
      role="presentation"
    >
      <div className="member-card-floating-badge">이동 중</div>
      <div className="member-id-grade">
        {member.employeeId} · {member.gender || '-'} · {member.grade || '-'}
      </div>
      <div className="member-name member-name-floating">{member.name || '-'}</div>
      <div className="member-meta">
        {[member.positionCode, member.rank].filter(Boolean).join(' · ')}
      </div>
    </div>
  )
}

export const MemberCardPreview = memo(MemberCardPreviewInner)
