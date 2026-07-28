package com.crew.lineteam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.crew.lineteam.util.GradeTokenUtil;
import com.crew.lineteam.util.RankTokenUtil;

import java.util.Map;

/**
 * 승무원 1명 정보 (엑셀/API 공통)
 * - 승무원 리스트 Test.xlsx 기준: 사번, 이름, 성별, BASE, Rank(TP/TS), Line, 직급, 구분, 자격(방송자격)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrewMemberDto {

    private String employeeId;   // 사번
    private String name;         // 이름
    private String grade;        // 직급 (객실3급 등 - 표시용)
    private String positionCode; // RANK 컬럼: TP, TS, FP, YY 등
    private String gender;       // 성별 (M/F 또는 남/여)
    private String rank;         // 자격 컬럼: 방송 자격 S/A/B/YY
    private String base;         // 근거지 BASE: SEL(서울), PUS(부산) 등
    private String line;         // Line 컬럼 (A411 등)
    private String status;      // 구분: 재직 등
    private String department;  // 부서 (선택)
    private String fromColumn;  // FROM 칼럼: LJ, RS, BX 중 하나 (필드명 from은 Lombok 빌더와 충돌 가능해 fromColumn 사용)
    private String annc;        // ANNC 칼럼 (표시용)
    private String qualification; // Qualification 칼럼 (심사관 등, 표시용)

    /** 업로드 시트 헤더 문자열 → 해당 행 셀 값(엑셀 추출 시 입력과 동일 열·순서) */
    private Map<String, String> importColumns;

    public String getFromColumn() { return fromColumn; }
    public void setFromColumn(String fromColumn) { this.fromColumn = fromColumn; }

    /** RANK 컬럼에서 추출한 토큰: TP, TS, TS OJT, FP, YY 중 하나 또는 null */
    public String getRankToken() {
        return RankTokenUtil.fromMember(this);
    }

    /** 직급 컬럼에서 추출한 토큰: PS, AP, SS, 인턴 중 하나 또는 null */
    public String getGradeToken() {
        return GradeTokenUtil.fromMember(this);
    }

    /** 팀장(TP) 여부 */
    public boolean isTP() {
        return RankTokenUtil.TP.equals(getRankToken())
                || containsTokenFallback(RankTokenUtil.TP);
    }

    /** 선임(TS) 여부 — TS OJT 포함 */
    public boolean isTS() {
        return RankTokenUtil.TS.equals(getRankToken())
                || RankTokenUtil.TS_OJT.equals(getRankToken())
                || containsTokenFallback(RankTokenUtil.TS);
    }

    public boolean isTsOjt() {
        return RankTokenUtil.TS_OJT.equals(getRankToken());
    }

    public boolean isFP() {
        return RankTokenUtil.FP.equals(getRankToken());
    }

    public boolean isYY() {
        return RankTokenUtil.YY.equals(getRankToken());
    }

    private boolean containsTokenFallback(String token) {
        if (positionCode != null && positionCode.toUpperCase().contains(token)) return true;
        return grade != null && grade.toUpperCase().contains(token);
    }

    /** 남성 여부 */
    public boolean isMale() {
        if (gender == null) return false;
        return "M".equalsIgnoreCase(gender) || "남".equals(gender) || "남자".equals(gender);
    }

    /** 자격이 LJ인지 (라인팀 TP/TS 매칭 규칙용) - FROM 필드 사용 */
    public boolean isLineQualificationLJ() {
        return "LJ".equalsIgnoreCase(normalizeFrom(fromColumn));
    }

    /** 자격이 BX인지 - FROM 필드 사용 */
    public boolean isLineQualificationBX() {
        return "BX".equalsIgnoreCase(normalizeFrom(fromColumn));
    }

    /** 자격이 RS인지 - FROM 필드 사용 */
    public boolean isLineQualificationRS() {
        return "RS".equalsIgnoreCase(normalizeFrom(fromColumn));
    }

    /** 정규화된 라인자격: LJ, BX, RS 중 하나 또는 null - FROM 필드 사용 */
    public String getLineQualification() {
        return normalizeFrom(fromColumn);
    }

    private static String normalizeFrom(String f) {
        if (f == null || f.isBlank()) return null;
        String s = f.trim().toUpperCase();
        if (s.contains("LJ")) return "LJ";
        if (s.contains("BX")) return "BX";
        if (s.contains("RS")) return "RS";
        return null;
    }

    /**
     * 이 TS가 주어진 TP가 있는 팀에 배정 가능한지 (FROM 칼럼 기준)
     * - TP가 LJ이면 TS는 LJ, BX, RS 모두 가능
     * - TP가 BX 또는 RS이면 TS는 LJ만 가능
     */
    public boolean canBeTSInTeamWithTP(CrewMemberDto tp) {
        if (tp == null) return true;
        String tpQ = tp.getLineQualification(); // FROM 칼럼에서 추출
        String myQ = getLineQualification(); // FROM 칼럼에서 추출
        if (tpQ == null || myQ == null) return true;
        if (tp.isLineQualificationLJ()) return true;
        if (tp.isLineQualificationBX() || tp.isLineQualificationRS()) return isLineQualificationLJ();
        return true;
    }

    /**
     * 라인 편성에서 제외할 재직상태(구분 컬럼).
     * 재직·휴직예정·단기휴직 등은 편성에 포함하고, 차출·휴직만 제외.
     */
    public boolean isExcludedFromLineAssignment() {
        if (status == null || status.isBlank()) return false;
        String s = status.trim();
        return "차출".equals(s) || "휴직".equals(s);
    }
}

