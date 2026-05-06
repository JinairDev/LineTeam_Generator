/** 편성 카드에서 휴직예정·단기휴직 강조(동일 스타일) */
export function memberHasLeaveStyleStatus(status: string | undefined): boolean {
  const s = status?.trim() ?? ''
  return s === '휴직예정' || s === '단기휴직'
}

/** 카드 상단 배지 문구 (없으면 null) */
export function memberStatusBadgeText(status: string | undefined): string | null {
  const s = status?.trim() ?? ''
  if (s === '휴직예정') return '*휴직예정'
  if (s === '단기휴직') return '*단기휴직'
  return null
}
