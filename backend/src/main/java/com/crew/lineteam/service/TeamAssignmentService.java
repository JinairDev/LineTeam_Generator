package com.crew.lineteam.service;

import com.crew.lineteam.dto.CrewMemberDto;
import com.crew.lineteam.dto.LineTeamDto;
import com.crew.lineteam.dto.PinMode;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 라인팀 편성 알고리즘
 * - SEL / PUS 베이스는 라인 코드가 정해진 **고정 팀 수**만큼만 생성 (SEL 61, PUS 8), 전원을 그 팀들에만 배분
 * - 그 외 베이스는 TP 수 기준(최소 1팀)으로 팀 슬롯 생성
 * - TP/TS 자격 규칙: TP=LJ → TS는 LJ/BX/RS 가능, TP=BX/RS → TS는 LJ만
 * - TS 인원은 자격 규칙을 지키면서 팀별 TS 수가 균등하도록 배분
 * - SEL·PUS 고정 팀은 인원 상한 없이 전원 배분(그 외 베이스는 팀당 최대 15명 목표)
 */
@Service
public class TeamAssignmentService {

    private static final int MAX_TEAM_SIZE = 15;
    /** SEL/PUS 고정 라인팀: 인원 전원 배분을 위해 사실상 상한 없음 */
    private static final int UNLIMITED_TEAM = 1_000_000;

    public List<LineTeamDto> assign(List<CrewMemberDto> allCrew, Map<String, Integer> teamCountByBase) {
        List<CrewMemberDto> crew = assignableCrewOnly(allCrew);
        if (crew.isEmpty()) return List.of();

        LinkedHashSet<String> basesOrdered = basesInAssignmentOrder(crew);
        List<LineTeamDto> teams = new ArrayList<>();

        for (String base : basesOrdered) {
            int shellCount = teamShellCountForBase(base, crew);
            for (int i = 1; i <= shellCount; i++) {
                teams.add(LineTeamDto.builder()
                        .teamId(LineTeamIdFormatter.teamIdFor(base, i))
                        .base(base)
                        .indexInBase(i)
                        .members(new ArrayList<>())
                        .build());
            }
        }

        Set<String> assignedIds = new HashSet<>();
        placeTpsRoundRobin(teams, crew, assignedIds);

        assignTsAndOthers(teams, crew, assignedIds);
        return teams;
    }

