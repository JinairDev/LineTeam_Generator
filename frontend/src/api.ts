import type { CrewMember, LineTeam } from './types'

const API = '/api'

export async function uploadExcel(file: File): Promise<CrewMember[]> {
  const form = new FormData()
  form.append('file', file)
  const res = await fetch(`${API}/upload`, {
    method: 'POST',
    body: form,
  })
  if (!res.ok) throw new Error('엑셀 업로드 실패')
  return res.json()
}

export interface TeamCountByBase {
  [base: string]: number  // e.g. { SEL: 5, PUS: 3 }
}

export async function assignTeams(
  crew: CrewMember[],
  teamCountByBase?: TeamCountByBase
): Promise<LineTeam[]> {
  const res = await fetch(`${API}/assign`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      crew,
      ...(teamCountByBase && Object.keys(teamCountByBase).length > 0 && { teamCountByBase }),
    }),
  })
  if (!res.ok) throw new Error('편성 실패')
  return res.json()
}

export async function moveMember(
  teams: LineTeam[],
  employeeId: string,
  fromTeamId: string,
  toTeamId: string,
  toIndex?: number
): Promise<LineTeam[]> {
  const res = await fetch(`${API}/teams/move`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      teams,
      employeeId,
      fromTeamId,
      toTeamId,
      ...(toIndex !== undefined && toIndex >= 0 && { toIndex }),
    }),
  })
  if (!res.ok) throw new Error('이동 실패')
  return res.json()
}

export async function exportExcel(teams: LineTeam[]): Promise<Blob> {
  const res = await fetch(`${API}/export`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(teams),
  })
  if (!res.ok) throw new Error('엑셀 추출 실패')
  return res.blob()
}
