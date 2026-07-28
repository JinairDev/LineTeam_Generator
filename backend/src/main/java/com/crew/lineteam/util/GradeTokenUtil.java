package com.crew.lineteam.util;

import com.crew.lineteam.dto.CrewMemberDto;
import com.crew.lineteam.dto.LineTeamDto;

import java.util.Locale;

/**
 * 직급 컬럼에서 PS, AP, SS, 인턴 토큰 추출 — 팀별 균등 분배·검증용.
 */
public final class GradeTokenUtil {

    public static final String PS = "PS";
    public static final String AP = "AP";
    public static final String SS = "SS";
    public static final String INTERN = "인턴";

    private static final String[] BALANCED_GRADES = {PS, AP, SS, INTERN};

    private GradeTokenUtil() {}

    public static boolean isBalancedDistributionGrade(String gradeToken) {
        if (gradeToken == null) {
            return false;
        }
        for (String g : BALANCED_GRADES) {
            if (g.equals(gradeToken)) {
                return true;
            }
        }
        return false;
    }

    public static String fromMember(CrewMemberDto member) {
        if (member == null) {
            return null;
        }
        return extract(member.getGrade(), member.getPositionCode());
    }

    public static boolean isToken(CrewMemberDto member, String gradeToken) {
        return gradeToken != null && gradeToken.equals(fromMember(member));
    }

    public static int countInTeam(LineTeamDto team, String gradeToken) {
        if (team == null || team.getMembers() == null || gradeToken == null) {
            return 0;
        }
        int count = 0;
        for (CrewMemberDto m : team.getMembers()) {
            if (isToken(m, gradeToken)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 직급·Rank 문자열에서 PS/AP/SS/인턴 중 하나 추출.
     * SP·PS·AP·SS 우선순위는 엑셀 정렬({@code ExcelService})과 동일하게 PS가 AP보다 우선.
     */
    private static String extract(String grade, String positionCode) {
        String g = grade == null ? "" : grade.trim();
        if (g.contains(INTERN)) {
            return INTERN;
        }
        String hay = (g + " " + (positionCode == null ? "" : positionCode)).toUpperCase(Locale.ROOT);
        if (hay.contains("INTERN")) {
            return INTERN;
        }
        String best = null;
        int bestOrder = Integer.MAX_VALUE;
        if (hay.contains(PS) && 1 < bestOrder) {
            best = PS;
            bestOrder = 1;
        }
        if (hay.contains(AP) && 2 < bestOrder) {
            best = AP;
            bestOrder = 2;
        }
        if (hay.contains(SS) && 3 < bestOrder) {
            best = SS;
        }
        return best;
    }
}
