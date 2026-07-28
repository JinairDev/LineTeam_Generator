import { useState, useCallback, useRef } from 'react'
import type { FpYyBalanceReport, LineTeam } from './types'
import {
  uploadExcel,
  uploadCrewCsvText,
  uploadFromGoogleSpreadsheet,
  assignTeams,
  createTeamShells,
  moveMember,
  exportExcel,
  type CrewUploadResponse,
  type ExportMemberSort,
  type PinMode,
} from './api'
import { fetchGoogleSheetCsvWithSelectedAccount } from './googleSheetsOAuth'
import { TeamBoard } from './TeamBoard'
import { PreAssignTpTsBoard } from './PreAssignTpTsBoard'
import { BalanceReportCard } from './BalanceReportCard'
import { ConfirmDialog } from './ConfirmDialog'
import { DeptSeedNotice, type DeptSeedNoticeData } from './DeptSeedNotice'
import { detectBalanceMoveIssues, type BalanceMoveIssue } from './balanceDiff'
import { countTpTs, filterTpTsForPreAssign } from './rankToken'
import './App.css'

type Step = 'upload' | 'review' | 'preAssignChoice' | 'preAssign' | 'assigned'

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
  const [fpYyBalance, setFpYyBalance] = useState<FpYyBalanceReport | null>(null)
  const [balanceMoveIssues, setBalanceMoveIssues] = useState<BalanceMoveIssue[] | null>(null)
  const fpYyBalanceRef = useRef(fpYyBalance)
  fpYyBalanceRef.current = fpYyBalance
  const [preAssignPool, setPreAssignPool] = useState<import('./types').CrewMember[]>([])
  const [deptSeedNotice, setDeptSeedNotice] = useState<DeptSeedNoticeData | null>(null)
  const [exportConfirmOpen, setExportConfirmOpen] = useState(false)
  const [exporting, setExporting] = useState(false)

  const showDeptSeedNotice = useCallback(
    (seeded: number | undefined, skipped: number | undefined, context: DeptSeedNoticeData['context']) => {
      const s = seeded ?? 0
      if (s <= 0) {
        setDeptSeedNotice(null)
        return
      }
      setDeptSeedNotice({ seeded: s, skipped: skipped ?? 0, context })
    },
    [],
  )

  const applyAssignResult = useCallback(
    (result: {
      teams: LineTeam[]
      fpYyBalance?: FpYyBalanceReport
      departmentSeededCount?: number
      departmentSeedSkippedCount?: number
    }) => {
      setTeams(result.teams)
      setFpYyBalance(result.fpYyBalance ?? null)
      setBalanceMoveIssues(null)
      showDeptSeedNotice(result.departmentSeededCount, result.departmentSeedSkippedCount, 'assigned')
      setStep('assigned')
    },
    [showDeptSeedNotice],
  )

  const applyCrewAndAssign = useCallback(
    async (list: import('./types').CrewMember[], columnHeaders?: string[]) => {
      if (list.length === 0) return
      setCrew(list)
      if (columnHeaders !== undefined) setImportColumnHeaders(columnHeaders)
      const result = await assignTeams(list)
      applyAssignResult(result)
    },
    [applyAssignResult]
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

  const onAssignFromReview = useCallback(() => {
    if (crew.length === 0) {
      setError('편성할 승무원 데이터가 없습니다.')
      return
    }
    setError(null)
    setStep('preAssignChoice')
  }, [crew])

  const onSkipPreAssign = useCallback(async () => {
    if (crew.length === 0) return
    setError(null)
    setLoading(true)
    try {
      const result = await assignTeams(crew)
      applyAssignResult(result)
    } catch (err) {
      setError(err instanceof Error ? err.message : '편성 중 오류가 발생했습니다.')
    } finally {
      setLoading(false)
    }
  }, [crew, applyAssignResult])

  const onStartPreAssign = useCallback(async () => {
    if (crew.length === 0) return
    const tpTs = filterTpTsForPreAssign(crew)
    if (tpTs.length === 0) {
      setError('편성 대상 TP/TS가 없습니다. 자동 편성을 이용해 주세요.')
      return
    }
    setError(null)
    setLoading(true)
    try {
      const shellsRes = await createTeamShells(crew)
      const shells = shellsRes.teams ?? []
      const seededIds = new Set(
        shells.flatMap((t) => t.members.map((m) => m.employeeId)).filter(Boolean),
      )
      setTeams(shells)
      setPreAssignPool(tpTs.filter((m) => !seededIds.has(m.employeeId)))
      showDeptSeedNotice(
        shellsRes.departmentSeededCount ?? seededIds.size,
        shellsRes.departmentSeedSkippedCount,
        'preAssign',
      )
      setStep('preAssign')
    } catch (err) {
      setError(err instanceof Error ? err.message : '팀 생성 중 오류가 발생했습니다.')
    } finally {
      setLoading(false)
    }
  }, [crew, showDeptSeedNotice])

  const onCompletePreAssign = useCallback(async () => {
    if (crew.length === 0 || teams.length === 0) return
    setError(null)
    setLoading(true)
    try {
      const result = await assignTeams(crew, undefined, {
        pinMode: 'BOTH_FIXED',
        previousTeams: teams,
      })
      applyAssignResult(result)
    } catch (err) {
      setError(err instanceof Error ? err.message : '편성 중 오류가 발생했습니다.')
    } finally {
      setLoading(false)
    }
  }, [crew, teams, applyAssignResult])

  const onBackToUpload = useCallback(() => {
    setStep('upload')
    setTeams([])
    setCrew([])
    setImportColumnHeaders([])
    setUploadedExcelName(null)
    setCsvPasteText('')
    setError(null)
    setFpYyBalance(null)
    setBalanceMoveIssues(null)
    setPreAssignPool([])
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
      applyAssignResult(result)
    } catch (err) {
      setError(err instanceof Error ? err.message : '편성 중 오류가 발생했습니다.')
    } finally {
      setLoading(false)
    }
  }, [crew, applyAssignResult])

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
      applyAssignResult(result)
    } catch (err) {
      setError(err instanceof Error ? err.message : '편성 중 오류가 발생했습니다.')
    } finally {
      setLoading(false)
    }
  }, [crew, teams, selectedPinMode, applyAssignResult])

  const onMoveMember = useCallback(
    async (
      employeeId: string,
      fromTeamId: string,
      toTeamId: string,
      toIndex?: number
    ) => {
      setError(null)
      const prevBalance = fpYyBalanceRef.current
      try {
        const result = await moveMember(
          teams,
          employeeId,
          fromTeamId,
          toTeamId,
          toIndex
        )
        setTeams(result.teams)
        const report = result.fpYyBalance ?? null
        setFpYyBalance(report)
        if (report) {
          const issues = detectBalanceMoveIssues(prevBalance, report)
          setBalanceMoveIssues(issues.length > 0 ? issues : null)
        } else {
          setBalanceMoveIssues(null)
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : '이동 중 오류가 발생했습니다.')
      }
    },
    [teams]
  )

  const onExportClick = useCallback(() => {
    if (teams.length === 0) return
    setExportConfirmOpen(true)
  }, [teams.length])

  const onExportConfirm = useCallback(async () => {
    if (teams.length === 0) return
    setError(null)
    setExporting(true)
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
      setExportConfirmOpen(false)
    } catch (err) {
      setError(err instanceof Error ? err.message : '엑셀 추출 중 오류가 발생했습니다.')
      setExportConfirmOpen(false)
    } finally {
      setExporting(false)
    }
  }, [teams, exportMemberSort, importColumnHeaders])

  return (
    <div className={`app${step === 'upload' ? ' app--upload' : ''}`}>
      {step !== 'upload' && (
        <header className="header">
          <div className="header-brand">
            <span className="header-brand-mark">JINAIR</span>
            <h1>라인팀 편성</h1>
          </div>
        </header>
      )}

      <main className="main">
        <DeptSeedNotice notice={deptSeedNotice} onDismiss={() => setDeptSeedNotice(null)} />
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
          <section className="upload-landing" aria-label="재직 데이터 불러오기">
            <div className="upload-landing-hero">
              <p className="upload-brand">JINAIR</p>
              <h1 className="upload-title">라인팀 편성</h1>
              <p className="upload-lead">재직 데이터를 불러와 팀을 구성합니다.</p>
            </div>

            <div className="upload-chunks">
              <article className="upload-chunk upload-chunk--excel">
                <div className="upload-chunk-head">
                  <span className="upload-chunk-kicker">File</span>
                  <h2>엑셀 파일</h2>
                  <p>로컬 엑셀(.xlsx, .xls)을 업로드합니다.</p>
                </div>
                <label className="upload-chunk-action file-label">
                  <input
                    type="file"
                    accept=".xlsx,.xls"
                    onChange={onFileSelect}
                    disabled={loading}
                  />
                  <span>{loading ? '업로드 중…' : '엑셀 업로드'}</span>
                </label>
              </article>

              <article className="upload-chunk upload-chunk--google">
                <div className="upload-chunk-head">
                  <span className="upload-chunk-kicker">Sheet</span>
                  <h2>Google 스프레드시트</h2>
                  <p>
                    {hasGoogleClientId
                      ? '계정 선택 후 접근 권한이 있는 문서만 가져옵니다.'
                      : '공유 링크가 공개된 문서를 바로 가져옵니다.'}
                  </p>
                </div>
                {googleAccountEmail && (
                  <p className="google-account-email">현재 계정: {googleAccountEmail}</p>
                )}
                <div className="google-import-row">
                  <input
                    id="google-sheet-url"
                    type="url"
                    className="google-import-input"
                    placeholder="스프레드시트 URL"
                    value={googleUrl}
                    onChange={(e) => setGoogleUrl(e.target.value)}
                    disabled={loading}
                    autoComplete="off"
                  />
                  <button
                    type="button"
                    className="btn btn-large google-import-btn upload-btn-secondary"
                    onClick={onGoogleImport}
                    disabled={loading}
                  >
                    {loading ? '가져오는 중…' : '가져오기'}
                  </button>
                </div>
              </article>

              <article className="upload-chunk upload-chunk--csv">
                <div className="upload-chunk-head">
                  <span className="upload-chunk-kicker">Paste</span>
                  <h2>CSV 붙여넣기</h2>
                  <p>
                    엑셀에서 영역을 복사한 뒤 아래에 붙여넣으세요. 파일 저장·업로드가 필요 없습니다.
                  </p>
                </div>
                <textarea
                  className="csv-paste-textarea"
                  placeholder="첫 행은 헤더(사번, 이름, BASE …), 탭 또는 쉼표 구분"
                  value={csvPasteText}
                  onChange={(e) => setCsvPasteText(e.target.value)}
                  disabled={loading}
                  rows={8}
                  spellCheck={false}
                />
                <button
                  type="button"
                  className="btn btn-large upload-btn-primary"
                  onClick={onCsvPasteSubmit}
                  disabled={loading}
                >
                  {loading ? '처리 중…' : '붙여넣은 내용 불러오기'}
                </button>
              </article>
            </div>
          </section>
        )}

        {step === 'review' && (
          <section className="section card">
            <h2>업로드 파일 확인</h2>
            <p className="section-desc">
              아래 데이터가 편성 대상이 맞는지 확인한 뒤 <strong>다음</strong> 버튼을 눌러주세요.
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
                다음
              </button>
            </div>
          </section>
        )}

        {step === 'preAssignChoice' && (
          <section className="section card pre-assign-choice">
            <h2>사전 TP/TS를 배정하시겠습니까?</h2>
            <p className="section-desc">
              팀장(TP)과 사무장(TS)을 직접 팀에 배치한 뒤, 나머지 승무원은 기존 자동 편성 규칙으로 배정됩니다.
            </p>
            <div className="pre-assign-choice-stats">
              {(() => {
                const { tp, ts } = countTpTs(crew)
                return (
                  <>
                    <span>편성 대상 TP <strong>{tp}</strong>명</span>
                    <span>편성 대상 TS <strong>{ts}</strong>명</span>
                  </>
                )
              })()}
            </div>
            <div className="pre-assign-choice-actions">
              <button
                type="button"
                className="btn btn-large pre-assign-choice-yes"
                onClick={onStartPreAssign}
                disabled={loading}
              >
                {loading ? '준비 중…' : '예, 직접 배정'}
              </button>
              <button
                type="button"
                className="btn btn-large pre-assign-choice-no"
                onClick={onSkipPreAssign}
                disabled={loading}
              >
                {loading ? '편성 중…' : '아니오, 자동 편성'}
              </button>
            </div>
          </section>
        )}

        {step === 'preAssign' && teams.length > 0 && (
          <section className="section">
            <div className="section-header">
              <h2>TP/TS 사전 배정</h2>
              <div className="section-actions">
                <button
                  type="button"
                  className="btn btn-large"
                  onClick={() => setStep('preAssignChoice')}
                  disabled={loading}
                >
                  이전
                </button>
                <button
                  type="button"
                  className="btn btn-large btn-primary"
                  onClick={onCompletePreAssign}
                  disabled={loading}
                >
                  {loading ? '편성 중…' : '나머지 자동 편성'}
                </button>
              </div>
            </div>
            <p className="section-desc pre-assign-desc">
              원하는 만큼만 TP/TS를 팀 카드에 배치한 뒤, 「나머지 자동 편성」을 누르면
              미배정 TP/TS와 팀원이 자동으로 채워집니다. 이미 넣은 TP/TS는 유지됩니다.
              {preAssignPool.length > 0 && (
                <span className="pre-assign-remaining">
                  {' '}미배정 <strong>{preAssignPool.length}</strong>명 → 자동 배정 예정
                </span>
              )}
            </p>
            {deptSeedNotice && deptSeedNotice.context === 'preAssign' && (
              <div className="dept-seed-banner">
                <strong>소속팀 선배치 · {deptSeedNotice.seeded}명</strong>
                {' — '}엑셀 「소속팀」 기준으로 팀에 미리 넣었습니다
                {deptSeedNotice.skipped > 0 ? ` (스킵 ${deptSeedNotice.skipped}명)` : ''}.
              </div>
            )}
            <PreAssignTpTsBoard
              teams={teams}
              pool={preAssignPool}
              onTeamsChange={setTeams}
              onPoolChange={setPreAssignPool}
            />
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
                <button
                  type="button"
                  className="btn btn-large btn-export-final"
                  onClick={onExportClick}
                  disabled={loading || exporting}
                >
                  엑셀 추출
                </button>
              </div>
            </div>
            {deptSeedNotice && deptSeedNotice.context === 'assigned' && deptSeedNotice.seeded > 0 && (
              <div className="dept-seed-banner">
                <strong>소속팀 선배치 · {deptSeedNotice.seeded}명</strong>
                {' — '}해당 인원을 먼저 고정한 뒤 나머지를 자동 편성했습니다
                {deptSeedNotice.skipped > 0 ? ` (스킵 ${deptSeedNotice.skipped}명)` : ''}.
              </div>
            )}
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
            {fpYyBalance && (
              <BalanceReportCard
                report={fpYyBalance}
                moveIssues={balanceMoveIssues}
                onDismissMoveIssues={() => setBalanceMoveIssues(null)}
              />
            )}
            <TeamBoard teams={teams} balanceReport={fpYyBalance} onMoveMember={onMoveMember} />
          </section>
        )}
      </main>

      <ConfirmDialog
        open={exportConfirmOpen}
        title="엑셀로 추출하시겠습니까?"
        message="예를 누르면 편성 결과가 엑셀 파일로 다운로드됩니다."
        confirmLabel="예, 추출하기"
        cancelLabel="아니오"
        confirming={exporting}
        onConfirm={onExportConfirm}
        onCancel={() => {
          if (!exporting) setExportConfirmOpen(false)
        }}
      />
    </div>
  )
}

export default App
