import type { CrewMember, LineTeam } from './types'
import { isTP } from './rankToken'
import { findTpInTeam } from './teamMove'

export const PRE_ASSIGN_POOL_ID = '__pool__'

function cloneTeams(teams: LineTeam[]): LineTeam[] {
  return teams.map((t) => ({
    ...t,
    members: [...t.members],
  }))
}

/** 팀당 TP 1명: 대상 팀에 TP가 있으면 자리 교체 */
function trySwapTp(
  teams: LineTeam[],
  pool: CrewMember[],
  incoming: CrewMember,
  fromTeamId: string,
  toTeamId: string
): { teams: LineTeam[]; pool: CrewMember[] } | null {
  if (!isTP(incoming) || toTeamId === PRE_ASSIGN_POOL_ID) return null

  const nextTeams = cloneTeams(teams)
  const nextPool = [...pool]
  const targetTeam = nextTeams.find((t) => t.teamId === toTeamId)
  if (!targetTeam) return null

  const existingTp = findTpInTeam(targetTeam)
  if (!existingTp || existingTp.employeeId === incoming.employeeId) return null

  if (fromTeamId === PRE_ASSIGN_POOL_ID) {
    const poolIdx = nextPool.findIndex((m) => m.employeeId === incoming.employeeId)
    if (poolIdx < 0) return null
    nextPool.splice(poolIdx, 1)
  } else {
    const fromTeam = nextTeams.find((t) => t.teamId === fromTeamId)
    if (!fromTeam) return null
    const fromIdx = fromTeam.members.findIndex((m) => m.employeeId === incoming.employeeId)
    if (fromIdx < 0) return null
    fromTeam.members.splice(fromIdx, 1)
  }

  const tpSlot = targetTeam.members.findIndex((m) => m.employeeId === existingTp.employeeId)
  targetTeam.members.splice(tpSlot, 1)
  targetTeam.members.splice(tpSlot, 0, incoming)

  if (fromTeamId === PRE_ASSIGN_POOL_ID) {
    nextPool.push(existingTp)
  } else {
    const fromTeam = nextTeams.find((t) => t.teamId === fromTeamId)
    if (!fromTeam) return null
    fromTeam.members.unshift(existingTp)
  }

  return { teams: nextTeams, pool: nextPool }
}

function removeMember(
  teams: LineTeam[],
  pool: CrewMember[],
  employeeId: string,
  fromTeamId: string
): { teams: LineTeam[]; pool: CrewMember[]; member: CrewMember | null; fromIndex: number } {
  const nextTeams = cloneTeams(teams)
  const nextPool = [...pool]

  if (fromTeamId === PRE_ASSIGN_POOL_ID) {
    const idx = nextPool.findIndex((m) => m.employeeId === employeeId)
    if (idx < 0) return { teams: nextTeams, pool: nextPool, member: null, fromIndex: -1 }
    const [member] = nextPool.splice(idx, 1)
    return { teams: nextTeams, pool: nextPool, member, fromIndex: idx }
  }

  for (const team of nextTeams) {
    if (team.teamId !== fromTeamId) continue
    const idx = team.members.findIndex((m) => m.employeeId === employeeId)
    if (idx < 0) break
    const [member] = team.members.splice(idx, 1)
    return { teams: nextTeams, pool: nextPool, member, fromIndex: idx }
  }
  return { teams: nextTeams, pool: nextPool, member: null, fromIndex: -1 }
}

function insertMember(
  teams: LineTeam[],
  pool: CrewMember[],
  member: CrewMember,
  toTeamId: string,
  toIndex?: number
): { teams: LineTeam[]; pool: CrewMember[] } {
  if (toTeamId === PRE_ASSIGN_POOL_ID) {
    return { teams, pool: [...pool, member] }
  }

  const nextTeams = cloneTeams(teams)
  for (const team of nextTeams) {
    if (team.teamId !== toTeamId) continue
    if (isTP(member) && findTpInTeam(team)) {
      return { teams, pool }
    }
    let insertAt =
      toIndex !== undefined && toIndex >= 0 ? toIndex : team.members.length
    if (insertAt > team.members.length) insertAt = team.members.length
    team.members.splice(insertAt, 0, member)
    return { teams: nextTeams, pool }
  }
  // 대상 팀을 찾지 못하면 원래 위치로 복구
  if (toTeamId === PRE_ASSIGN_POOL_ID) {
    return { teams, pool: [...pool, member] }
  }
  return { teams, pool }
}

export function applyPreAssignMove(
  teams: LineTeam[],
  pool: CrewMember[],
  employeeId: string,
  fromTeamId: string,
  toTeamId: string,
  toIndex?: number
): { teams: LineTeam[]; pool: CrewMember[] } {
  if (fromTeamId === toTeamId && fromTeamId !== PRE_ASSIGN_POOL_ID) {
    const nextTeams = cloneTeams(teams)
    const team = nextTeams.find((t) => t.teamId === fromTeamId)
    if (!team) return { teams, pool }

    const fromIndex = team.members.findIndex((m) => m.employeeId === employeeId)
    if (fromIndex < 0) return { teams, pool }

    const [removed] = team.members.splice(fromIndex, 1)
    let insertAt =
      toIndex !== undefined && toIndex >= 0 ? toIndex : team.members.length
    if (insertAt > team.members.length) insertAt = team.members.length
    if (fromIndex < insertAt) insertAt--
    team.members.splice(insertAt, 0, removed)
    return { teams: nextTeams, pool }
  }

  const incoming =
    fromTeamId === PRE_ASSIGN_POOL_ID
      ? pool.find((m) => m.employeeId === employeeId) ?? null
      : teams
          .find((t) => t.teamId === fromTeamId)
          ?.members.find((m) => m.employeeId === employeeId) ?? null

  if (incoming && isTP(incoming) && toTeamId !== PRE_ASSIGN_POOL_ID) {
    const swapped = trySwapTp(teams, pool, incoming, fromTeamId, toTeamId)
    if (swapped) return swapped
  }

  const { teams: afterRemove, pool: poolAfterRemove, member } = removeMember(
    teams,
    pool,
    employeeId,
    fromTeamId
  )
  if (!member) return { teams, pool }

  return insertMember(afterRemove, poolAfterRemove, member, toTeamId, toIndex)
}

export function resolvePreAssignDropTarget(overId: string): {
  toTeamId: string
  toIndex?: number
} {
  if (overId === PRE_ASSIGN_POOL_ID || overId.startsWith(`${PRE_ASSIGN_POOL_ID}::`)) {
    return { toTeamId: PRE_ASSIGN_POOL_ID }
  }
  if (overId.includes('::')) {
    const [teamId, indexStr] = overId.split('::')
    const n = parseInt(indexStr, 10)
    return {
      toTeamId: teamId,
      toIndex: Number.isNaN(n) ? undefined : n,
    }
  }
  return { toTeamId: overId }
}

/** 드래그 중 TP를 이미 TP가 있는 팀에 놓으면 교체, 그 외 TP 중복 배치는 불가 */
export function getPreAssignDropHint(
  teams: LineTeam[],
  dragging: CrewMember | null,
  overTeamId: string | null
): 'swap' | 'ok' | 'blocked' | null {
  if (!dragging || !overTeamId || overTeamId === PRE_ASSIGN_POOL_ID) return null
  if (!isTP(dragging)) return 'ok'
  const team = teams.find((t) => t.teamId === overTeamId)
  if (!team) return null
  const existingTp = findTpInTeam(team)
  if (!existingTp) return 'ok'
  if (existingTp.employeeId === dragging.employeeId) return null
  return 'swap'
}
