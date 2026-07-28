import React, { useState, useEffect, useRef, useMemo } from 'react'
import { createPortal } from 'react-dom'
import {
  DndContext,
  DragEndEvent,
  DragOverlay,
  PointerSensor,
  useSensor,
  useSensors,
} from '@dnd-kit/core'
import type { Active, Over } from '@dnd-kit/core'
import type { CrewMember, FpYyBalanceReport, LineTeam } from './types'
import { TeamColumn } from './TeamColumn'
import { MemberCardPreview } from './MemberCardPreview'
import { boardCollisionDetection, resolveBoardDropTarget } from './dndCollision'
import { getTeamMoveDropHint } from './teamMove'
import { isTP } from './rankToken'
import { buildTeamBalanceIssueMap } from './teamBalanceFlags'

interface TeamBoardProps {
  teams: LineTeam[]
  balanceReport?: FpYyBalanceReport | null
  onMoveMember: (
    employeeId: string,
    fromTeamId: string,
    toTeamId: string,
    toIndex?: number
  ) => void
}

type MoveContextMenu = {
  clientX: number
  clientY: number
  employeeId: string
  fromTeamId: string
  otherTeams: LineTeam[]
}

export function TeamBoard({ teams, balanceReport, onMoveMember }: TeamBoardProps) {
  const teamIssueMap = useMemo(
    () => buildTeamBalanceIssueMap(balanceReport ?? null),
    [balanceReport]
  )
  const [collapsedIds, setCollapsedIds] = useState<Set<string>>(() => new Set())
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

  const teamIdSet = useMemo(() => new Set(teams.map((t) => t.teamId)), [teams])
  const collapsedTeams = useMemo(
    () => teams.filter((t) => collapsedIds.has(t.teamId)),
    [teams, collapsedIds],
  )
  const activeTeams = useMemo(
    () => teams.filter((t) => !collapsedIds.has(t.teamId)),
    [teams, collapsedIds],
  )

  useEffect(() => {
    setCollapsedIds((prev) => {
      let changed = false
      const next = new Set<string>()
      for (const id of prev) {
        if (teamIdSet.has(id)) next.add(id)
        else changed = true
      }
      return changed || next.size !== prev.size ? next : prev
    })
  }, [teamIdSet])

  const collapseTeam = (teamId: string) => {
    setCollapsedIds((prev) => {
      if (prev.has(teamId)) return prev
      const next = new Set(prev)
      next.add(teamId)
      return next
    })
  }

  const expandTeam = (teamId: string) => {
    setCollapsedIds((prev) => {
      if (!prev.has(teamId)) return prev
      const next = new Set(prev)
      next.delete(teamId)
      return next
    })
  }

  const openMoveMenu = (
    e: React.MouseEvent,
    employeeId: string,
    fromTeamId: string,
    otherTeams: LineTeam[]
  ) => {
    e.preventDefault()
    e.stopPropagation()
    setMoveMenu({
      clientX: e.clientX,
      clientY: e.clientY,
      employeeId,
      fromTeamId,
      otherTeams,
    })
  }

  useEffect(() => {
    if (!moveMenu) return
    const close = (e: MouseEvent) => {
      if (menuRef.current?.contains(e.target as Node)) return
      setMoveMenu(null)
    }
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [moveMenu])

  const handleDragOver = (over: Over | null) => {
    lastOverRef.current = over
    if (!over) {
      setOverTeamId(null)
      return
    }
    const id = String(over.id)
    if (id.includes('::')) {
      setOverTeamId(id.split('::')[0])
      return
    }
    setOverTeamId(id)
  }

  const handleDragEnd = (event: DragEndEvent) => {
    const { active } = event
    const over = event.over ?? lastOverRef.current
    lastOverRef.current = null
    setOverTeamId(null)
    if (!over) return
    const payload = active.data.current as { member: CrewMember; teamId: string } | undefined
    if (!payload) return
    const { toTeamId, toIndex } = resolveBoardDropTarget(String(over.id))
    const isTpSwap =
      isTP(payload.member) &&
      getTeamMoveDropHint(teams, payload.member, toTeamId) === 'swap'
    if (payload.teamId === toTeamId && toIndex === undefined && !isTpSwap) return
    onMoveMember(
      payload.member.employeeId,
      payload.teamId,
      toTeamId,
      isTpSwap ? undefined : toIndex
    )
  }

  const overlayContent = (active: Active | null): React.ReactNode => {
    const payload = active?.data.current as { member: CrewMember; teamId: string } | undefined
    if (!payload) return null
    return <MemberCardPreview member={payload.member} />
  }

  const menuMember = moveMenu
    ? teams.flatMap((t) => t.members).find((m) => m.employeeId === moveMenu.employeeId)
    : null

  return (
    <DndContext
      sensors={sensors}
      autoScroll
      collisionDetection={boardCollisionDetection}
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
        setDraggingMember(null)
        handleDragEnd(e)
      }}
    >
      <div className="team-board-layout">
        {collapsedTeams.length > 0 && (
          <div className="team-board-dock" aria-label="접힌 팀">
            {collapsedTeams.map((team) => {
              const issues = teamIssueMap.get(team.teamId)
              const warn = Boolean(issues?.length)
              return (
                <button
                  key={team.teamId}
                  type="button"
                  className={`team-dock-bubble${warn ? ' team-dock-bubble--warn' : ''}`}
                  onClick={() => expandTeam(team.teamId)}
                  title="펼치기"
                >
                  <span className="team-dock-bubble-id">{team.teamId}</span>
                  <span className="team-dock-bubble-count">{team.members.length}</span>
                </button>
              )
            })}
          </div>
        )}
        <div className="team-board">
          {teamIssueMap.size > 0 && (
            <p className="team-board-balance-legend">
              주황 테두리 팀 — 균등 분배 편차 원인 (많음/적음/미배치)
            </p>
          )}
          {activeTeams.map((team) => {
            const dropHint = getTeamMoveDropHint(teams, draggingMember, overTeamId)
            const isHighlighted = overTeamId === team.teamId
            return (
              <TeamColumn
                key={team.teamId}
                team={team}
                otherTeams={teams.filter((t) => t.teamId !== team.teamId)}
                onMoveMember={onMoveMember}
                onOpenMoveMenu={openMoveMenu}
                isDropHighlighted={isHighlighted}
                tpSwapTarget={isHighlighted && dropHint === 'swap'}
                balanceIssues={teamIssueMap.get(team.teamId)}
                collapsible
                onCollapse={() => collapseTeam(team.teamId)}
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
          <div className="member-move-dropdown-title">다른 팀으로 이동</div>
          {moveMenu.otherTeams.map((t) => {
            const swap = menuMember && isTP(menuMember) && t.members.some(isTP)
            return (
              <button
                key={t.teamId}
                type="button"
                className="member-move-dropdown-item"
                onClick={() => {
                  onMoveMember(moveMenu.employeeId, moveMenu.fromTeamId, t.teamId)
                  setMoveMenu(null)
                }}
              >
                {t.teamId}
                {swap ? ' (TP 교체)' : ''}
                {' '}
                <span className="member-move-dropdown-count">({t.members.length}명)</span>
              </button>
            )
          })}
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
