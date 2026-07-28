package com.crew.lineteam.util;

import com.crew.lineteam.dto.CrewMemberDto;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 엑셀 「소속팀」({@link CrewMemberDto#getDepartment()}) 값을 라인팀 ID로 해석합니다.
 * 내보내기 형식({@code 101TP}, {@code 101DP} 등)과 {@code A101}/{@code B101} 직접 표기를 지원합니다.
 */
public final class DepartmentTeamResolver {

    private static final Pattern TEAM_NUMBER = Pattern.compile("(\\d+)");

    private DepartmentTeamResolver() {}

    /** RAW 소속팀 문자열 (department, 없으면 importColumns의 「소속팀」). 비어 있으면 null. */
    public static String inputDepartment(CrewMemberDto m) {
        if (m == null) return null;
        String d = m.getDepartment();
        if (d != null && !d.isBlank()) {
            return d.trim();
        }
        if (m.getImportColumns() != null) {
            String fromImport = m.getImportColumns().get("소속팀");
            if (fromImport != null && !fromImport.isBlank()) {
                return fromImport.trim();
            }
        }
        return null;
    }

    public static boolean hasPrefillDepartment(CrewMemberDto m) {
        return inputDepartment(m) != null;
    }

    /**
     * @param departmentRaw 소속팀 원문
     * @param normalizedBase SEL / PUS / 기타
     * @param teamIdsInBase  해당 베이스에 존재하는 팀 ID
     * @return 매칭된 teamId, 없으면 null
     */
    public static String resolveTeamId(
            String departmentRaw,
            String normalizedBase,
            Collection<String> teamIdsInBase) {
        if (departmentRaw == null || departmentRaw.isBlank() || teamIdsInBase == null || teamIdsInBase.isEmpty()) {
            return null;
        }
        String d = departmentRaw.trim().toUpperCase(Locale.ROOT).replace(" ", "");
        String base = normalizedBase == null ? "" : normalizedBase.trim().toUpperCase(Locale.ROOT);

        for (String id : teamIdsInBase) {
            if (id != null && id.equalsIgnoreCase(d)) {
                return id;
            }
        }

        // A101TP / B108DP 처럼 접두+숫자+직급
        Matcher prefixed = Pattern.compile("^([AB])(\\d+)").matcher(d);
        if (prefixed.find()) {
            String candidate = prefixed.group(1) + prefixed.group(2);
            for (String id : teamIdsInBase) {
                if (candidate.equalsIgnoreCase(id)) {
                    return id;
                }
            }
        }

        Matcher num = TEAM_NUMBER.matcher(d);
        if (!num.find()) {
            return null;
        }
        String number = num.group(1);

        if ("SEL".equals(base)) {
            String candidate = "A" + number;
            if (containsIgnoreCase(teamIdsInBase, candidate)) {
                return findExact(teamIdsInBase, candidate);
            }
        } else if ("PUS".equals(base)) {
            String candidate = "B" + number;
            if (containsIgnoreCase(teamIdsInBase, candidate)) {
                return findExact(teamIdsInBase, candidate);
            }
        } else {
            String padded = number.length() >= 2
                    ? number.substring(number.length() - 2)
                    : ("0" + number);
            String candidate = base + "-" + padded;
            if (containsIgnoreCase(teamIdsInBase, candidate)) {
                return findExact(teamIdsInBase, candidate);
            }
            for (String id : teamIdsInBase) {
                if (id != null && id.toUpperCase(Locale.ROOT).endsWith(number)) {
                    return id;
                }
            }
        }
        return null;
    }

    private static boolean containsIgnoreCase(Collection<String> ids, String candidate) {
        return findExact(ids, candidate) != null;
    }

    private static String findExact(Collection<String> ids, String candidate) {
        for (String id : ids) {
            if (id != null && id.equalsIgnoreCase(candidate)) {
                return id;
            }
        }
        return null;
    }
}
