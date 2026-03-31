const SPREADSHEET_ID = /\/spreadsheets\/d\/([a-zA-Z0-9-_]+)/
const GID = /[?&#]gid=(\d+)/

export function parseGoogleSheetUrl(input: string): { id: string; gid?: string } | null {
  const t = input.trim()
  const m = t.match(SPREADSHEET_ID)
  let id: string | null = m ? m[1] : null
  if (!id && /^[a-zA-Z0-9-_]{10,}$/.test(t)) {
    id = t
  }
  if (!id) return null
  const gm = t.match(GID)
  const gid = gm ? gm[1] : undefined
  return { id, gid }
}

/** Vite 개발 서버 프록시 경로 → docs.google.com CSV 내보내기 */
export function devGoogleCsvProxyUrl(id: string, gid?: string): string {
  const q = new URLSearchParams({ format: 'csv' })
  if (gid) q.set('gid', gid)
  return `/google-sheets-csv/spreadsheets/d/${encodeURIComponent(id)}/export?${q.toString()}`
}
