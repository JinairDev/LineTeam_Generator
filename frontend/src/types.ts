/** 승무원 카드 표시: 사번·성별·직급 / 이름 / FROM·RANK·ANNC·Qualification */
export interface CrewMember {
  employeeId: string
  name: string
  grade: string
  positionCode?: string  // RANK: TP, TS 등
  gender: string
  rank: string           // FROM: LJ, BX, RS (라인자격)
  base: string
  line?: string
  status?: string
  department?: string
  annc?: string          // ANNC
  qualification?: string // Qualification (심사관 등)
}

export interface LineTeam {
  teamId: string
  base: string
  indexInBase: number
  members: CrewMember[]
}
