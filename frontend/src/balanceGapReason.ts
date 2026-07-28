import type { BalanceMetric } from './balanceReport'

export type GapReasonCategory = 'rank' | 'from' | 'grade' | 'yy'

const CATEGORY_BY_KEY: Record<string, GapReasonCategory> = {
  fp: 'rank',
  yy: 'yy',
  tsOjt: 'rank',
  lj: 'from',
  bx: 'from',
  rs: 'from',
  ps: 'grade',
  ap: 'grade',
  ss: 'grade',
  intern: 'grade',
}

/** 불균형 항목 칩 아래 한 줄 설명 */
export function metricGapHint(metric: BalanceMetric): string | null {
  if (metric.ok || metric.total === 0) return null
  const cat = CATEGORY_BY_KEY[metric.key]
  switch (cat) {
    case 'rank':
      return 'RANK는 1순위이나 TP·TS 선배치·YY 규칙 영향으로 편차가 남을 수 있습니다. 재배치가 필요해 보입니다.'
    case 'from':
      return '배치 시 FP·직급이 먼저 적용되어 FROM(LJ/BX/RS)은 팀별로 쏠리기 쉽습니다. 재배치가 필요해 보입니다.'
    case 'grade':
      return 'FP·RANK가 먼저라 직급(PS/AP/SS/인턴)은 들쭉날쭉해질 수 있습니다. 재배치가 필요해 보입니다.'
    case 'yy':
      return 'YY는 “YY 없는 팀에 1명”을 먼저 채웁니다. YY 인원이 팀 수보다 적으면 0명인 팀이 남을 수 있습니다. 재배치가 필요해 보입니다.'
    default:
      return null
  }
}

export const BALANCE_GAP_OVERVIEW = {
  title: '왜 팀 간 격차가 생기나요?',
  steps: [
    '편성 순서: TP → TS → 나머지. 앞 단계에서 FROM·직급이 이미 한쪽으로 기울 수 있습니다.',
    '한 명 배치 시 우선순위: ① RANK(FP/YY/TS OJT) → ② 직급 → ③ FROM. 한 사람에게는 하나만 적용됩니다.',
    '예: FP+LJ 인원은 FP만 보고 들어가 검증의 LJ·BX·RS는 불균형해지기 쉽습니다.',
    '자격·인원 제약 때문에 자동으로 완전히 맞추기 어려울 수 있습니다. 주황 테두리 팀을 기준으로 재배치해 보세요.',
  ],
  tip: '주황 테두리 팀 = 적은 팀·많은 팀. 드래그로 옮기면 검증이 실시간 갱신됩니다.',
}
