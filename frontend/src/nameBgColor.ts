import type { CSSProperties } from 'react'

/** 엑셀 이름 셀 배경색 → 카드 이름 영역에 쓸 인라인 스타일 */
export function nameBgStyle(nameBgColor?: string): CSSProperties | undefined {
  if (!nameBgColor) return undefined
  return {
    backgroundColor: nameBgColor,
    color: textColorOnBg(nameBgColor),
    borderRadius: '0.25rem',
    padding: '0.1rem 0.35rem',
    alignSelf: 'flex-start',
  }
}

function textColorOnBg(hex: string): string {
  const m = /^#?([0-9a-f]{6})$/i.exec(hex.trim())
  if (!m) return '#111'
  const n = parseInt(m[1], 16)
  const r = (n >> 16) & 0xff
  const g = (n >> 8) & 0xff
  const b = n & 0xff
  // WCAG relative luminance (sRGB approx)
  const lum = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255
  return lum > 0.55 ? '#111' : '#fff'
}
