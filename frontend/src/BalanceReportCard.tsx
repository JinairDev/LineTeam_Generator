import { useEffect, useState } from 'react'
import type { FpYyBalanceReport } from './types'
import type { BalanceMoveIssue } from './balanceDiff'
import { baseHasIssues, formatMetricDisplay, metricsForBase } from './balanceReport'
import type { BalanceMetric } from './balanceReport'
import { BALANCE_GAP_OVERVIEW, metricGapHint } from './balanceGapReason'
import './BalanceReportCard.css'

interface BalanceReportCardProps {
  report: FpYyBalanceReport
  moveIssues?: BalanceMoveIssue[] | null
  onDismissMoveIssues?: () => void
}

function MetricChip({
  metric,
  highlighted,
}: {
  metric: BalanceMetric
  highlighted?: boolean
}) {
  const { label, total, min, max, ok } = metric
  const display = formatMetricDisplay(total, min, max, ok)
  const hint = metricGapHint(metric)
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
      {hint && <p className="balance-metric-hint">{hint}</p>}
    </div>
  )
}

export function BalanceReportCard({ report, moveIssues, onDismissMoveIssues }: BalanceReportCardProps) {
  const warnCount = report.bases.reduce(
    (sum, b) => sum + metricsForBase(b).filter((m) => !m.ok).length,
    0,
  )

  const [expanded, setExpanded] = useState(true)

  useEffect(() => {
    if (moveIssues && moveIssues.length > 0) {
      setExpanded(true)
    }
  }, [moveIssues])

  const highlightedKeys = new Set(
    (moveIssues ?? []).map((i) => `${i.base}:${i.label}`)
  )

  const isHighlighted = (base: string, label: string) => highlightedKeys.has(`${base}:${label}`)

  const showGapOverview = !report.balanced || warnCount > 0

  return (
    <div
      className={`balance-report card ${report.balanced ? 'balance-report--ok' : 'balance-report--warn'}${expanded ? '' : ' balance-report--collapsed'}`}
      role="status"
    >
      <button
        type="button"
        className="balance-report-header balance-report-header--toggle"
        onClick={() => setExpanded((v) => !v)}
        aria-expanded={expanded}
      >
        <span className="balance-report-header-main">
          <span className="balance-report-chevron" aria-hidden>
            {expanded ? '▾' : '▸'}
          </span>
          <h3 className="balance-report-title">팀별 균등 분배 검증</h3>
        </span>
        <span className={`balance-report-badge ${report.balanced ? 'balance-report-badge--ok' : 'balance-report-badge--warn'}`}>
          {report.balanced ? '모두 균형' : `주의 ${warnCount}건`}
        </span>
      </button>

      {expanded && (
        <div className="balance-report-body">
      <p className="balance-report-legend">
        <strong>✓ 균등</strong> 차이 2명 이하
        <span className="balance-report-legend-sep">·</span>
        <strong>⚠ 불균형</strong> 차이 3명 이상
        <span className="balance-report-legend-sep">·</span>
        이동 시 실시간 반영 · 주황 테두리 = 편차 원인 팀
      </p>

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
                  <p className="balance-section-label">불균형 항목</p>
                  <div className="balance-metric-grid">
                    {issues.map((m) => (
                      <MetricChip
                        key={m.key}
                        metric={m}
                        highlighted={isHighlighted(b.base, m.label)}
                      />
                    ))}
                  </div>
                </div>
              )}

              {okMetrics.length > 0 && (
                <div className="balance-section">
                  <p className="balance-section-label">균등 분배된 항목</p>
                  <div className="balance-metric-grid balance-metric-grid--compact">
                    {okMetrics.map((m) => (
                      <MetricChip
                        key={m.key}
                        metric={m}
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

      {showGapOverview && (
        <details className="balance-gap-overview" open={false}>
          <summary>{BALANCE_GAP_OVERVIEW.title}</summary>
          <ol className="balance-gap-overview-list">
            {BALANCE_GAP_OVERVIEW.steps.map((step, i) => (
              <li key={i}>{step}</li>
            ))}
          </ol>
          <p className="balance-gap-overview-tip">{BALANCE_GAP_OVERVIEW.tip}</p>
        </details>
      )}
        </div>
      )}
    </div>
  )
}
