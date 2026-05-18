/** 승무원 카드 표시: 사번·성별·직급 / 이름 / FROM·RANK·ANNC·Qualification (API 필드명과 일치) */
export interface CrewMember {
  employeeId: string
  name: string
  grade: string
  positionCode?: string  // RANK 컬럼 → TP, TS 등
  gender: string
  rank?: string          // Qualification(방송자격) - 엑셀 Qualification/자격 컬럼
  base: string
  line?: string
  status?: string
  department?: string
  fromColumn?: string    // FROM 컬럼 → LJ, BX, RS
  annc?: string          // ANNC 컬럼
  qualification?: string // Qualification(심사관 등) - 엑셀과 동일 이름
  /** 업로드 시트 헤더 → 셀 값 (엑셀 추출 시 입력과 동일 순서) */
  importColumns?: Record<string, string>
}

export interface LineTeam {
  /** SEL: A101~…, PUS: B101~… 등 백엔드 규칙 */
  teamId: string
  base: string
  indexInBase: number
  members: CrewMember[]
}

export interface FpYyTeamCount {
  teamId: string
  fpCount: number
  yyCount: number
}

export interface FpYyBaseBalance {
  base: string
  teamCount: number
  totalFp: number
  totalYy: number
  fpBalanced: boolean
  yyBalanced: boolean
  fpMinPerTeam: number
  fpMaxPerTeam: number
  yyMinPerTeam: number
  yyMaxPerTeam: number
  teamsWithoutYy: number
  yyMinimumCoverageMet: boolean
  teams: FpYyTeamCount[]
}

export interface FpYyBalanceReport {
  balanced: boolean
  summary: string
  bases: FpYyBaseBalance[]
}

export interface AssignResponse {
  teams: LineTeam[]
  fpYyBalance?: FpYyBalanceReport
}
