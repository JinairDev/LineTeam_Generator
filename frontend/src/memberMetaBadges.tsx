import type { CrewMember } from './types'
import { extractRankToken } from './rankToken'

export type FromToken = 'LJ' | 'BX' | 'RS'

export function extractFromToken(fromColumn?: string): FromToken | null {
  if (!fromColumn?.trim()) return null
  const upper = fromColumn.trim().toUpperCase()
  if (upper.includes('LJ')) return 'LJ'
  if (upper.includes('BX')) return 'BX'
  if (upper.includes('RS')) return 'RS'
  return null
}

export function fromBadgeClass(token: FromToken | null): string {
  if (!token) return 'meta-badge meta-badge--muted'
  return `meta-badge meta-badge-from meta-badge-from--${token.toLowerCase()}`
}

/** 사번 왼쪽용 FROM 뱃지 */
export function FromBadge({ member }: { member: CrewMember }) {
  const fromRaw = (member.fromColumn ?? '').trim()
  const fromToken = extractFromToken(fromRaw)
  const fromLabel = fromToken ?? (fromRaw || '-')
  return (
    <span className={`${fromBadgeClass(fromToken)} meta-badge-from-inline`} title="FROM">
      {fromLabel}
    </span>
  )
}

/** RANK · ANNC · Qualification 표시용 메타 행 (FROM은 사번 옆에 별도 표시) */
export function MemberMetaBadges({ member }: { member: CrewMember }) {
  const rankToken = extractRankToken(member.positionCode, member.grade)
  const rankLabel = member.positionCode?.trim() || rankToken || '-'

  const annc = member.annc?.trim() || '-'
  const qualification = (member.qualification ?? member.rank)?.trim() || '-'

  return (
    <div className="member-meta member-meta-badges">
      <span className="meta-badge meta-badge--muted" title="RANK">
        {rankLabel}
      </span>
      <span className="meta-badge meta-badge--muted" title="ANNC">
        {annc}
      </span>
      <span className="meta-badge meta-badge--muted" title="Qualification">
        {qualification}
      </span>
    </div>
  )
}
