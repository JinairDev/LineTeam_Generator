import { memo } from 'react'
import type { CrewMember } from './types'

interface MemberCardPreviewProps {
  member: CrewMember
}

function MemberCardPreviewInner({ member }: MemberCardPreviewProps) {
  const isTP = member.positionCode?.includes('TP') || member.grade?.includes('TP')
  const isTS = member.positionCode?.includes('TS') || member.grade?.includes('TS')
  const isLeaveScheduled = member.status?.trim() === '휴직예정'

  return (
    <div
      className={`member-card member-card-floating ${isTP ? 'tp' : ''} ${isTS ? 'ts' : ''} ${isLeaveScheduled ? 'member-card-leave' : ''}`}
      role="presentation"
    >
      <div className="member-card-floating-badge">이동 중</div>
      {isLeaveScheduled && <span className="member-card-status-badge">*휴직예정</span>}
      <div className="member-id-grade">
        {member.employeeId} · {member.gender || '-'} · {member.grade || '-'}
      </div>
      <div className="member-name member-name-floating">{member.name || '-'}</div>
      <div className="member-meta">
        <span>{(member.fromColumn ?? member.rank)?.trim() || '-'}</span>
        <span className="member-meta-sep"> · </span>
        <span>{member.positionCode || '-'}</span>
        <span className="member-meta-sep"> · </span>
        <span>{member.annc?.trim() || '-'}</span>
        <span className="member-meta-sep"> · </span>
        <span>{(member.qualification ?? member.rank)?.trim() || '-'}</span>
      </div>
    </div>
  )
}

export const MemberCardPreview = memo(MemberCardPreviewInner)
