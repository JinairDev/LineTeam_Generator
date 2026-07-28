import type { FpYyBaseBalance } from './types'

export interface BalanceMetric {
  key: string
  label: string
  total: number
  min: number
  max: number
  ok: boolean
}

export function metricsForBase(b: FpYyBaseBalance): BalanceMetric[] {
  return [
    {
      key: 'fp',
      label: 'FP',
      total: b.totalFp,
      min: b.fpMinPerTeam,
      max: b.fpMaxPerTeam,
      ok: b.fpBalanced,
    },
    {
      key: 'yy',
      label: 'YY',
      total: b.totalYy,
      min: b.yyMinPerTeam,
      max: b.yyMaxPerTeam,
      ok: b.yyBalanced && b.yyMinimumCoverageMet,
    },
    {
      key: 'tsOjt',
      label: 'TS OJT',
      total: b.totalTsOjt,
      min: b.tsOjtMinPerTeam,
      max: b.tsOjtMaxPerTeam,
      ok: b.tsOjtBalanced,
    },
    {
      key: 'lj',
      label: 'LJ',
      total: b.totalLj,
      min: b.ljMinPerTeam,
      max: b.ljMaxPerTeam,
      ok: b.ljBalanced,
    },
    {
      key: 'bx',
      label: 'BX',
      total: b.totalBx,
      min: b.bxMinPerTeam,
      max: b.bxMaxPerTeam,
      ok: b.bxBalanced,
    },
    {
      key: 'rs',
      label: 'RS',
      total: b.totalRs,
      min: b.rsMinPerTeam,
      max: b.rsMaxPerTeam,
      ok: b.rsBalanced,
    },
    {
      key: 'ps',
      label: 'PS',
      total: b.totalPs,
      min: b.psMinPerTeam,
      max: b.psMaxPerTeam,
      ok: b.psBalanced,
    },
    {
      key: 'ap',
      label: 'AP',
      total: b.totalAp,
      min: b.apMinPerTeam,
      max: b.apMaxPerTeam,
      ok: b.apBalanced,
    },
    {
      key: 'ss',
      label: 'SS',
      total: b.totalSs,
      min: b.ssMinPerTeam,
      max: b.ssMaxPerTeam,
      ok: b.ssBalanced,
    },
    {
      key: 'intern',
      label: '인턴',
      total: b.totalIntern,
      min: b.internMinPerTeam,
      max: b.internMaxPerTeam,
      ok: b.internBalanced,
    },
  ]
}

export function baseHasIssues(b: FpYyBaseBalance): boolean {
  return metricsForBase(b).some((m) => !m.ok)
}

/** 칩에 표시할 범위·판정 문구 */
export function formatMetricDisplay(total: number, min: number, max: number, ok: boolean) {
  if (total === 0) {
    return {
      totalLabel: '0명',
      rangeLabel: '해당 인원 없음',
      verdict: '—',
    }
  }
  const spread = max - min
  return {
    totalLabel: `전체 ${total}명`,
    rangeLabel: `적은 팀 ${min}명 · 많은 팀 ${max}명`,
    verdict: ok ? `균등 (팀 간 차이 ${spread}명)` : `불균형 (팀 간 차이 ${spread}명)`,
  }
}
