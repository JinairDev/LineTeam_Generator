import { useState, useCallback } from 'react'
import type { LineTeam } from './types'
import { uploadExcel, assignTeams, moveMember, exportExcel } from './api'
import { TeamBoard } from './TeamBoard'
import './App.css'

type Step = 'upload' | 'assigned'

function App() {
  const [step, setStep] = useState<Step>('upload')
  const [teams, setTeams] = useState<LineTeam[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const onFileSelect = useCallback(async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    setError(null)
    setLoading(true)
    try {
      const list = await uploadExcel(file)
      if (list.length === 0) {
        setLoading(false)
        return
      }
      const result = await assignTeams(list)
      setTeams(result)
      setStep('assigned')
    } catch (err) {
      setError(err instanceof Error ? err.message : '업로드 또는 편성 실패')
    } finally {
      setLoading(false)
    }
  }, [])

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
        setError(err instanceof Error ? err.message : '이동 실패')
      }
    },
    [teams]
  )

  const onExport = useCallback(async () => {
    if (teams.length === 0) return
    setError(null)
    try {
      const blob = await exportExcel(teams)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = '라인팀 생성 결과.xlsx'
      a.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      setError(err instanceof Error ? err.message : '엑셀 추출 실패')
    }
  }, [teams])

  return (
    <div className="app">
      <header className="header">
        <h1>승무원 라인팀 편성</h1>
        <p className="sub">재직 현황 엑셀 업로드 → 자동 편성 → 수동 조정 → 엑셀 추출</p>
      </header>

      <main className="main">
        {error && (
          <div className="error card">
            {error}
          </div>
        )}

        {step === 'upload' && (
          <section className="section card">
            <h2>재직 현황 불러오기</h2>
            <p className="section-desc">엑셀 컬럼: 사번, 이름, 성별, BASE, Rank, Line, 직급, 구분, 자격 (첫 행 헤더)</p>
            <label className="file-label btn btn-large">
              <input
                type="file"
                accept=".xlsx,.xls"
                onChange={onFileSelect}
                disabled={loading}
              />
              <span>{loading ? '업로드 및 편성 중…' : '엑셀 파일 업로드'}</span>
            </label>
          </section>
        )}

        {step === 'assigned' && teams.length > 0 && (
          <section className="section">
            <div className="section-header">
              <h2>편성 결과</h2>
              <div className="section-actions">
                <button type="button" className="btn btn-large btn-primary" onClick={onExport}>
                  엑셀 추출
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
