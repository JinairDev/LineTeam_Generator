package com.crew.lineteam.util;

import com.crew.lineteam.dto.CrewMemberDto;

import java.util.Locale;

/**
 * RANK(또는 직급) 문자열에서 TP, TS, TS OJT, FP, YY 토큰 추출.
 * 엑셀 RANK 컬럼 → {@link CrewMemberDto#getPositionCode()}.
 */
public final class RankTokenUtil {

    public static final String TP = "TP";
    public static final String TS = "TS";
    /** RANK 값 "TS OJT" (TS보다 먼저 매칭) */
    public static final String TS_OJT = "TS OJT";
    public static final String FP = "FP";
    public static final String YY = "YY";

    /** TS OJT 다음 TS·TP 순 (부분 문자열 오인 방지) */
    private static final String[] RANK_TOKENS_AFTER_OJT = {TS, TP, FP, YY};

    private RankTokenUtil() {}

    public static boolean isBalancedDistributionToken(String token) {
        return FP.equals(token) || YY.equals(token) || TS_OJT.equals(token);
    }

    public static String extract(String positionCode, String grade) {
        String fromRank = extractFromText(positionCode);
        if (fromRank != null) {
            return fromRank;
        }
        return extractFromText(grade);
    }

    public static String fromMember(CrewMemberDto member) {
        if (member == null) {
            return null;
        }
        return extract(member.getPositionCode(), member.getGrade());
    }

    public static boolean isToken(CrewMemberDto member, String token) {
        return token != null && token.equals(fromMember(member));
    }

    private static String extractFromText(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String upper = raw.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (upper.contains("TS OJT") || upper.contains("TSOJT")) {
            return TS_OJT;
        }
        for (String token : RANK_TOKENS_AFTER_OJT) {
            if (upper.contains(token)) {
                return token;
            }
        }
        return null;
    }
}
