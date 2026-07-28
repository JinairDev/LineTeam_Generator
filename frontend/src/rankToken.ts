import type { CrewMember } from './types'

const TS_OJT = 'TS OJT'
const RANK_TOKENS_AFTER_OJT = ['TS', 'TP', 'FP', 'YY'] as const

export function extractRankToken(positionCode?: string, grade?: string): string | null {
  const fromRank = extractFromText(positionCode)
  if (fromRank) return fromRank
  return extractFromText(grade)
}

function extractFromText(raw?: string): string | null {
  if (!raw?.trim()) return null
  const upper = raw.trim().toUpperCase().replace(/\s+/g, ' ')
  if (upper.includes('TS OJT') || upper.includes('TSOJT')) return TS_OJT
  for (const token of RANK_TOKENS_AFTER_OJT) {
    if (upper.includes(token)) return token
  }
  return null
}

export function isTP(member: CrewMember): boolean {
  const token = extractRankToken(member.positionCode, member.grade)
  return token === 'TP' || containsTokenFallback(member, 'TP')
}

export function isTS(member: CrewMember): boolean {
  const token = extractRankToken(member.positionCode, member.grade)
  return token === 'TS' || token === TS_OJT || containsTokenFallback(member, 'TS')
}

export function isTsOjt(member: CrewMember): boolean {
  return extractRankToken(member.positionCode, member.grade) === TS_OJT
}

function containsTokenFallback(member: CrewMember, token: string): boolean {
  if (member.positionCode?.toUpperCase().includes(token)) return true
  return Boolean(member.grade?.toUpperCase().includes(token))
}

export function isExcludedFromLineAssignment(member: CrewMember): boolean {
  const s = member.status?.trim() ?? ''
  return s === '차출' || s === '휴직'
}

export function isTpOrTs(member: CrewMember): boolean {
  return isTP(member) || isTS(member)
}

export function filterTpTsForPreAssign(crew: CrewMember[]): CrewMember[] {
  return crew.filter((m) => !isExcludedFromLineAssignment(m) && isTpOrTs(m))
}

export function countTpTs(crew: CrewMember[]): { tp: number; ts: number } {
  let tp = 0
  let ts = 0
  for (const m of crew) {
    if (isExcludedFromLineAssignment(m)) continue
    if (isTP(m)) tp++
    else if (isTS(m)) ts++
  }
  return { tp, ts }
}
