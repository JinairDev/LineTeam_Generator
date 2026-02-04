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
    private String rank;         // 자격 컬럼: 방송 자격 S/A/B/YY
    private String base;         // 근거지 BASE: SEL(서울), PUS(부산) 등
    private String line;         // Line 컬럼 (A411 등)
    private String status;      // 구분: 재직 등
    private String department;  // 부서 (선택)

    /** 팀장(TP) 여부 - positionCode 또는 grade에 TP 포함 */
    public boolean isTP() {
        if (positionCode != null && positionCode.contains("TP")) return true;
        return grade != null && grade.contains("TP");
    }

    /** 선임(TS) 여부 - positionCode 또는 grade에 TS 포함 */
    public boolean isTS() {
        if (positionCode != null && positionCode.contains("TS")) return true;
        return grade != null && grade.contains("TS");
    }

    /** 남성 여부 */
    public boolean isMale() {
        if (gender == null) return false;
        return "M".equalsIgnoreCase(gender) || "남".equals(gender) || "남자".equals(gender);
    }
}
