import type { CrewMember, LineTeam } from './types'

const API = '/api'

async function parseErrorResponse(res: Response, defaultMsg: string): Promise<string> {
  try {
    const text = await res.text()
    if (!text) return defaultMsg
    const json = JSON.parse(text) as { message?: string }
    return typeof json?.message === 'string' && json.message.trim() ? json.message.trim() : defaultMsg
  } catch {
    return defaultMsg
  }
}

async function handleResponse<T>(res: Response, defaultError: string, parse: () => Promise<T>): Promise<T> {
  if (!res.ok) {
    const message = await parseErrorResponse(res, defaultError)
    throw new Error(message)
  }
  try {
    return await parse()
  } catch (e) {
    if (e instanceof Error && e.message.startsWith('서버에 연결할 수 없습니다')) throw e
    throw new Error(defaultError + ' (응답 형식 오류)')
  }
}

export async function uploadExcel(file: File): Promise<CrewMember[]> {
  const form = new FormData()
  form.append('file', file)
  let res: Response
  try {
    res = await fetch(`${API}/upload`, { method: 'POST', body: form })
  } catch (e) {
    throw new Error('서버에 연결할 수 없습니다. 네트워크를 확인해 주세요.')
  }
  return handleResponse(res, '엑셀 업로드 실패', () => res.json())
}

export interface TeamCountByBase {
  [base: string]: number  // e.g. { SEL: 5, PUS: 3 }
}

export async function assignTeams(
  crew: CrewMember[],
  teamCountByBase?: TeamCountByBase
): Promise<LineTeam[]> {
  let res: Response
  try {
    res = await fetch(`${API}/assign`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        crew,
        ...(teamCountByBase && Object.keys(teamCountByBase).length > 0 && { teamCountByBase }),
      }),
    })
  } catch (e) {
    throw new Error('서버에 연결할 수 없습니다. 네트워크를 확인해 주세요.')
  }
  return handleResponse(res, '편성 실패', () => res.json())
}

export async function moveMember(
  teams: LineTeam[],
  employeeId: string,
  fromTeamId: string,
  toTeamId: string,
  toIndex?: number
): Promise<LineTeam[]> {
  let res: Response
  try {
    res = await fetch(`${API}/teams/move`, {
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
  } catch (e) {
    throw new Error('서버에 연결할 수 없습니다. 네트워크를 확인해 주세요.')
  }
  return handleResponse(res, '이동 실패', () => res.json())
}

export async function exportExcel(teams: LineTeam[]): Promise<Blob> {
  let res: Response
  try {
    res = await fetch(`${API}/export`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(teams),
    })
  } catch (e) {
    throw new Error('서버에 연결할 수 없습니다. 네트워크를 확인해 주세요.')
  }
  return handleResponse(res, '엑셀 추출 실패', () => res.blob())
}
