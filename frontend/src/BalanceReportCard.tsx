import type { FpYyBalanceReport } from './types'
import type { BalanceMoveIssue } from './balanceDiff'
import { baseHasIssues, formatMetricDisplay, metricsForBase } from './balanceReport'
import './BalanceReportCard.css'

interface BalanceReportCardProps {
  report: FpYyBalanceReport
  moveIssues?: BalanceMoveIssue[] | null
  onDismissMoveIssues?: () => void
}

function MetricChip({
  label,
  total,
  min,
  max,
  ok,
  highlighted,
}: {
  label: string
  total: number
  min: number
  max: number
  ok: boolean
  highlighted?: boolean
}) {
  const display = formatMetricDisplay(total, min, max, ok)
  return (
    <div
      className={`balance-metric ${ok ? 'balance-metric--ok' : 'balance-metric--warn'}${highlighted ? ' balance-metric--highlight' : ''}`}
    >
      <span className="balance-metric-label">{label}</span>
      <span className="balance-metric-total">{display.totalLabel}</span>
      <span className="balance-metric-range">{display.rangeLabel}</span>
      <span className={`balance-metric-verdict${ok ? ' balance-metric-verdict--ok' : ''}`}>
        {display.verdict}
      </span>
    </div>
  )
}

export function BalanceReportCard({ report, moveIssues, onDismissMoveIssues }: BalanceReportCardProps) {
  const warnCount = report.bases.reduce(
    (sum, b) => sum + metricsForBase(b).filter((m) => !m.ok).length,
    0,
  )

  const highlightedKeys = new Set(
    (moveIssues ?? []).map((i) => `${i.base}:${i.label}`)
  )

  const isHighlighted = (base: string, label: string) => highlightedKeys.has(`${base}:${label}`)

  return (
    <div
      className={`balance-report card ${report.balanced ? 'balance-report--ok' : 'balance-report--warn'}`}
      role="status"
    >
      <div className="balance-report-header">
        <h3 className="balance-report-title">팀별 균등 분배 검증</h3>
        <span className={`balance-report-badge ${report.balanced ? 'balance-report-badge--ok' : 'balance-report-badge--warn'}`}>
          {report.balanced ? '모두 균형' : `주의 ${warnCount}건`}
        </span>
      </div>

      <div className="balance-report-guide">
        <p>
          <strong>전체 N명</strong> — 이 베이스에 속한 해당 항목(FP, LJ 등) 인원 합계입니다.
          부족·과다가 아니라 <em>총량</em>입니다.
        </p>
        <p>
          <strong>적은 팀 · 많은 팀</strong> — 같은 베이스 안 팀들 중, 그 항목을
          <em>가장 적게</em> 가진 팀과 <em>가장 많이</em> 가진 팀 인원입니다.
        </p>
        <p>
          <strong>✓ 균등</strong> — 팀 간 차이 1명 이하 ·{' '}
          <strong>⚠ 불균형</strong> — 차이 2명 이상 (어떤 팀은 많고 어떤 팀은 적음)
        </p>
      </div>

      <p className="balance-report-live">팀원 이동 시 실시간 반영 · 주황 테두리 팀이 편차 원인</p>

      {moveIssues && moveIssues.length > 0 && (
        <div className="balance-move-alert" role="alert">
          <div className="balance-move-alert-head">
            <strong>이동으로 균형이 깨졌습니다</strong>
            {onDismissMoveIssues && (
              <button type="button" className="balance-move-alert-dismiss" onClick={onDismissMoveIssues}>
                닫기
              </button>
            )}
          </div>
          <ul className="balance-move-alert-list">
            {moveIssues.map((issue) => (
              <li key={`${issue.base}-${issue.label}-${issue.kind}`}>
                <span className="balance-move-alert-tag">
                  {issue.kind === 'new' ? '신규' : '악화'}
                </span>
                <strong>{issue.base}</strong> {issue.label}
                {' — '}
                적은 팀·많은 팀 {issue.range}
                {issue.prevRange && (
                  <span className="balance-move-alert-prev"> (이전 {issue.prevRange})</span>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="balance-report-bases">
        {report.bases.map((b) => {
          const metrics = metricsForBase(b)
          const issues = metrics.filter((m) => !m.ok)
          const okMetrics = metrics.filter((m) => m.ok)
          const hasIssues = baseHasIssues(b)

          return (
            <section key={b.base} className={`balance-base ${hasIssues ? 'balance-base--warn' : 'balance-base--ok'}`}>
              <div className="balance-base-header">
                <h4 className="balance-base-name">{b.base}</h4>
                <span className="balance-base-meta">{b.teamCount}팀</span>
                {b.teamsWithoutYy > 0 && b.totalYy > 0 && (
                  <span className="balance-base-note">YY 없는 팀 {b.teamsWithoutYy}개</span>
                )}
              </div>

              {hasIssues && (
                <div className="balance-section">
                  <p className="balance-section-label">팀 간 격차 큼</p>
                  <div className="balance-metric-grid">
                    {issues.map((m) => (
                      <MetricChip
                        key={m.key}
                        label={m.label}
                        total={m.total}
                        min={m.min}
                        max={m.max}
                        ok={false}
                        highlighted={isHighlighted(b.base, m.label)}
                      />
                    ))}
                  </div>
                </div>
              )}

              {okMetrics.length > 0 && (
                <div className="balance-section">
                  <p className="balance-section-label">{hasIssues ? '팀 간 균등 분배됨' : '항목'}</p>
                  <div className="balance-metric-grid balance-metric-grid--compact">
                    {okMetrics.map((m) => (
                      <MetricChip
                        key={m.key}
                        label={m.label}
                        total={m.total}
                        min={m.min}
                        max={m.max}
                        ok
                        highlighted={isHighlighted(b.base, m.label)}
                      />
                    ))}
                  </div>
                </div>
              )}
            </section>
          )
        })}
      </div>
    </div>
  )
}
