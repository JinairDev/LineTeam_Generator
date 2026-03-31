import { parseGoogleSheetUrl } from './googleSheetExport'

const GIS_SRC = 'https://accounts.google.com/gsi/client'
const SCOPE = [
  'https://www.googleapis.com/auth/spreadsheets.readonly',
  'https://www.googleapis.com/auth/userinfo.email',
].join(' ')

type TokenResponse = {
  access_token?: string
  error?: string
  error_description?: string
}

type GoogleUserInfo = { email?: string }
type SpreadsheetMeta = { sheets?: Array<{ properties?: { sheetId?: number; title?: string } }> }
type SheetValues = { values?: string[][] }

declare global {
  interface Window {
    google?: {
      accounts: {
        oauth2: {
          initTokenClient: (config: {
            client_id: string
            scope: string
            callback: (response: TokenResponse) => void
            error_callback?: (error: unknown) => void
          }) => {
            requestAccessToken: (options?: { prompt?: string }) => void
          }
        }
      }
    }
  }
}

function toCsv(rows: string[][]): string {
  return rows
    .map((row) =>
      row
        .map((cell) => {
          const text = cell ?? ''
          if (text.includes('"') || text.includes(',') || text.includes('\n')) {
            return `"${text.replace(/"/g, '""')}"`
          }
          return text
        })
        .join(',')
    )
    .join('\n')
}

async function loadGis(): Promise<void> {
  if (window.google?.accounts?.oauth2) return
  const existing = document.querySelector(`script[src="${GIS_SRC}"]`) as HTMLScriptElement | null
  if (existing && window.google?.accounts?.oauth2) return
  await new Promise<void>((resolve, reject) => {
    const script = existing ?? document.createElement('script')
    script.src = GIS_SRC
    script.async = true
    script.defer = true
    script.onload = () => resolve()
    script.onerror = () => reject(new Error('Google 인증 스크립트를 불러오지 못했습니다.'))
    if (!existing) document.head.appendChild(script)
  })
}

async function selectGoogleAccessToken(): Promise<string> {
  const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID?.trim()
  if (!clientId) {
    throw new Error('VITE_GOOGLE_CLIENT_ID가 설정되지 않았습니다.')
  }
  await loadGis()
  const oauth2 = window.google?.accounts?.oauth2
  if (!oauth2) throw new Error('Google OAuth를 초기화하지 못했습니다.')

  return new Promise<string>((resolve, reject) => {
    const client = oauth2.initTokenClient({
      client_id: clientId,
      scope: SCOPE,
      callback: (resp) => {
        if (resp.access_token) {
          resolve(resp.access_token)
          return
        }
        if (resp.error === 'access_denied') {
          reject(new Error('계정 선택 또는 권한 동의가 취소되었습니다.'))
          return
        }
        reject(new Error(resp.error_description || resp.error || 'Google 토큰 발급에 실패했습니다.'))
      },
      error_callback: () => reject(new Error('Google OAuth 팝업이 차단되었거나 실패했습니다.')),
    })
    // 다중 계정일 때 명시적으로 선택하게 함
    client.requestAccessToken({ prompt: 'select_account' })
  })
}

async function fetchJson<T>(url: string, token: string): Promise<T> {
  const res = await fetch(url, { headers: { Authorization: `Bearer ${token}` } })
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}`)
  }
  return (await res.json()) as T
}

async function fetchGoogleUserEmail(token: string): Promise<string> {
  const info = await fetchJson<GoogleUserInfo>('https://www.googleapis.com/oauth2/v3/userinfo', token)
  return info.email ?? '알 수 없는 계정'
}

async function fetchSheetTitleByGid(id: string, gid: string | undefined, token: string): Promise<string> {
  const meta = await fetchJson<SpreadsheetMeta>(
    `https://sheets.googleapis.com/v4/spreadsheets/${encodeURIComponent(id)}?fields=sheets.properties(sheetId%2Ctitle)`,
    token
  )
  const sheets = meta.sheets ?? []
  if (sheets.length === 0) throw new Error('시트 정보가 없습니다.')
  if (!gid) return sheets[0].properties?.title ?? 'Sheet1'

  const match = sheets.find((s) => String(s.properties?.sheetId ?? '') === gid)
  if (!match?.properties?.title) throw new Error('요청한 시트 탭(gid)을 찾을 수 없습니다.')
  return match.properties.title
}

async function fetchSheetCsv(id: string, gid: string | undefined, token: string): Promise<string> {
  const title = await fetchSheetTitleByGid(id, gid, token)
  const range = encodeURIComponent(`'${title}'!A:ZZ`)
  const values = await fetchJson<SheetValues>(
    `https://sheets.googleapis.com/v4/spreadsheets/${encodeURIComponent(id)}/values/${range}?majorDimension=ROWS`,
    token
  )
  const rows = values.values ?? []
  return toCsv(rows)
}

export async function fetchGoogleSheetCsvWithSelectedAccount(
  spreadsheetUrlOrId: string
): Promise<{ email: string; csv: string }> {
  const parsed = parseGoogleSheetUrl(spreadsheetUrlOrId)
  if (!parsed) throw new Error('Google 스프레드시트 링크 또는 ID 형식이 올바르지 않습니다.')
  const token = await selectGoogleAccessToken()
  const email = await fetchGoogleUserEmail(token)
  const csv = await fetchSheetCsv(parsed.id, parsed.gid, token)
  return { email, csv }
}
