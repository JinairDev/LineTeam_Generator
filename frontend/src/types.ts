/** 승무원 리스트 Test.xlsx 기준: 사번, 이름, 성별, BASE, Rank(TP/TS), Line, 직급, 구분, 자격 */
export interface CrewMember {
  employeeId: string
  name: string
  grade: string
  positionCode?: string  // Rank 컬럼: TP, TS 등
  gender: string
  rank: string           // 자격: S/A/B/YY 방송자격
  base: string
  line?: string
  status?: string
  department?: string
}

export interface LineTeam {
  teamId: string
  base: string
  indexInBase: number
  members: CrewMember[]
}
