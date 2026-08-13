import { useState, useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
import {
  DndContext,
  DragEndEvent,
  DragOverlay,
  PointerSensor,
  useSensor,
  useSensors,
  useDroppable,
} from '@dnd-kit/core'
import type { Active, Over } from '@dnd-kit/core'
import type { CrewMember, LineTeam } from './types'
import { TeamColumn } from './TeamColumn'
import { MemberCard } from './MemberCard'
import { MemberCardPreview } from './MemberCardPreview'
import { DropSlot } from './DropSlot'
import {
  PRE_ASSIGN_POOL_ID,
  applyPreAssignMove,
  getPreAssignDropHint,
} from './preAssignMove'
import { boardCollisionDetection, resolveBoardDropTarget } from './dndCollision'
import { isTP, isTS } from './rankToken'
import { findTpTsQualViolations } from './tpTsQual'

interface PreAssignTpTsBoardProps {
  teams: LineTeam[]
  pool: CrewMember[]
  onTeamsChange: (teams: LineTeam[]) => void
  onPoolChange: (pool: CrewMember[]) => void
}

type MoveContextMenu = {
  clientX: number
  clientY: number
  employeeId: string
  fromTeamId: string
  targets: { teamId: string; label: string }[]
}

function UnassignedPool({
  pool,
  teams,
  isDropHighlighted,
  onOpenMoveMenu,
}: {
  pool: CrewMember[]
  teams: LineTeam[]
  isDropHighlighted?: boolean
  onOpenMoveMenu: (
    e: React.MouseEvent,
    employeeId: string,
    fromTeamId: string,
    targets: { teamId: string; label: string }[]
  ) => void
}) {
  const { setNodeRef, isOver } = useDroppable({ id: PRE_ASSIGN_POOL_ID })
  const tpPool = pool.filter(isTP)
  const tsPool = pool.filter((m) => isTS(m) && !isTP(m))
  const teamTargets = teams.map((t) => {
    const hasTp = t.members.some(isTP)
    return { teamId: t.teamId, label: t.teamId, hasTp }
  })

  return (
    <aside className={`pre-assign-pool card${isOver || isDropHighlighted ? ' pre-assign-pool-over' : ''}`}>
      <div className="pre-assign-pool-header">
        <h3>미배정 TP/TS</h3>
        <span className="pre-assign-pool-count">{pool.length}명</span>
      </div>
      <p className="pre-assign-pool-hint">
        원하는 만큼만 배치해도 됩니다. 팀당 <strong>TP는 1명</strong>이며, 이미 TP가 있는 팀에 놓으면 <strong>교체</strong>됩니다.
        남은 인원은 「나머지 자동 편성」에서 채워집니다.
      </p>
      <div
        ref={setNodeRef}
        className={`pre-assign-pool-body ${isOver ? 'pre-assign-pool-body-over' : ''}`}
      >
        {pool.length === 0 ? (
          <p className="pre-assign-pool-empty">모든 TP/TS가 배정되었습니다.</p>
        ) : (
          <>
            {tpPool.length > 0 && (
              <div className="member-group">
                <div className="member-group-label">팀장 (TP) · {tpPool.length}명</div>
                {tpPool.map((member, i) => (
                  <span key={member.employeeId} className="member-row">
                    <DropSlot teamId={PRE_ASSIGN_POOL_ID} index={i} />
                    <MemberCard
                      member={member}
                      teamId={PRE_ASSIGN_POOL_ID}
                      otherTeams={teams}
                      onOpenMoveMenu={(e, id, fromId) => {
                        const member = pool.find((m) => m.employeeId === id)
                        onOpenMoveMenu(
                          e,
                          id,
                          fromId,
                          teamTargets.map((t) => ({
                            teamId: t.teamId,
                            label:
                              member && isTP(member) && t.hasTp
                                ? `${t.label} (TP 교체)`
                                : t.label,
                          }))
                        )
                      }}
                    />
                  </span>
                ))}
              </div>
            )}
            {tsPool.length > 0 && (
              <div className="member-group">
                <div className="member-group-label">사무장 (TS) · {tsPool.length}명</div>
                {tsPool.map((member, i) => (
                  <span key={member.employeeId} className="member-row">
                    <DropSlot teamId={PRE_ASSIGN_POOL_ID} index={tpPool.length + i} />
                    <MemberCard
                      member={member}
                      teamId={PRE_ASSIGN_POOL_ID}
                      otherTeams={teams}
                      onOpenMoveMenu={(e, id, fromId) => {
                        const member = pool.find((m) => m.employeeId === id)
                        onOpenMoveMenu(
                          e,
                          id,
                          fromId,
                          teamTargets.map((t) => ({
                            teamId: t.teamId,
                            label:
                              member && isTP(member) && t.hasTp
                                ? `${t.label} (TP 교체)`
                                : t.label,
                          }))
                        )
                      }}
                    />
                  </span>
                ))}
              </div>
            )}
            <DropSlot teamId={PRE_ASSIGN_POOL_ID} index={pool.length} />
          </>
        )}
      </div>
    </aside>
  )
}

export function PreAssignTpTsBoard({
  teams,
  pool,
  onTeamsChange,
  onPoolChange,
}: PreAssignTpTsBoardProps) {
  const [moveMenu, setMoveMenu] = useState<MoveContextMenu | null>(null)
  const [draggingActive, setDraggingActive] = useState<Active | null>(null)
  const [draggingMember, setDraggingMember] = useState<CrewMember | null>(null)
  const [overTeamId, setOverTeamId] = useState<string | null>(null)
  const menuRef = useRef<HTMLDivElement>(null)
  const lastOverRef = useRef<Over | null>(null)

  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: { distance: 2 },
    })
  )

  useEffect(() => {
    if (!moveMenu) return
    const close = (e: MouseEvent) => {
      if (menuRef.current?.contains(e.target as Node)) return
      setMoveMenu(null)
    }
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [moveMenu])

  const handleMove = (
    employeeId: string,
    fromTeamId: string,
    toTeamId: string,
    toIndex?: number
  ) => {
    const next = applyPreAssignMove(teams, pool, employeeId, fromTeamId, toTeamId, toIndex)
    onTeamsChange(next.teams)
    onPoolChange(next.pool)
  }

  const handleDragEnd = (event: DragEndEvent) => {
    const { active } = event
    const over = event.over ?? lastOverRef.current
    lastOverRef.current = null
    setOverTeamId(null)
    setDraggingMember(null)
    if (!over) return
    const payload = active.data.current as { member: CrewMember; teamId: string } | undefined
    if (!payload) return
    const { toTeamId, toIndex } = resolveBoardDropTarget(String(over.id))
    if (payload.teamId === toTeamId && toIndex === undefined) return
    handleMove(payload.member.employeeId, payload.teamId, toTeamId, toIndex)
  }

  const handleDragOver = (over: Over | null) => {
    lastOverRef.current = over
    if (!over) {
      setOverTeamId(null)
      return
    }
    const id = String(over.id)
    if (id === PRE_ASSIGN_POOL_ID || id.startsWith(`${PRE_ASSIGN_POOL_ID}::`)) {
      setOverTeamId(PRE_ASSIGN_POOL_ID)
      return
    }
    if (id.includes('::')) {
      setOverTeamId(id.split('::')[0])
      return
    }
    setOverTeamId(id)
  }

  const overlayContent = (active: Active | null): React.ReactNode => {
    const payload = active?.data.current as { member: CrewMember; teamId: string } | undefined
    if (!payload) return null
    return <MemberCardPreview member={payload.member} />
  }

  return (
    <DndContext
      sensors={sensors}
      autoScroll
      onDragStart={({ active }) => {
        setDraggingActive(active)
        setDraggingMember(
          (active.data.current as { member: CrewMember } | undefined)?.member ?? null
        )
        lastOverRef.current = null
      }}
      onDragOver={({ over }) => handleDragOver(over)}
      onDragCancel={() => {
        setDraggingActive(null)
        setDraggingMember(null)
        lastOverRef.current = null
        setOverTeamId(null)
      }}
      onDragEnd={(e) => {
        setDraggingActive(null)
        handleDragEnd(e)
      }}
      collisionDetection={boardCollisionDetection}
    >
      <div className="pre-assign-layout">
        <UnassignedPool
          pool={pool}
          teams={teams}
          isDropHighlighted={overTeamId === PRE_ASSIGN_POOL_ID}
          onOpenMoveMenu={(e, employeeId, fromTeamId, targets) => {
            e.preventDefault()
            e.stopPropagation()
            setMoveMenu({
              clientX: e.clientX,
              clientY: e.clientY,
              employeeId,
              fromTeamId,
              targets: [
                ...(fromTeamId !== PRE_ASSIGN_POOL_ID
                  ? [{ teamId: PRE_ASSIGN_POOL_ID, label: '미배정으로 되돌리기' }]
                  : []),
                ...targets.filter((t) => t.teamId !== fromTeamId),
              ],
            })
          }}
        />
        <div className="team-board pre-assign-team-board">
          {teams.map((team) => {
            const dropHint = getPreAssignDropHint(teams, draggingMember, overTeamId)
            const isHighlighted = overTeamId === team.teamId
            const teamQualWarn = findTpTsQualViolations([team])[0]?.message
            return (
            <TeamColumn
              key={team.teamId}
              team={team}
              preAssignMode
              isDropHighlighted={isHighlighted}
              tpSwapTarget={isHighlighted && dropHint === 'swap'}
              qualWarn={teamQualWarn}
              otherTeams={teams.filter((t) => t.teamId !== team.teamId)}
              onMoveMember={handleMove}
              onOpenMoveMenu={(e, employeeId, fromTeamId, otherTeams) => {
                e.preventDefault()
                e.stopPropagation()
                const dragged =
                  pool.find((m) => m.employeeId === employeeId) ??
                  teams.flatMap((t) => t.members).find((m) => m.employeeId === employeeId)
                setMoveMenu({
                  clientX: e.clientX,
                  clientY: e.clientY,
                  employeeId,
                  fromTeamId,
                  targets: [
                    { teamId: PRE_ASSIGN_POOL_ID, label: '미배정으로 되돌리기' },
                    ...otherTeams.map((t) => {
                      const hasTp = t.members.some(isTP)
                      const swap = dragged && isTP(dragged) && hasTp
                      return {
                        teamId: t.teamId,
                        label: swap ? `${t.teamId} (TP 교체)` : t.teamId,
                      }
                    }),
                  ],
                })
              }}
            />
            )
          })}
        </div>
      </div>
      {moveMenu && createPortal(
        <div
          ref={menuRef}
          className="member-move-context-menu"
          style={{ left: moveMenu.clientX, top: moveMenu.clientY }}
        >
          <div className="member-move-dropdown-title">이동</div>
          {moveMenu.targets.map((t) => (
            <button
              key={t.teamId}
              type="button"
              className="member-move-dropdown-item"
              onClick={() => {
                handleMove(moveMenu.employeeId, moveMenu.fromTeamId, t.teamId)
                setMoveMenu(null)
              }}
            >
              {t.label}
            </button>
          ))}
        </div>,
        document.body
      )}
      {createPortal(
        <DragOverlay dropAnimation={null} zIndex={10000}>
          {overlayContent(draggingActive)}
        </DragOverlay>,
        document.body
      )}
    </DndContext>
  )
}
