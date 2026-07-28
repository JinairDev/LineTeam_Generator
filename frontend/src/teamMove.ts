import type { CrewMember, LineTeam } from './types'
import { isTP } from './rankToken'

export function findTpInTeam(team: LineTeam): CrewMember | undefined {
  return team.members.find(isTP)
}

/** 드래그 중 TP를 이미 TP가 있는 팀에 놓으면 교체 */
export function getTeamMoveDropHint(
  teams: LineTeam[],
  dragging: CrewMember | null,
  overTeamId: string | null
): 'swap' | 'ok' | null {
  if (!dragging || !overTeamId) return null
  if (!isTP(dragging)) return 'ok'
  const team = teams.find((t) => t.teamId === overTeamId)
  if (!team) return null
  const existingTp = findTpInTeam(team)
  if (!existingTp || existingTp.employeeId === dragging.employeeId) return 'ok'
  return 'swap'
}

function cloneTeams(teams: LineTeam[]): LineTeam[] {
  return teams.map((t) => ({
    ...t,
    members: [...t.members],
  }))
}

/** 팀당 TP 1명: 대상 팀에 TP가 있으면 자리 교체 (member는 이미 from 팀에서 제거된 상태) */
function trySwapTpAfterRemove(
  teams: LineTeam[],
  incoming: CrewMember,
  fromTeamId: string,
  toTeamId: string
): LineTeam[] | null {
  if (!isTP(incoming) || fromTeamId === toTeamId) return null

  const nextTeams = cloneTeams(teams)
  const targetTeam = nextTeams.find((t) => t.teamId === toTeamId)
  if (!targetTeam) return null

  const existingTp = findTpInTeam(targetTeam)
  if (!existingTp || existingTp.employeeId === incoming.employeeId) return null

  const tpSlot = targetTeam.members.findIndex((m) => m.employeeId === existingTp.employeeId)
  targetTeam.members.splice(tpSlot, 1)
  targetTeam.members.splice(tpSlot, 0, incoming)

  const fromTeam = nextTeams.find((t) => t.teamId === fromTeamId)
  if (!fromTeam) return null
  fromTeam.members.unshift(existingTp)

  return nextTeams
}

export function applyTeamMove(
  teams: LineTeam[],
  employeeId: string,
  fromTeamId: string,
  toTeamId: string,
  toIndex?: number
): LineTeam[] {
  if (fromTeamId === toTeamId) {
    const nextTeams = cloneTeams(teams)
    const team = nextTeams.find((t) => t.teamId === fromTeamId)
    if (!team) return teams

    const fromIndex = team.members.findIndex((m) => m.employeeId === employeeId)
    if (fromIndex < 0) return teams

    const [removed] = team.members.splice(fromIndex, 1)
    let insertAt = toIndex !== undefined && toIndex >= 0 ? toIndex : team.members.length
    if (insertAt > team.members.length) insertAt = team.members.length
    if (fromIndex < insertAt) insertAt--
    team.members.splice(insertAt, 0, removed)
    return nextTeams
  }

  const nextTeams = cloneTeams(teams)
  let member: CrewMember | null = null
  let fromIndex = -1

  for (const team of nextTeams) {
    if (team.teamId !== fromTeamId) continue
    const idx = team.members.findIndex((m) => m.employeeId === employeeId)
    if (idx < 0) break
    fromIndex = idx
    ;[member] = team.members.splice(idx, 1)
    break
  }
  if (!member) return teams

  const swapped = trySwapTpAfterRemove(nextTeams, member, fromTeamId, toTeamId)
  if (swapped) return swapped

  for (const team of nextTeams) {
    if (team.teamId !== toTeamId) continue
    if (isTP(member) && findTpInTeam(team)) return teams
    let insertAt =
      isTP(member)
        ? 0
        : toIndex !== undefined && toIndex >= 0
          ? toIndex
          : team.members.length
    if (insertAt > team.members.length) insertAt = team.members.length
    if (fromTeamId === toTeamId && fromIndex >= 0 && insertAt > fromIndex) insertAt--
    team.members.splice(insertAt, 0, member)
    return nextTeams
  }
  return teams
}
