import { useState, useCallback } from 'react'
import type { LineTeam } from './types'
import {
  uploadExcel,
  uploadCrewCsvText,
  uploadFromGoogleSpreadsheet,
  assignTeams,
  moveMember,
  exportExcel,
  type CrewUploadResponse,
  type ExportMemberSort,
  type PinMode,
} from './api'
import { fetchGoogleSheetCsvWithSelectedAccount } from './googleSheetsOAuth'
import { TeamBoard } from './TeamBoard'
import './App.css'

type Step = 'upload' | 'review' | 'assigned'

function App() {
  const hasGoogleClientId = Boolean(import.meta.env.VITE_GOOGLE_CLIENT_ID?.trim())
  const [step, setStep] = useState<Step>('upload')
  const [crew, setCrew] = useState<import('./types').CrewMember[]>([])
  const [teams, setTeams] = useState<LineTeam[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [googleUrl, setGoogleUrl] = useState('')
  const [googleAccountEmail, setGoogleAccountEmail] = useState<string | null>(null)
  const [uploadedExcelName, setUploadedExcelName] = useState<string | null>(null)
  const [csvPasteText, setCsvPasteText] = useState('')
  const [selectedPinMode, setSelectedPinMode] = useState<PinMode | null>(null)
  const [exportMemberSort, setExportMemberSort] = useState<ExportMemberSort>('employeeId')
  const [importColumnHeaders, setImportColumnHeaders] = useState<string[]>([])

  const applyCrewAndAssign = useCallback(
    async (list: import('./types').CrewMember[], columnHeaders?: string[]) => {
      if (list.length === 0) return
      setCrew(list)
      if (columnHeaders !== undefined) setImportColumnHeaders(columnHeaders)
      const result = await assignTeams(list)
      setTeams(result)
      setStep('assigned')
    },
    []
  )

  const onFileSelect = useCallback(async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    setError(null)
    setLoading(true)
    try {
      const { crew: list, columnHeaders } = await uploadExcel(file)
      if (list.length === 0) {
        setLoading(false)
        return
      }
      setUploadedExcelName(file.name)
      setImportColumnHeaders(columnHeaders ?? [])
      setCrew(list)
      setTeams([])
      setStep('review')
    } catch (err) {
      setError(err instanceof Error ? err.message : '업로드 중 오류가 발생했습니다.')
    } finally {
      setLoading(false)
      e.target.value = ''
    }
  }, [])

  const onAssignFromReview = useCallback(async () => {
    if (crew.length === 0) {
      setError('편성할 승무원 데이터가 없습니다.')
      return
    }
    setError(null)
    setLoading(true)
    try {
      const result = await assignTeams(crew)
      setTeams(result)
      setStep('assigned')
    } catch (err) {
      setError(err instanceof Error ? err.message : '편성 중 오류가 발생했습니다.')
    } finally {
      setLoading(false)
    }
  }, [crew])

  const onBackToUpload = useCallback(() => {
    setStep('upload')
    setTeams([])
    setCrew([])
    setImportColumnHeaders([])
    setUploadedExcelName(null)
    setCsvPasteText('')
    setError(null)
  }, [])

  const onCsvPasteSubmit = useCallback(async () => {
    const text = csvPasteText.trim()
    if (!text) {
      setError('CSV 내용을 붙여넣어 주세요.')
      return
    }
    setError(null)
    setLoading(true)
    try {
      const { crew: list, columnHeaders } = await uploadCrewCsvText(text)
      if (list.length === 0) {
        setLoading(false)
        return
      }
      setUploadedExcelName('CSV 붙여넣기')
      setImportColumnHeaders(columnHeaders ?? [])
      setCrew(list)
      setTeams([])
      setStep('review')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'CSV 처리 중 오류가 발생했습니다.')
    } finally {
      setLoading(false)
    }
  }, [csvPasteText])

  const onGoogleImport = useCallback(async () => {
    const url = googleUrl.trim()
    if (!url) {
      setError('Google 스프레드시트 링크 또는 ID를 입력해 주세요.')
      return
    }
    setError(null)
    setLoading(true)
    try {
      let res: CrewUploadResponse
      if (hasGoogleClientId) {
        const { email, csv } = await fetchGoogleSheetCsvWithSelectedAccount(url)
        setGoogleAccountEmail(email)
        res = await uploadCrewCsvText(csv)
      } else {
        setGoogleAccountEmail(null)
        res = await uploadFromGoogleSpreadsheet(url)
      }
      if (res.crew.length === 0) {
        setLoading(false)
        return
      }
      await applyCrewAndAssign(res.crew, res.columnHeaders ?? [])
    } catch (err) {
      const message = err instanceof Error ? err.message : '가져오기 또는 편성 중 오류가 발생했습니다.'
      if (
        message.includes('HTTP 401') ||
        message.includes('HTTP 403') ||
        message.toLowerCase().includes('unauthorized') ||
        message.toLowerCase().includes('forbidden')
      ) {
        setError('해당 파일에 접근 권한이 없습니다.')
      } else {
        setError(message)
      }
    } finally {
      setLoading(false)
    }
  }, [googleUrl, applyCrewAndAssign, hasGoogleClientId])

  const onReassign = useCallback(async () => {
    if (crew.length === 0) return
    setSelectedPinMode(null)
    setError(null)
    setLoading(true)
    try {
      const result = await assignTeams(crew)
      setTeams(result)
    } catch (err) {
      setError(err instanceof Error ? err.message : '편성 중 오류가 발생했습니다.')
    } finally {
      setLoading(false)
    }
  }, [crew])

  const selectPinMode = useCallback((mode: PinMode) => {
    setSelectedPinMode((prev) => (prev === mode ? null : mode))
  }, [])

  const onApplyPinRegenerate = useCallback(async () => {
    if (crew.length === 0 || teams.length === 0 || !selectedPinMode) return
    setError(null)
    setLoading(true)
    try {
      const result = await assignTeams(crew, undefined, {
        pinMode: selectedPinMode,
        previousTeams: teams,
      })
      setTeams(result)
    } catch (err) {
      setError(err instanceof Error ? err.message : '편성 중 오류가 발생했습니다.')
    } finally {
      setLoading(false)
    }
  }, [crew, teams, selectedPinMode])

  const onMoveMember = useCallback(
    async (
      employeeId: string,
      fromTeamId: string,
      toTeamId: string,
      toIndex?: number
    ) => {
      setError(null)
      try {
        const next = await moveMember(
          teams,
          employeeId,
          fromTeamId,
          toTeamId,
          toIndex
        )
        setTeams(next)
      } catch (err) {
        setError(err instanceof Error ? err.message : '이동 중 오류가 발생했습니다.')
      }
    },
    [teams]
  )

  const onExport = useCallback(async () => {
    if (teams.length === 0) return
    setError(null)
    try {
      const blob = await exportExcel(teams, {
        sort: exportMemberSort,
        columnHeaders: importColumnHeaders.length > 0 ? importColumnHeaders : undefined,
      })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = '라인팀 생성 결과.xlsx'
      a.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      setError(err instanceof Error ? err.message : '엑셀 추출 중 오류가 발생했습니다.')
    }
  }, [teams, exportMemberSort, importColumnHeaders])

  return (
    <div className="app">
      <header className="header">
        <h1>승무원 라인팀 편성</h1>
      </header>

      <main className="main">
        {error && (
          <div className="error card error-bar">
            <span>{error}</span>
            <button
              type="button"
              className="error-dismiss"
              onClick={() => setError(null)}
              aria-label="닫기"
            >
              ×
            </button>
          </div>
        )}

        {step === 'upload' && (
          <section className="section card">
            <h2>재직 현황 불러오기</h2>

            <div className="import-options">
              <div className="import-option">
                <h3>엑셀 파일</h3>
                <p>로컬 엑셀(.xlsx, .xls) 파일을 업로드합니다.</p>
                <label className="file-label btn btn-large">
                  <input
                    type="file"
                    accept=".xlsx,.xls"
                    onChange={onFileSelect}
                    disabled={loading}
                  />
                  <span>{loading ? '업로드 중…' : '엑셀 파일 업로드'}</span>
                </label>
              </div>

              <div className="import-option">
                <h3>Google 스프레드시트</h3>
                <p>
                  {hasGoogleClientId
                    ? '계정 선택 후 접근 권한이 있는 문서만 가져옵니다.'
                    : '공유 링크가 공개된 문서를 바로 가져옵니다.'}
                </p>
                {googleAccountEmail && (
                  <p className="google-account-email">현재 선택 계정: {googleAccountEmail}</p>
                )}
                <div className="google-import-row">
                  <input
                    id="google-sheet-url"
                    type="url"
                    className="google-import-input"
                    placeholder="스프레드시트 URL를 입력하세요"
                    value={googleUrl}
                    onChange={(e) => setGoogleUrl(e.target.value)}
                    disabled={loading}
                    autoComplete="off"
                  />
                  <button
                    type="button"
                    className="btn btn-large google-import-btn"
                    onClick={onGoogleImport}
                    disabled={loading}
                  >
                    {loading ? '가져오는 중…' : '가져오기'}
                  </button>
                </div>
              </div>

              <div className="import-option">
                <h3>CSV 붙여넣기</h3>
                <p>
                  엑셀에서 영역을 복사(Ctrl+C)한 뒤 아래에 붙여넣기 하세요. 파일 저장·업로드가 필요 없습니다.
                </p>
                <textarea
                  className="csv-paste-textarea"
                  placeholder="첫 행은 헤더(사번, 이름, BASE …), 탭 또는 쉼표 구분"
                  value={csvPasteText}
                  onChange={(e) => setCsvPasteText(e.target.value)}
                  disabled={loading}
                  rows={6}
                  spellCheck={false}
                />
                <button
                  type="button"
                  className="btn btn-large btn-primary"
                  onClick={onCsvPasteSubmit}
                  disabled={loading}
                >
                  {loading ? '처리 중…' : '붙여넣은 내용 불러오기'}
                </button>
              </div>
            </div>
          </section>
        )}

        {step === 'review' && (
          <section className="section card">
            <h2>업로드 파일 확인</h2>
            <p className="section-desc">
              아래 데이터가 편성 대상이 맞는지 확인한 뒤 <strong>편성 시작</strong> 버튼을 눌러주세요.
            </p>
            <div className="review-meta">
              <span>파일명: <strong>{uploadedExcelName ?? '-'}</strong></span>
              <span>인원수: <strong>{crew.length}</strong>명</span>
            </div>
            <div className="review-preview">
              <table>
                <thead>
                  <tr>
                    <th>사번</th>
                    <th>이름</th>
                    <th>BASE</th>
                    <th>Rank</th>
                    <th>Line</th>
                  </tr>
                </thead>
                <tbody>
                  {crew.slice(0, 10).map((m, idx) => (
                    <tr key={`${m.employeeId}-${idx}`}>
                      <td>{m.employeeId}</td>
                      <td>{m.name}</td>
                      <td>{m.base}</td>
                      <td>{m.positionCode}</td>
                      <td>{m.line}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {crew.length > 10 && (
                <p className="review-more">미리보기는 상위 10명만 표시됩니다.</p>
              )}
            </div>
            <div className="section-actions">
              <button type="button" className="btn btn-large" onClick={onBackToUpload} disabled={loading}>
                다른 파일 선택
              </button>
              <button
                type="button"
                className="btn btn-large btn-primary"
                onClick={onAssignFromReview}
                disabled={loading}
              >
                {loading ? '편성 중…' : '편성 시작'}
              </button>
            </div>
          </section>
        )}

        {step === 'assigned' && teams.length > 0 && (
          <section className="section">
            <div className="section-header">
              <h2>편성 결과</h2>
              <div className="section-actions section-actions-export">
                <label className="export-sort-label">
                  <span className="export-sort-text">엑셀 행 순서</span>
                  <select
                    className="export-sort-select"
                    value={exportMemberSort}
                    onChange={(e) =>
                      setExportMemberSort(e.target.value as ExportMemberSort)
                    }
                    disabled={loading}
                    aria-label="엑셀 추출 시 팀 내 행 정렬"
                  >
                    <option value="employeeId">사번순</option>
                    <option value="grade">직급순 (TP→TS→SP…→사번)</option>
                  </select>
                </label>
                <button
                  type="button"
                  className="btn btn-large"
                  onClick={onReassign}
                  disabled={loading}
                >
                  {loading ? '편성 중…' : '전체 재편성'}
                </button>
                <button type="button" className="btn btn-large btn-primary" onClick={onExport}>
                  엑셀 추출
                </button>
              </div>
            </div>
            <div className="reassign-pin-row">
              <div className="reassign-pin-left">
                <div className="reassign-pin-buttons">
                  <button
                    type="button"
                    className={`btn btn-large btn-pin${selectedPinMode === 'TP_FIXED' ? ' btn-pin-active' : ''}`}
                    onClick={() => selectPinMode('TP_FIXED')}
                    disabled={loading}
                  >
                    TP 고정
                  </button>
                  <button
                    type="button"
                    className={`btn btn-large btn-pin${selectedPinMode === 'TS_FIXED' ? ' btn-pin-active' : ''}`}
                    onClick={() => selectPinMode('TS_FIXED')}
                    disabled={loading}
                  >
                    TS 고정
                  </button>
                  <button
                    type="button"
                    className={`btn btn-large btn-pin${selectedPinMode === 'BOTH_FIXED' ? ' btn-pin-active' : ''}`}
                    onClick={() => selectPinMode('BOTH_FIXED')}
                    disabled={loading}
                  >
                    TP·TS 고정
                  </button>
                </div>
              </div>
              <div className="reassign-pin-right">
                <button
                  type="button"
                  className="btn btn-large btn-primary"
                  onClick={onApplyPinRegenerate}
                  disabled={loading || selectedPinMode === null}
                >
                  {loading && selectedPinMode ? '편성 중…' : '다시 편성하기'}
                </button>
              </div>
            </div>
            <div className="stats-bar">
              <span className="stat">생성된 팀 <strong>{teams.length}</strong>개</span>
              <span className="stat">총 승무원 <strong>{teams.reduce((s, t) => s + t.members.length, 0)}</strong>명</span>
            </div>
            <TeamBoard teams={teams} onMoveMember={onMoveMember} />
          </section>
        )}
      </main>
    </div>
  )
}

export default App
