import type { FpYyBalanceReport } from './types'
import { metricsForBase } from './balanceReport'

export interface BalanceMoveIssue {
  base: string
  label: string
  kind: 'new' | 'worse'
  range: string
  prevRange?: string
}

/** 이동 전후 비교 — 새로 불균형이 되었거나 편차가 커진 항목 */
export function detectBalanceMoveIssues(
  before: FpYyBalanceReport | null,
  after: FpYyBalanceReport
): BalanceMoveIssue[] {
  const issues: BalanceMoveIssue[] = []

  for (const baseAfter of after.bases) {
    const baseBefore = before?.bases.find((b) => b.base === baseAfter.base)
    const metricsAfter = metricsForBase(baseAfter)
    const metricsBefore = baseBefore ? metricsForBase(baseBefore) : []

    for (const mAfter of metricsAfter) {
      if (mAfter.total === 0) continue
      const mBefore = metricsBefore.find((m) => m.key === mAfter.key)
      const wasOk = mBefore?.ok ?? true
      const spreadAfter = mAfter.max - mAfter.min
      const spreadBefore = mBefore ? mBefore.max - mBefore.min : 0

      if (!mAfter.ok && wasOk) {
        issues.push({
          base: baseAfter.base,
          label: mAfter.label,
          kind: 'new',
          range: `${mAfter.min}~${mAfter.max}명`,
        })
      } else if (!mAfter.ok && spreadAfter > spreadBefore) {
        issues.push({
          base: baseAfter.base,
          label: mAfter.label,
          kind: 'worse',
          range: `${mAfter.min}~${mAfter.max}명`,
          prevRange: mBefore ? `${mBefore.min}~${mBefore.max}명` : undefined,
        })
      }
    }
  }

  return issues
}
