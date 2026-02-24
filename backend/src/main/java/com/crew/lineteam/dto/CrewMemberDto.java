package com.crew.lineteam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private String positionCode; // Rank 컬럼: TP, TS 등 (팀장/선임 구분)
    private String gender;       // 성별 (M/F 또는 남/여)
    private String rank;         // FROM 컬럼: LJ/BX/RS (라인자격, TP/TS 규칙용)
    private String base;         // 근거지 BASE: SEL(서울), PUS(부산) 등
    private String line;         // Line 컬럼 (A411 등)
    private String status;      // 구분: 재직 등
    private String department;  // 부서 (선택)
    private String annc;        // ANNC 컬럼 (표시용)
    private String qualification; // Qualification 컬럼 (심사관 등, 표시용)

    /** 팀장(TP) 여부 - positionCode 또는 grade에 TP 포함 (대소문자 무관) */
    public boolean isTP() {
        if (positionCode != null && positionCode.toUpperCase().contains("TP")) return true;
        return grade != null && grade.toUpperCase().contains("TP");
    }

    /** 선임(TS) 여부 - positionCode 또는 grade에 TS 포함 (대소문자 무관) */
    public boolean isTS() {
        if (positionCode != null && positionCode.toUpperCase().contains("TS")) return true;
        return grade != null && grade.toUpperCase().contains("TS");
    }

    /** 남성 여부 */
    public boolean isMale() {
        if (gender == null) return false;
        return "M".equalsIgnoreCase(gender) || "남".equals(gender) || "남자".equals(gender);
    }

    /** 자격이 LJ인지 (라인팀 TP/TS 매칭 규칙용) */
    public boolean isLineQualificationLJ() {
        return "LJ".equalsIgnoreCase(normalizeLineQualification(rank));
    }

    /** 자격이 BX인지 */
    public boolean isLineQualificationBX() {
        return "BX".equalsIgnoreCase(normalizeLineQualification(rank));
    }

    /** 자격이 RS인지 */
    public boolean isLineQualificationRS() {
        return "RS".equalsIgnoreCase(normalizeLineQualification(rank));
    }

    /** 정규화된 라인자격: LJ, BX, RS 중 하나 또는 null */
    public String getLineQualification() {
        return normalizeLineQualification(rank);
    }

    private static String normalizeLineQualification(String r) {
        if (r == null || r.isBlank()) return null;
        String s = r.trim().toUpperCase();
        if (s.contains("LJ")) return "LJ";
        if (s.contains("BX")) return "BX";
        if (s.contains("RS")) return "RS";
        return null;
    }

    /**
     * 이 TS가 주어진 TP가 있는 팀에 배정 가능한지
     * - TP가 LJ이면 TS는 LJ, BX, RS 모두 가능
     * - TP가 BX 또는 RS이면 TS는 LJ만 가능
     */
    public boolean canBeTSInTeamWithTP(CrewMemberDto tp) {
        if (tp == null) return true;
        String tpQ = tp.getLineQualification();
        String myQ = getLineQualification();
        if (tpQ == null || myQ == null) return true;
        if (tp.isLineQualificationLJ()) return true;
        if (tp.isLineQualificationBX() || tp.isLineQualificationRS()) return isLineQualificationLJ();
        return true;
    }
}
