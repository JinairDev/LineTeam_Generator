package com.crew.lineteam.util;

import com.crew.lineteam.dto.CrewMemberDto;

import java.util.Locale;

/**
 * RANK(또는 직급) 문자열에서 TP, TS, FP, YY 토큰 추출.
 * 엑셀 RANK 컬럼 → {@link CrewMemberDto#getPositionCode()}.
 */
public final class RankTokenUtil {

    public static final String TP = "TP";
    public static final String TS = "TS";
    public static final String FP = "FP";
    public static final String YY = "YY";

    /** 긴 토큰·TS를 TP보다 먼저 검사 (부분 문자열 오인 방지) */
    private static final String[] RANK_TOKENS = {TS, TP, FP, YY};

    private RankTokenUtil() {}

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
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        for (String token : RANK_TOKENS) {
            if (upper.contains(token)) {
                return token;
            }
        }
        return null;
    }
}
