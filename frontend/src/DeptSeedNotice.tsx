import { useEffect } from 'react'
import './DeptSeedNotice.css'

export interface DeptSeedNoticeData {
  seeded: number
  skipped: number
  /** preAssign | assigned */
  context: 'preAssign' | 'assigned'
}

interface DeptSeedNoticeProps {
  notice: DeptSeedNoticeData | null
  onDismiss: () => void
}

function buildMessage(notice: DeptSeedNoticeData): { title: string; detail: string } {
  if (notice.context === 'preAssign') {
    return {
      title: `소속팀 선배치 완료 · ${notice.seeded}명`,
      detail:
        notice.skipped > 0
          ? `엑셀 「소속팀」이 있는 인원을 해당 팀에 미리 넣었습니다. (팀 미매칭·TP 충돌 ${notice.skipped}명은 미배정 풀에 남김)`
          : '엑셀 「소속팀」이 있는 인원을 해당 팀에 미리 넣었습니다. 나머지 TP/TS만 직접 배치하면 됩니다.',
    }
  }
  return {
    title: `소속팀 선배치 반영 · ${notice.seeded}명`,
    detail:
      notice.skipped > 0
        ? `소속팀이 채워진 ${notice.seeded}명을 먼저 팀에 고정한 뒤 나머지를 자동 편성했습니다. (스킵 ${notice.skipped}명)`
        : `소속팀이 채워진 ${notice.seeded}명을 먼저 팀에 고정한 뒤 나머지를 자동 편성했습니다.`,
  }
}

export function DeptSeedNotice({ notice, onDismiss }: DeptSeedNoticeProps) {
  useEffect(() => {
    if (!notice || notice.seeded <= 0) return
    const t = window.setTimeout(onDismiss, 12000)
    return () => window.clearTimeout(t)
  }, [notice, onDismiss])

  if (!notice || notice.seeded <= 0) return null

  const { title, detail } = buildMessage(notice)

  return (
    <div className="dept-seed-toast" role="status" aria-live="polite">
      <div className="dept-seed-toast-body">
        <strong className="dept-seed-toast-title">{title}</strong>
        <p className="dept-seed-toast-detail">{detail}</p>
      </div>
      <button type="button" className="dept-seed-toast-dismiss" onClick={onDismiss} aria-label="알림 닫기">
        닫기
      </button>
    </div>
  )
}
