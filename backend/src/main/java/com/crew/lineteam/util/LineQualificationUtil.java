package com.crew.lineteam.util;

import com.crew.lineteam.dto.CrewMemberDto;
import com.crew.lineteam.dto.LineTeamDto;

/**
 * FROM 컬럼 라인 자격(LJ, BX, RS) — 팀별 균등 분배·검증용.
 */
public final class LineQualificationUtil {

    public static final String LJ = "LJ";
    public static final String BX = "BX";
    public static final String RS = "RS";

    private LineQualificationUtil() {}

    public static boolean isBalancedDistributionQualification(String qualification) {
        return LJ.equals(qualification) || BX.equals(qualification) || RS.equals(qualification);
    }

    public static int countInTeam(LineTeamDto team, String qualification) {
        if (team == null || team.getMembers() == null || qualification == null) {
            return 0;
        }
        int count = 0;
        for (CrewMemberDto m : team.getMembers()) {
            if (qualification.equals(m.getLineQualification())) {
                count++;
            }
        }
        return count;
    }
}
