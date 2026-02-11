import type { Modifier } from '@dnd-kit/core'

/**
 * 드래그 오버레이가 커서 중앙에 오도록 위치를 보정합니다.
 * 카드가 손가락/마우스 위치에 붙어 따라다니는 느낌을 줍니다.
 */
export const centerUnderCursor: Modifier = ({
  transform,
  activatorEvent,
  draggingNodeRect,
  overlayNodeRect,
}) => {
  const rect = overlayNodeRect ?? draggingNodeRect
  if (!activatorEvent || !rect) return transform
  let clientX = 0
  let clientY = 0
  if ('clientX' in activatorEvent && 'clientY' in activatorEvent) {
    const mouseEvent = activatorEvent as MouseEvent
    clientX = mouseEvent.clientX
    clientY = mouseEvent.clientY
  } else if (activatorEvent instanceof TouchEvent && activatorEvent.touches?.[0]) {
    clientX = activatorEvent.touches[0].clientX
    clientY = activatorEvent.touches[0].clientY
  }
  const offsetX = clientX - rect.left - rect.width / 2
  const offsetY = clientY - rect.top - rect.height / 2
  return {
    ...transform,
    x: transform.x + offsetX,
    y: transform.y + offsetY,
  }
}