    /** 차출·휴직(구분)은 라인 편성 대상에서 제외 */
    private static List<CrewMemberDto> assignableCrewOnly(List<CrewMemberDto> allCrew) {
        if (allCrew == null) return List.of();
        return allCrew.stream()
                .filter(c -> c != null && !c.isExcludedFromLineAssignment())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** SEL → PUS → 나머지 베이스(가나다순) */
    private static LinkedHashSet<String> basesInAssignmentOrder(List<CrewMemberDto> allCrew) {
        Set<String> present = allCrew.stream().map(c -> normalizeBase(c.getBase())).collect(Collectors.toCollection(TreeSet::new));
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (present.contains("SEL")) out.add("SEL");
        if (present.contains("PUS")) out.add("PUS");
        present.stream().filter(b -> !"SEL".equals(b) && !"PUS".equals(b)).sorted().forEach(out::add);
        return out;
    }

    private static int teamShellCountForBase(String base, List<CrewMemberDto> allCrew) {
        if ("SEL".equals(base)) {
            return LineTeamIdFormatter.selTeamCount();
        }
        if ("PUS".equals(base)) {
            return LineTeamIdFormatter.pusTeamCount();
        }
        long tpCount = allCrew.stream()
                .filter(CrewMemberDto::isTP)
                .filter(c -> normalizeBase(c.getBase()).equals(base))
                .count();
        return (int) Math.max(1, tpCount);
    }

    /** 같은 베이스 고정 팀들에 TP를 라운드로빈으로 배치 */
    private static void placeTpsRoundRobin(List<LineTeamDto> teams, List<CrewMemberDto> allCrew, Set<String> assignedIds) {
        Map<String, List<LineTeamDto>> byBase = teams.stream().collect(Collectors.groupingBy(LineTeamDto::getBase));
        for (List<LineTeamDto> list : byBase.values()) {
            list.sort(Comparator.comparingInt(LineTeamDto::getIndexInBase));
        }
        Map<String, Integer> nextIdx = new HashMap<>();
        List<CrewMemberDto> tps = allCrew.stream().filter(CrewMemberDto::isTP).collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(tps);
        for (CrewMemberDto tp : tps) {
            String base = normalizeBase(tp.getBase());
            List<LineTeamDto> baseTeams = byBase.get(base);
            if (baseTeams == null || baseTeams.isEmpty()) {
                continue;
            }
            int i = nextIdx.getOrDefault(base, 0) % baseTeams.size();
            nextIdx.put(base, nextIdx.getOrDefault(base, 0) + 1);
            baseTeams.get(i).getMembers().add(tp);
            assignedIds.add(tp.getEmployeeId());
        }
    }

    private static int maxTeamSize(LineTeamDto t) {
        if ("SEL".equals(t.getBase()) || "PUS".equals(t.getBase())) {
            return UNLIMITED_TEAM;
        }
        return MAX_TEAM_SIZE;
    }

    /**
     * 직전 편성 결과를 기준으로 고정 범위만 두고 나머지를 다시 배치합니다.
     */
    public List<LineTeamDto> assign(
            List<CrewMemberDto> allCrew,
            Map<String, Integer> teamCountByBase,
            List<LineTeamDto> previousTeams,
            PinMode pinMode) {
        if (allCrew == null || allCrew.isEmpty()) return List.of();
        if (previousTeams == null || previousTeams.isEmpty() || pinMode == null) {
            return assign(allCrew, teamCountByBase);
        }

        List<CrewMemberDto> crew = assignableCrewOnly(allCrew);
        if (crew.isEmpty()) return List.of();

        Map<String, CrewMemberDto> byId = crew.stream()
                .collect(Collectors.toMap(CrewMemberDto::getEmployeeId, c -> c, (a, b) -> a));

        List<LineTeamDto> teams = new ArrayList<>();
        Set<String> assignedIds = new HashSet<>();

        for (LineTeamDto prev : previousTeams) {
            List<CrewMemberDto> keep = new ArrayList<>();
            for (CrewMemberDto m : prev.getMembers()) {
                CrewMemberDto c = byId.get(m.getEmployeeId());
                if (c == null) continue;
                if (pinMode == PinMode.BOTH_FIXED) {
                    if (c.isTP() || c.isTS()) keep.add(c);
                } else if (pinMode == PinMode.TP_FIXED) {
                    if (c.isTP()) keep.add(c);
                } else if (pinMode == PinMode.TS_FIXED) {
                    if (c.isTS()) keep.add(c);
                }
            }
            LineTeamDto team = LineTeamDto.builder()
                    .teamId(prev.getTeamId())
                    .base(prev.getBase())
                    .indexInBase(prev.getIndexInBase())
                    .members(new ArrayList<>(keep))
                    .build();
            teams.add(team);
            keep.forEach(x -> assignedIds.add(x.getEmployeeId()));
        }

        if (pinMode == PinMode.BOTH_FIXED) {
            assignOthersPhase(teams, crew);
            return teams;
        }

        if (pinMode == PinMode.TS_FIXED) {
            List<CrewMemberDto> tpPool = crew.stream()
                    .filter(CrewMemberDto::isTP)
                    .filter(c -> !assignedIds.contains(c.getEmployeeId()))
                    .collect(Collectors.toCollection(ArrayList::new));
            Collections.shuffle(tpPool);
            for (LineTeamDto team : teams) {
                boolean hasTp = team.getMembers().stream().anyMatch(CrewMemberDto::isTP);
                if (hasTp) continue;
                CrewMemberDto pick = pickTpForBase(tpPool, team.getBase());
                if (pick != null) {
                    team.getMembers().add(pick);
                    assignedIds.add(pick.getEmployeeId());
                }
            }
        }

        assignTsAndOthers(teams, crew, assignedIds);
        return teams;
    }

    private static CrewMemberDto pickTpForBase(List<CrewMemberDto> tpPool, String teamBase) {
        String nb = normalizeBase(teamBase);
        for (int i = 0; i < tpPool.size(); i++) {
            CrewMemberDto c = tpPool.get(i);
            if (normalizeBase(c.getBase()).equals(nb)) {
                return tpPool.remove(i);
            }
        }
        if (!tpPool.isEmpty()) {
            return tpPool.remove(0);
        }
        return null;
    }

    /**
     * 팀에 TP가 배치된 뒤 TS·기타 인원을 배정합니다. {@code assignedIds}에는 팀에 이미 들어간 인원이 포함되어야 합니다.
     */
    private void assignTsAndOthers(List<LineTeamDto> teams, List<CrewMemberDto> allCrew, Set<String> assignedIds) {
        List<CrewMemberDto> tss = allCrew.stream()
                .filter(CrewMemberDto::isTS)
                .filter(c -> !assignedIds.contains(c.getEmployeeId()))
                .collect(Collectors.toList());

        List<CrewMemberDto> tsLj = tss.stream().filter(CrewMemberDto::isLineQualificationLJ).collect(Collectors.toList());
        List<CrewMemberDto> tsOther = tss.stream().filter(t -> !t.isLineQualificationLJ()).collect(Collectors.toList());
        Collections.shuffle(tsLj);
        Collections.shuffle(tsOther);

        Map<String, List<LineTeamDto>> teamsByBaseForTs = teams.stream().collect(Collectors.groupingBy(LineTeamDto::getBase));
        for (LineTeamDto team : teams) {
            if (team.getMembers().stream().anyMatch(CrewMemberDto::isTS)) continue;
            CrewMemberDto tp = team.getMembers().stream().filter(CrewMemberDto::isTP).findFirst().orElse(null);
            if (tp == null || (!tp.isLineQualificationBX() && !tp.isLineQualificationRS())) continue;
            String base = team.getBase();
            CrewMemberDto toAdd = null;
            for (CrewMemberDto ts : tsLj) {
                if (normalizeBase(ts.getBase()).equals(base)) {
                    toAdd = ts;
                    break;
                }
            }
            if (toAdd != null) {
                tsLj.remove(toAdd);
                team.getMembers().add(toAdd);
            }
        }

        List<CrewMemberDto> tsRemaining = new ArrayList<>();
        tsRemaining.addAll(tsLj);
        tsRemaining.addAll(tsOther);
        Collections.shuffle(tsRemaining);
        List<CrewMemberDto> tsUnassigned = new ArrayList<>();
        for (CrewMemberDto ts : tsRemaining) {
            String base = normalizeBase(ts.getBase());
            List<LineTeamDto> baseTeams = teamsByBaseForTs.get(base);
            if (baseTeams == null) baseTeams = teams;
            LineTeamDto team = findTeamWithFewestTS(baseTeams, ts);
            if (team == null) {
                team = findSmallestTeam(baseTeams);
            }
            if (team != null) team.getMembers().add(ts);
            else tsUnassigned.add(ts);
        }
        for (CrewMemberDto ts : tsUnassigned) {
            String base = normalizeBase(ts.getBase());
            List<LineTeamDto> baseTeams = teamsByBaseForTs.get(base);
            List<LineTeamDto> candidates = baseTeams != null ? baseTeams : teams;
            LineTeamDto team = findTeamWithFewestTS(candidates, ts);
            if (team == null) {
                team = findSmallestTeam(candidates);
            }
            if (team != null) team.getMembers().add(ts);
        }

        assignOthersPhase(teams, allCrew);
    }

    /**
     * TP/TS를 제외한 인원만 팀에 배정합니다. 팀에 이미 들어 있는 TP/TS는 유지됩니다.
     */
    private void assignOthersPhase(List<LineTeamDto> teams, List<CrewMemberDto> allCrew) {
        Set<String> assignedIds = new HashSet<>();
        teams.forEach(t -> t.getMembers().forEach(m -> assignedIds.add(m.getEmployeeId())));

        List<CrewMemberDto> others = allCrew.stream()
                .filter(c -> !c.isTP() && !c.isTS())
                .collect(Collectors.toList());
        List<CrewMemberDto> otherPool = new ArrayList<>(others);
        Collections.shuffle(otherPool);

        Map<String, List<CrewMemberDto>> byGrade = otherPool.stream().collect(Collectors.groupingBy(c -> nullToDefault(c.getGrade())));
        Map<String, List<CrewMemberDto>> byRank = otherPool.stream().collect(Collectors.groupingBy(c -> nullToDefault(c.getRank())));
        List<CrewMemberDto> flatPool = buildBalancedPool(otherPool, byGrade, byRank);

        assignedIds.clear();
        teams.forEach(t -> t.getMembers().forEach(m -> assignedIds.add(m.getEmployeeId())));

        Map<String, List<LineTeamDto>> teamsByBase = teams.stream().collect(Collectors.groupingBy(LineTeamDto::getBase));
        Map<String, Integer> nextTeamIndexByBase = new HashMap<>();

        for (CrewMemberDto c : flatPool) {
            if (assignedIds.contains(c.getEmployeeId())) continue;
            String base = normalizeBase(c.getBase());
            List<LineTeamDto> baseTeams = teamsByBase.get(base);
            if (baseTeams == null) baseTeams = teams;
            int startIdx = nextTeamIndexByBase.getOrDefault(base, 0);

            LineTeamDto team = findTeamWithCapacity(baseTeams, startIdx);

            if (team == null) {
                team = findTeamWithCapacity(teams, 0);
            }

            if (team == null) {
                team = findSmallestTeam(baseTeams);
            }

            if (team == null && !teams.isEmpty()) {
                team = teams.stream()
                        .min(Comparator.comparingInt(LineTeamDto::getMemberCount))
                        .orElse(null);
            }

            if (team != null) {
                team.getMembers().add(c);
                assignedIds.add(c.getEmployeeId());
                int foundIdx = baseTeams.indexOf(team);
                nextTeamIndexByBase.put(base, foundIdx >= 0 ? (foundIdx + 1) % baseTeams.size() : (startIdx + 1) % baseTeams.size());
            }
        }

        Set<String> allCrewIds = allCrew.stream().map(CrewMemberDto::getEmployeeId).collect(Collectors.toSet());
        Set<String> unassignedIds = new HashSet<>(allCrewIds);
        unassignedIds.removeAll(assignedIds);

        if (!unassignedIds.isEmpty()) {
            System.err.println(String.format("[TeamAssignmentService] 경고: %d명의 승무원이 배정되지 않았습니다.", unassignedIds.size()));

            System.out.println(String.format("[TeamAssignmentService] 팀 현황: 총 %d개 팀", teams.size()));
            for (LineTeamDto team : teams) {
                System.out.println(String.format("  - %s: %d명 (최대: %d)", team.getTeamId(), team.getMemberCount(), MAX_TEAM_SIZE));
            }

            final Set<String> finalUnassignedIds = new HashSet<>(unassignedIds);
            List<CrewMemberDto> unassigned = allCrew.stream()
                    .filter(c -> finalUnassignedIds.contains(c.getEmployeeId()))
                    .collect(Collectors.toList());

            for (CrewMemberDto c : unassigned) {
                String base = nullToDefault(c.getBase());
                List<LineTeamDto> baseTeams = teamsByBase.get(base);
                if (baseTeams == null) baseTeams = teams;

                LineTeamDto team = findTeamWithCapacity(baseTeams, 0);

                if (team == null) {
                    team = findTeamWithCapacity(teams, 0);
                }

                if (team == null) {
                    team = findSmallestTeam(baseTeams);
                }

                if (team == null && !teams.isEmpty()) {
                    team = teams.stream()
                            .min(Comparator.comparingInt(LineTeamDto::getMemberCount))
                            .orElse(null);
                }

                if (team != null) {
                    team.getMembers().add(c);
                    assignedIds.add(c.getEmployeeId());
                    System.out.println(String.format("[TeamAssignmentService] 강제 배정: %s (%s) → %s",
                            c.getName(), c.getEmployeeId(), team.getTeamId()));
                } else {
                    System.err.println(String.format("[TeamAssignmentService] 심각: %s (%s)를 배정할 수 없습니다.",
                            c.getName(), c.getEmployeeId()));
                }
            }
        }

        allCrewIds = allCrew.stream().map(CrewMemberDto::getEmployeeId).collect(Collectors.toSet());
        unassignedIds = new HashSet<>(allCrewIds);
        unassignedIds.removeAll(assignedIds);
        if (!unassignedIds.isEmpty()) {
            System.err.println(String.format("[TeamAssignmentService] 심각: 여전히 %d명의 승무원이 배정되지 않았습니다: %s",
                    unassignedIds.size(), unassignedIds));
        } else {
            System.out.println(String.format("[TeamAssignmentService] 성공: 모든 승무원(%d명)이 배정되었습니다.", allCrew.size()));
        }
    }

    private LineTeamDto findTeamWithCapacity(List<LineTeamDto> teams, int startIdx) {
        for (int i = 0; i < teams.size(); i++) {
            LineTeamDto t = teams.get((startIdx + i) % teams.size());
            if (t.getMemberCount() < maxTeamSize(t)) return t;
        }
        return null;
    }

    private static LineTeamDto findSmallestTeam(List<LineTeamDto> teams) {
        if (teams == null || teams.isEmpty()) return null;
        return teams.stream().min(Comparator.comparingInt(LineTeamDto::getMemberCount)).orElse(null);
    }

    /** 자격 규칙을 만족하고 여유 인원이 있는 팀 중, TS 수가 가장 적은 팀 반환 (TS 균등 배분용) */
    private LineTeamDto findTeamWithFewestTS(List<LineTeamDto> teams, CrewMemberDto ts) {
        LineTeamDto best = null;
        int bestTsCount = Integer.MAX_VALUE;
        int bestTotal = Integer.MAX_VALUE;
        for (LineTeamDto t : teams) {
            if (t.getMemberCount() >= maxTeamSize(t)) continue;
            CrewMemberDto tp = t.getMembers().stream().filter(CrewMemberDto::isTP).findFirst().orElse(null);
            if (!ts.canBeTSInTeamWithTP(tp)) continue;
            long tsCount = t.getMembers().stream().filter(CrewMemberDto::isTS).count();
            int total = t.getMemberCount();
            if (tsCount < bestTsCount || (tsCount == bestTsCount && total < bestTotal)) {
                best = t;
                bestTsCount = (int) tsCount;
                bestTotal = total;
            }
        }
        return best;
    }

    private List<CrewMemberDto> buildBalancedPool(List<CrewMemberDto> others,
                                                  Map<String, List<CrewMemberDto>> byGrade,
                                                  Map<String, List<CrewMemberDto>> byRank) {
        List<CrewMemberDto> result = new ArrayList<>();
        int maxRounds = 0;
        for (List<CrewMemberDto> list : byGrade.values()) maxRounds = Math.max(maxRounds, list.size());
        for (List<CrewMemberDto> list : byRank.values()) maxRounds = Math.max(maxRounds, list.size());

        Set<String> added = new HashSet<>();
        for (int r = 0; r < maxRounds; r++) {
            for (List<CrewMemberDto> list : byGrade.values()) {
                if (r < list.size() && added.add(list.get(r).getEmployeeId())) {
                    result.add(list.get(r));
                }
            }
            for (List<CrewMemberDto> list : byRank.values()) {
                if (r < list.size() && added.add(list.get(r).getEmployeeId())) {
                    result.add(list.get(r));
                }
            }
        }
        for (CrewMemberDto c : others) {
            if (added.add(c.getEmployeeId())) result.add(c);
        }
        return result;
    }

    private static String nullToDefault(String s) {
        return s == null || s.isBlank() ? "(없음)" : s;
    }

    /** BASE 정규화: trim, 빈 값은 (없음), PUS/부산 포함→PUS·SEL/서울 포함→SEL (부분 일치·숫자 1→SEL 2→PUS) */
    private static String normalizeBase(String s) {
        if (s == null) return "(없음)";
        String v = s.trim().replace("\u00A0", " ").trim();
        if (v.isEmpty()) return "(없음)";
        String u = v.toUpperCase();
        if ("2".equals(v) || "02".equals(v) || "2.0".equals(v)) return "PUS";
        if ("1".equals(v) || "01".equals(v) || "1.0".equals(v)) return "SEL";
        if (u.contains("PUS") || v.contains("부산") || u.contains("BUSAN")) return "PUS";
        if (u.contains("SEL") || v.contains("서울") || u.contains("SEOUL")) return "SEL";
        if ("PUS".equals(u) || "부산".equals(v) || "BUSAN".equals(u)) return "PUS";
        if ("SEL".equals(u) || "서울".equals(v) || "SEOUL".equals(u)) return "SEL";
        return u;
    }
}
