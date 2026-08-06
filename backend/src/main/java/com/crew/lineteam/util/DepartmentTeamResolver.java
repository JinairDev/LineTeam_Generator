package com.crew.lineteam.util;

import com.crew.lineteam.dto.CrewMemberDto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 엑셀 「소속팀」·「GRP」·「(구)TM」 값을 라인팀/그룹으로 해석합니다.
 * <ul>
 *   <li>소속팀: {@code 101TP}, {@code A101} 등 → 특정 팀</li>
 *   <li>GRP: {@code 1}~{@code 5} → SEL A1xx~A5xx 그룹 내 임의 팀</li>
 * </ul>
 */
public final class DepartmentTeamResolver {

    private static final Pattern TEAM_NUMBER = Pattern.compile("(\\d+)");
    /** GRP 값: 1, 2, 1그룹, 그룹3 */
    private static final Pattern GROUP_ONLY = Pattern.compile(
            "^(?:([1-5])(?:그룹)?|그룹([1-5]))$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TEAM_ID_NUMBER = Pattern.compile("^[AB](\\d+)$", Pattern.CASE_INSENSITIVE);

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

    /** RAW GRP 문자열 (grp 필드, 없으면 importColumns의 GRP/그룹). 비어 있으면 null. */
    public static String inputGrp(CrewMemberDto m) {
        if (m == null) return null;
        String g = m.getGrp();
        if (g != null && !g.isBlank()) {
            return g.trim();
        }
        if (m.getImportColumns() != null) {
            for (String key : List.of("GRP", "Grp", "그룹")) {
                String fromImport = m.getImportColumns().get(key);
                if (fromImport != null && !fromImport.isBlank()) {
                    return fromImport.trim();
                }
            }
            for (Map.Entry<String, String> e : m.getImportColumns().entrySet()) {
                if (e.getKey() == null || e.getValue() == null || e.getValue().isBlank()) continue;
                String h = e.getKey().replace(" ", "").trim();
                if ("GRP".equalsIgnoreCase(h) || "그룹".equals(h)) {
                    return e.getValue().trim();
                }
            }
        }
        return null;
    }

    /** 소속팀 또는 GRP가 있어 사전 배정 대상이면 true. */
    public static boolean hasPrefillDepartment(CrewMemberDto m) {
        return inputDepartment(m) != null || inputGrp(m) != null;
    }

    /**
     * 엑셀 「(구)TM」 / 「구(TM)」 원문. 이전 소속 팀 표기(예: {@code 101TP}).
     */
    public static String inputLegacyTm(CrewMemberDto m) {
        if (m == null || m.getImportColumns() == null || m.getImportColumns().isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String> e : m.getImportColumns().entrySet()) {
            if (!isLegacyTmHeader(e.getKey())) continue;
            String v = e.getValue();
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    public static boolean isLegacyTmHeader(String header) {
        if (header == null || header.isBlank()) {
            return false;
        }
        String t = header.replace(" ", "").trim();
        if ("구(TM)".equalsIgnoreCase(t) || "(구)TM".equalsIgnoreCase(t)) {
            return true;
        }
        String u = t.toUpperCase(Locale.ROOT);
        return u.contains("구") && u.contains("TM");
    }

    /**
     * (구)TM이 가리키는 이전 팀 ID. 해석 실패 시 null.
     */
    public static String resolvePreviousTeamId(
            CrewMemberDto m,
            String normalizedBase,
            Collection<String> teamIdsInBase) {
        return resolveTeamId(inputLegacyTm(m), normalizedBase, teamIdsInBase);
    }

    /**
     * TP가 (구)TM으로 확인된 이전 팀에 다시 TP로 들어가면 안 될 때 true.
     */
    public static boolean isBlockedAsTpForPreviousTm(
            CrewMemberDto m,
            String candidateTeamId,
            String normalizedBase,
            Collection<String> teamIdsInBase) {
        if (m == null || !m.isTP() || candidateTeamId == null || candidateTeamId.isBlank()) {
            return false;
        }
        String prev = resolvePreviousTeamId(m, normalizedBase, teamIdsInBase);
        return prev != null && prev.equalsIgnoreCase(candidateTeamId.trim());
    }

    /**
     * GRP 컬럼 값 → 그룹 번호 1~5. 해석 실패 시 null.
     */
    public static Integer resolveGroupNumber(String grpRaw) {
        if (grpRaw == null || grpRaw.isBlank()) {
            return null;
        }
        String d = grpRaw.trim().replace(" ", "");
        // 엑셀 숫자 "1.0" 등
        if (d.matches("^[1-5](?:\\.0+)?$")) {
            return Integer.parseInt(d.substring(0, 1));
        }
        Matcher m = GROUP_ONLY.matcher(d);
        if (!m.matches()) {
            return null;
        }
        String g = m.group(1) != null ? m.group(1) : m.group(2);
        if (g == null || g.isBlank()) {
            return null;
        }
        return Integer.parseInt(g);
    }

    /** 팀 ID의 그룹 번호(백의 자리). {@code A201}→2, {@code B108}→1. 해석 실패 시 null. */
    public static Integer groupOfTeamId(String teamId) {
        if (teamId == null || teamId.isBlank()) {
            return null;
        }
        Matcher m = TEAM_ID_NUMBER.matcher(teamId.trim());
        if (!m.matches()) {
            return null;
        }
        int n = Integer.parseInt(m.group(1));
        if (n < 100) {
            return null;
        }
        return n / 100;
    }

    /** 해당 베이스 팀 ID 중 지정 그룹에 속하는 것만 (순서 유지). */
    public static List<String> filterTeamIdsByGroup(Collection<String> teamIdsInBase, int group) {
        List<String> out = new ArrayList<>();
        if (teamIdsInBase == null || group < 1 || group > 5) {
            return out;
        }
        for (String id : teamIdsInBase) {
            Integer g = groupOfTeamId(id);
            if (g != null && g == group) {
                out.add(id);
            }
        }
        return out;
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
