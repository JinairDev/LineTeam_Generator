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

    /** FROM(LJ/BX/RS) 균등분배 대상 인원 — 1차 배치 후 이동·스왑하지 않음 */
    public static boolean isFromDistributionMember(CrewMemberDto member) {
        if (member == null) {
            return false;
        }
        return isBalancedDistributionQualification(member.getLineQualification());
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
