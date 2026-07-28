import type { FpYyBalanceReport, FpYyTeamCount } from './types'
import { metricsForBase } from './balanceReport'

export type TeamBalanceIssueKind = 'high' | 'low' | 'missing'

export interface TeamBalanceIssue {
  key: string
  label: string
  count: number
  kind: TeamBalanceIssueKind
  min: number
  max: number
}

function countForMetric(team: FpYyTeamCount, key: string): number {
  switch (key) {
    case 'fp':
      return team.fpCount
    case 'yy':
      return team.yyCount
    case 'tsOjt':
      return team.tsOjtCount
    case 'lj':
      return team.ljCount
    case 'bx':
      return team.bxCount
    case 'rs':
      return team.rsCount
    case 'ps':
      return team.psCount
    case 'ap':
      return team.apCount
    case 'ss':
      return team.ssCount
    case 'intern':
      return team.internCount
    default:
      return 0
  }
}

function kindLabel(kind: TeamBalanceIssueKind, label: string): string {
  if (kind === 'missing') return `${label} 없음`
  if (kind === 'high') return `${label} 많음`
  return `${label} 적음`
}

/** 팀별 불균형 원인 (베이스 내 min/max 이탈·YY 미배치) */
export function buildTeamBalanceIssueMap(
  report: FpYyBalanceReport | null
): Map<string, TeamBalanceIssue[]> {
  const map = new Map<string, TeamBalanceIssue[]>()
  if (!report) return map

  for (const base of report.bases) {
    const metrics = metricsForBase(base)

    for (const metric of metrics) {
      if (metric.ok || metric.total === 0) continue
      const spread = metric.max - metric.min
      if (spread <= 1) continue

      for (const team of base.teams) {
        const count = countForMetric(team, metric.key)
        let kind: TeamBalanceIssueKind | null = null
        if (count === metric.max && metric.max > metric.min) {
          kind = 'high'
        } else if (count === metric.min && metric.max > metric.min) {
          kind = 'low'
        }
        if (!kind) continue
        const list = map.get(team.teamId) ?? []
        if (!list.some((i) => i.key === metric.key && i.kind === kind)) {
          list.push({
            key: metric.key,
            label: metric.label,
            count,
            kind,
            min: metric.min,
            max: metric.max,
          })
        }
        map.set(team.teamId, list)
      }
    }

    if (!base.yyMinimumCoverageMet && base.totalYy > 0) {
      for (const team of base.teams) {
        if (team.yyCount > 0) continue
        const list = map.get(team.teamId) ?? []
        if (!list.some((i) => i.key === 'yy' && i.kind === 'missing')) {
          list.push({
            key: 'yy',
            label: 'YY',
            count: 0,
            kind: 'missing',
            min: base.yyMinPerTeam,
            max: base.yyMaxPerTeam,
          })
          map.set(team.teamId, list)
        }
      }
    }
  }

  return map
}

export function formatTeamBalanceIssue(issue: TeamBalanceIssue): string {
  const head = kindLabel(issue.kind, issue.label)
  if (issue.kind === 'missing') return head
  return `${head}(${issue.count})`
}

export function teamBalanceIssueSummary(issues: TeamBalanceIssue[]): string {
  return issues.map(formatTeamBalanceIssue).join(' · ')
}
