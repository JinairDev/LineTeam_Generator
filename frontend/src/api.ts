import type { CrewMember, LineTeam } from './types'
import { devGoogleCsvProxyUrl, parseGoogleSheetUrl } from './googleSheetExport'

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

export async function uploadCrewCsvText(csv: string): Promise<CrewMember[]> {
  let res: Response
  try {
    res = await fetch(`${API}/upload-csv`, {
      method: 'POST',
      headers: { 'Content-Type': 'text/plain;charset=utf-8' },
      body: csv,
    })
  } catch (e) {
    throw new Error('서버에 연결할 수 없습니다. 네트워크를 확인해 주세요.')
  }
  return handleResponse(res, 'CSV 처리 실패', () => res.json())
}

export async function uploadFromGoogleSpreadsheet(spreadsheetUrl: string): Promise<CrewMember[]> {
  if (import.meta.env.DEV) {
    const parsed = parseGoogleSheetUrl(spreadsheetUrl)
    if (parsed) {
      try {
        const proxyUrl = devGoogleCsvProxyUrl(parsed.id, parsed.gid)
        const csvRes = await fetch(proxyUrl)
        if (csvRes.ok) {
          const csv = await csvRes.text()
          return uploadCrewCsvText(csv)
        }
      } catch {
        // 백엔드 경로로 폴백
      }
    }
  }

  let res: Response
  try {
    res = await fetch(`${API}/upload-from-google`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ spreadsheetUrl }),
    })
  } catch (e) {
    throw new Error('서버에 연결할 수 없습니다. 네트워크를 확인해 주세요.')
  }
  return handleResponse(res, 'Google 스프레드시트 가져오기 실패', () => res.json())
}

export interface TeamCountByBase {
  [base: string]: number  // e.g. { SEL: 5, PUS: 3 }
}

/** 재편성 시 TP/TS 고정 모드 */
export type PinMode = 'TP_FIXED' | 'TS_FIXED' | 'BOTH_FIXED'

export async function assignTeams(
  crew: CrewMember[],
  teamCountByBase?: TeamCountByBase,
  options?: { pinMode?: PinMode; previousTeams?: LineTeam[] }
): Promise<LineTeam[]> {
  let res: Response
  try {
    res = await fetch(`${API}/assign`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        crew,
        ...(teamCountByBase && Object.keys(teamCountByBase).length > 0 && { teamCountByBase }),
        ...(options?.pinMode && options?.previousTeams?.length
          ? { pinMode: options.pinMode, previousTeams: options.previousTeams }
          : {}),
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
