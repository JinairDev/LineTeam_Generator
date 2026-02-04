import { useState, useCallback, useMemo } from 'react'
import type { LineTeam } from './types'
import type { TeamCountByBase } from './api'
import { uploadExcel, assignTeams, moveMember, exportExcel } from './api'
import { TeamBoard } from './TeamBoard'
import './App.css'

type Step = 'upload' | 'settings' | 'assigned'

function App() {
  const [step, setStep] = useState<Step>('upload')
  const [crew, setCrew] = useState<import('./types').CrewMember[]>([])
  const [teams, setTeams] = useState<LineTeam[]>([])
  const [teamCountByBase, setTeamCountByBase] = useState<TeamCountByBase>({})
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const basesWithTpCount = useMemo(() => {
    if (crew.length === 0) return []
    const baseTp: Record<string, number> = {}
    crew.forEach((c) => {
      const base = c.base?.trim() || '(없음)'
      const isTP = (c.positionCode ?? '').includes('TP') || (c.grade ?? '').includes('TP')
      if (isTP) baseTp[base] = (baseTp[base] ?? 0) + 1
    })
    return Object.entries(baseTp).sort((a, b) => a[0].localeCompare(b[0]))
  }, [crew])

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
      setCrew(list)
      const initial: TeamCountByBase = {}
      list.forEach((c) => {
        const b = c.base?.trim() || '(없음)'
        const isTP = (c.positionCode ?? '').includes('TP') || (c.grade ?? '').includes('TP')
        if (isTP) initial[b] = (initial[b] ?? 0) + 1
      })
      Object.keys(initial).forEach((b) => {
        initial[b] = Math.max(1, initial[b]!)
      })
      setTeamCountByBase(initial)
      setStep('settings')
    } catch (err) {
      setError(err instanceof Error ? err.message : '업로드 실패')
    } finally {
      setLoading(false)
    }
  }, [])

  const onRunAssign = useCallback(async () => {
    setError(null)
    setLoading(true)
    try {
      const effective: TeamCountByBase = {}
      basesWithTpCount.forEach(([base, tpCount]) => {
        const v = teamCountByBase[base]
        effective[base] = v != null && v >= 1 ? v : Math.max(1, tpCount)
      })
      const result = await assignTeams(crew, effective)
      setTeams(result)
      setStep('assigned')
    } catch (err) {
      setError(err instanceof Error ? err.message : '편성 실패')
    } finally {
      setLoading(false)
    }
  }, [crew, teamCountByBase, basesWithTpCount])

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
              <span>{loading ? '업로드 중…' : '엑셀 파일 업로드'}</span>
            </label>
          </section>
        )}

        {step === 'settings' && (
          <section className="section card">
            <h2>라인팀 갯수 설정</h2>
            <p className="section-desc">지역(BASE)별 팀 수를 입력한 뒤 편성 실행을 눌러주세요. 팀 수를 TP 수보다 많게 하면 TP 없는 팀도 생성됩니다.</p>
            <div className="team-count-form">
              {basesWithTpCount.map(([base, tpCount]) => (
                <div key={base} className="team-count-row">
                  <label>
                    <span className="team-count-label">{base}</span>
                    <span className="team-count-tp">(TP {tpCount}명)</span>
                  </label>
                  <input
                    type="text"
                    inputMode="numeric"
                    className="team-count-input"
                    placeholder={String(tpCount)}
                    value={teamCountByBase[base] ?? ''}
                    onChange={(e) => {
                      const raw = e.target.value.replace(/\D/g, '')
                      if (raw === '') {
                        setTeamCountByBase((prev) => {
                          const next = { ...prev }
                          delete next[base]
                          return next
                        })
                        return
                      }
                      const v = parseInt(raw, 10)
                      if (v >= 1) setTeamCountByBase((prev) => ({ ...prev, [base]: v }))
                    }}
                  />
                  <span className="team-count-unit">팀</span>
                </div>
              ))}
            </div>
            <div className="section-actions" style={{ marginTop: '1rem' }}>
              <button
                type="button"
                className="btn btn-large btn-primary"
                onClick={onRunAssign}
                disabled={loading}
              >
                {loading ? '편성 중…' : '편성 실행'}
              </button>
            </div>
          </section>
        )}

        {step === 'assigned' && teams.length > 0 && (
          <section className="section">
            <div className="section-header">
              <h2>편성 결과</h2>
              <div className="section-actions">
                <button
                  type="button"
                  className="btn btn-large"
                  onClick={() => setStep('settings')}
                >
                  팀 다시 생성하기
                </button>
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
