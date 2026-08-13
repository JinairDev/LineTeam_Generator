import type { CrewMember, LineTeam } from './types'
import { extractFromToken, type FromToken } from './memberMetaBadges'
import { isTP, isTS } from './rankToken'

export interface TpTsQualViolation {
  teamId: string
  tpName: string
  tpFrom: FromToken
  tsName: string
  tsFrom: FromToken | string
  message: string
}

/** 백엔드 canBeTSInTeamWithTP와 동일: TP=LJ → TS 모두, TP=BX/RS → TS는 LJ만 */
export function canBeTSInTeamWithTP(ts: CrewMember, tp: CrewMember | undefined): boolean {
  if (!tp) return true
  const tpFrom = extractFromToken(tp.fromColumn)
  const tsFrom = extractFromToken(ts.fromColumn)
  if (!tpFrom || !tsFrom) return true
  if (tpFrom === 'LJ') return true
  if (tpFrom === 'BX' || tpFrom === 'RS') return tsFrom === 'LJ'
  return true
}

export function findTpTsQualViolations(teams: LineTeam[]): TpTsQualViolation[] {
  const out: TpTsQualViolation[] = []
  for (const team of teams) {
    if (!team?.members?.length) continue
    const tp = team.members.find(isTP)
    if (!tp) continue
    const tpFrom = extractFromToken(tp.fromColumn)
    if (tpFrom !== 'BX' && tpFrom !== 'RS') continue
    for (const m of team.members) {
      if (!isTS(m) || isTP(m)) continue
      if (canBeTSInTeamWithTP(m, tp)) continue
      const tsFrom = extractFromToken(m.fromColumn) ?? (m.fromColumn?.trim() || '-')
      const tpLabel = tp.name?.trim() || tp.employeeId
      const tsLabel = m.name?.trim() || m.employeeId
      out.push({
        teamId: team.teamId,
        tpName: tpLabel,
        tpFrom,
        tsName: tsLabel,
        tsFrom,
        message: `${team.teamId}: TP ${tpLabel}(${tpFrom}) + TS ${tsLabel}(${tsFrom}) — TP가 BX/RS이면 TS는 LJ만 가능`,
      })
    }
  }
  return out
}
