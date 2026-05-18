package com.crew.lineteam.service;

import com.crew.lineteam.dto.AssignResponse;
import com.crew.lineteam.dto.CrewMemberDto;
import com.crew.lineteam.dto.FpYyBalanceReport;
import com.crew.lineteam.dto.LineTeamDto;
import com.crew.lineteam.dto.PinMode;
import com.crew.lineteam.util.RankTokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 라인팀 편성 알고리즘
 * - SEL / PUS 베이스는 라인 코드가 정해진 **고정 팀 수**만큼만 생성 (SEL 61, PUS 8), 전원을 그 팀들에만 배분
 * - 그 외 베이스는 TP 수 기준(최소 1팀)으로 팀 슬롯 생성
 * - TP/TS 자격 규칙: TP=LJ → TS는 LJ/BX/RS 가능, TP=BX/RS → TS는 LJ만
 * - TS 인원은 자격 규칙을 지키면서 팀별 TS 수가 균등하도록 배분(동률 팀은 무작위)
 * - TP·기타 인원은 팀별 인원이 최대한 맞도록 두되, 가장 적은 팀이 여럿이면 그중 무작위 배치(A101 순서 고정 없음)
 * - SEL·PUS 고정 팀은 인원 상한 없이 전원 배분(그 외 베이스는 팀당 최대 15명 목표)
 * - RANK FP·YY는 팀별 해당 인원 수 최소 팀에 우선 배치, 편성 후 균등 여부 검증
 * - RANK YY는 같은 베이스 내 YY가 남는 한 모든 팀에 최소 1명 배치 (YY 부족 시 일부 팀은 0명)
 */
@Service
@RequiredArgsConstructor
public class TeamAssignmentService {

    private final FpYyBalanceService fpYyBalanceService;

    private static final int MAX_TEAM_SIZE = 15;
    /** SEL/PUS 고정 라인팀: 인원 전원 배분을 위해 사실상 상한 없음 */
    private static final int UNLIMITED_TEAM = 1_000_000;

    public AssignResponse assignWithBalanceReport(List<CrewMemberDto> allCrew, Map<String, Integer> teamCountByBase) {
        List<LineTeamDto> teams = assign(allCrew, teamCountByBase);
        FpYyBalanceReport report = fpYyBalanceService.verify(teams);
        logFpYyBalanceReport(report);
        return AssignResponse.builder().teams(teams).fpYyBalance(report).build();
    }

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
        placeTpsBalancedRandom(teams, crew, assignedIds);

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

    /** 같은 베이스 팀에 TP를 배치: 인원이 가장 적은 팀이 여러 개면 그중 무작위(균등 + 잔여 랜덤) */
    private static void placeTpsBalancedRandom(List<LineTeamDto> teams, List<CrewMemberDto> allCrew, Set<String> assignedIds) {
        Map<String, List<LineTeamDto>> byBase = teams.stream().collect(Collectors.groupingBy(LineTeamDto::getBase));
        for (List<LineTeamDto> list : byBase.values()) {
            list.sort(Comparator.comparingInt(LineTeamDto::getIndexInBase));
        }
        List<CrewMemberDto> tps = allCrew.stream().filter(CrewMemberDto::isTP).collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(tps);
        for (CrewMemberDto tp : tps) {
            String base = normalizeBase(tp.getBase());
            List<LineTeamDto> baseTeams = byBase.get(base);
            if (baseTeams == null || baseTeams.isEmpty()) {
                continue;
            }
            LineTeamDto target = pickRandomTeamAmongMinLoadWithCapacity(baseTeams);
            if (target == null) {
                continue;
            }
            target.getMembers().add(tp);
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
    public AssignResponse assignWithBalanceReport(
            List<CrewMemberDto> allCrew,
            Map<String, Integer> teamCountByBase,
            List<LineTeamDto> previousTeams,
            PinMode pinMode) {
        List<LineTeamDto> teams = assign(allCrew, teamCountByBase, previousTeams, pinMode);
        FpYyBalanceReport report = fpYyBalanceService.verify(teams);
        logFpYyBalanceReport(report);
        return AssignResponse.builder().teams(teams).fpYyBalance(report).build();
    }

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
        Map<String, List<CrewMemberDto>> byBroadcastRank = otherPool.stream().collect(Collectors.groupingBy(c -> nullToDefault(c.getRank())));
        Map<String, List<CrewMemberDto>> byRankToken = otherPool.stream()
                .filter(c -> {
                    String t = c.getRankToken();
                    return RankTokenUtil.FP.equals(t) || RankTokenUtil.YY.equals(t);
                })
                .collect(Collectors.groupingBy(c -> c.getRankToken()));
        List<CrewMemberDto> flatPool = buildBalancedPool(otherPool, byGrade, byBroadcastRank, byRankToken);

        assignedIds.clear();
        teams.forEach(t -> t.getMembers().forEach(m -> assignedIds.add(m.getEmployeeId())));

        Map<String, List<LineTeamDto>> teamsByBase = teams.stream()
                .collect(Collectors.groupingBy(t -> normalizeBase(t.getBase()), LinkedHashMap::new, Collectors.toList()));

        List<CrewMemberDto> yyMembers = otherPool.stream()
                .filter(CrewMemberDto::isYY)
                .collect(Collectors.toCollection(ArrayList::new));
        ensureMinimumYyPerTeam(teamsByBase, yyMembers, assignedIds);

        for (CrewMemberDto c : flatPool) {
            if (assignedIds.contains(c.getEmployeeId())) continue;
            String base = normalizeBase(c.getBase());
            List<LineTeamDto> baseTeams = teamsByBase.get(base);
            if (baseTeams == null) baseTeams = teams;

            LineTeamDto team = pickTeamForMember(baseTeams, c);

            if (team == null) {
                team = pickTeamForMember(teams, c);
            }

            if (team != null) {
                team.getMembers().add(c);
                assignedIds.add(c.getEmployeeId());
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

                LineTeamDto team = pickTeamForMember(baseTeams, c);

                if (team == null) {
                    team = pickTeamForMember(teams, c);
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

    /**
     * 정원 미만인 팀만 대상으로, 그중 멤버 수가 최소인 팀들 가운데 하나를 무작위로 고릅니다.
     * 라운드로빈(A101→A102…)이 아니라 동률이면 랜덤이라 잔여 인원이 특정 팀 번호로 쏠리지 않습니다.
     */
    private static LineTeamDto pickRandomTeamAmongMinLoadWithCapacity(List<LineTeamDto> teams) {
        if (teams == null || teams.isEmpty()) return null;
        List<LineTeamDto> withCap = new ArrayList<>();
        for (LineTeamDto t : teams) {
            if (t.getMemberCount() < maxTeamSize(t)) withCap.add(t);
        }
        if (withCap.isEmpty()) return null;
        int minMembers = withCap.stream().mapToInt(LineTeamDto::getMemberCount).min().orElse(0);
        List<LineTeamDto> atMin = new ArrayList<>();
        for (LineTeamDto t : withCap) {
            if (t.getMemberCount() == minMembers) atMin.add(t);
        }
        return atMin.get(ThreadLocalRandom.current().nextInt(atMin.size()));
    }

    private static LineTeamDto findSmallestTeam(List<LineTeamDto> teams) {
        if (teams == null || teams.isEmpty()) return null;
        return teams.stream().min(Comparator.comparingInt(LineTeamDto::getMemberCount)).orElse(null);
    }

    /** 자격 규칙·정원을 만족하는 팀 중 TS 수 최소(동률이면 총원 최소)인 팀들 중 하나를 무작위로 반환 */
    private LineTeamDto findTeamWithFewestTS(List<LineTeamDto> teams, CrewMemberDto ts) {
        List<LineTeamDto> valid = new ArrayList<>();
        for (LineTeamDto t : teams) {
            if (t.getMemberCount() >= maxTeamSize(t)) continue;
            CrewMemberDto tp = t.getMembers().stream().filter(CrewMemberDto::isTP).findFirst().orElse(null);
            if (!ts.canBeTSInTeamWithTP(tp)) continue;
            valid.add(t);
        }
        if (valid.isEmpty()) return null;
        int bestTs = valid.stream()
                .mapToInt(t -> (int) t.getMembers().stream().filter(CrewMemberDto::isTS).count())
                .min()
                .orElse(0);
        List<LineTeamDto> fewestTs = new ArrayList<>();
        for (LineTeamDto t : valid) {
            int cnt = (int) t.getMembers().stream().filter(CrewMemberDto::isTS).count();
            if (cnt == bestTs) fewestTs.add(t);
        }
        int bestTotal = fewestTs.stream().mapToInt(LineTeamDto::getMemberCount).min().orElse(Integer.MAX_VALUE);
        List<LineTeamDto> ties = new ArrayList<>();
        for (LineTeamDto t : fewestTs) {
            if (t.getMemberCount() == bestTotal) ties.add(t);
        }
        return ties.get(ThreadLocalRandom.current().nextInt(ties.size()));
    }

    /**
     * 같은 베이스에서 YY가 없는 팀에 우선 1명씩 배치합니다.
     * YY 인원이 팀 수보다 적으면 가능한 팀까지만 채우고 나머지 팀은 YY 0명으로 둡니다.
     */
    private static void ensureMinimumYyPerTeam(
            Map<String, List<LineTeamDto>> teamsByBase,
            List<CrewMemberDto> yyMembers,
            Set<String> assignedIds) {
        Map<String, List<CrewMemberDto>> yyByBase = new LinkedHashMap<>();
        for (CrewMemberDto c : yyMembers) {
            if (c == null || assignedIds.contains(c.getEmployeeId())) {
                continue;
            }
            String base = normalizeBase(c.getBase());
            yyByBase.computeIfAbsent(base, k -> new ArrayList<>()).add(c);
        }
        for (List<CrewMemberDto> list : yyByBase.values()) {
            Collections.shuffle(list);
        }

        for (Map.Entry<String, List<LineTeamDto>> entry : teamsByBase.entrySet()) {
            List<LineTeamDto> baseTeams = entry.getValue();
            List<CrewMemberDto> available = yyByBase.getOrDefault(entry.getKey(), List.of());
            if (available.isEmpty() || baseTeams.isEmpty()) {
                continue;
            }

            List<LineTeamDto> needsYy = new ArrayList<>();
            for (LineTeamDto team : baseTeams) {
                if (countRankTokenInTeam(team, RankTokenUtil.YY) == 0) {
                    needsYy.add(team);
                }
            }
            needsYy.sort(Comparator.comparingInt(LineTeamDto::getMemberCount));

            for (LineTeamDto team : needsYy) {
                if (available.isEmpty()) {
                    break;
                }
                if (team.getMemberCount() >= maxTeamSize(team)) {
                    continue;
                }
                CrewMemberDto yy = available.remove(0);
                team.getMembers().add(yy);
                assignedIds.add(yy.getEmployeeId());
            }
        }
    }

    private static LineTeamDto pickTeamForMember(List<LineTeamDto> teams, CrewMemberDto member) {
        if (member == null) {
            return pickRandomTeamAmongMinLoadWithCapacity(teams);
        }
        String token = member.getRankToken();
        if (RankTokenUtil.FP.equals(token) || RankTokenUtil.YY.equals(token)) {
            LineTeamDto balanced = pickTeamForBalancedRankToken(teams, token);
            if (balanced != null) {
                return balanced;
            }
        }
        LineTeamDto team = pickRandomTeamAmongMinLoadWithCapacity(teams);
        if (team != null) {
            return team;
        }
        return findSmallestTeam(teams);
    }

    /**
     * 정원 미만 팀 중 해당 RANK 토큰(FP/YY) 인원이 가장 적은 팀을 선택 (동률이면 총원 최소 → 무작위).
     */
    private static LineTeamDto pickTeamForBalancedRankToken(List<LineTeamDto> teams, String rankToken) {
        if (teams == null || teams.isEmpty() || rankToken == null) {
            return null;
        }
        List<LineTeamDto> withCap = new ArrayList<>();
        for (LineTeamDto t : teams) {
            if (t.getMemberCount() < maxTeamSize(t)) {
                withCap.add(t);
            }
        }
        if (withCap.isEmpty()) {
            return null;
        }
        int minToken = withCap.stream()
                .mapToInt(t -> countRankTokenInTeam(t, rankToken))
                .min()
                .orElse(0);
        List<LineTeamDto> atMinToken = new ArrayList<>();
        for (LineTeamDto t : withCap) {
            if (countRankTokenInTeam(t, rankToken) == minToken) {
                atMinToken.add(t);
            }
        }
        int minMembers = atMinToken.stream().mapToInt(LineTeamDto::getMemberCount).min().orElse(0);
        List<LineTeamDto> ties = new ArrayList<>();
        for (LineTeamDto t : atMinToken) {
            if (t.getMemberCount() == minMembers) {
                ties.add(t);
            }
        }
        return ties.get(ThreadLocalRandom.current().nextInt(ties.size()));
    }

    private static int countRankTokenInTeam(LineTeamDto team, String rankToken) {
        if (team == null || team.getMembers() == null) {
            return 0;
        }
        int count = 0;
        for (CrewMemberDto m : team.getMembers()) {
            if (RankTokenUtil.isToken(m, rankToken)) {
                count++;
            }
        }
        return count;
    }

    private List<CrewMemberDto> buildBalancedPool(List<CrewMemberDto> others,
                                                  Map<String, List<CrewMemberDto>> byGrade,
                                                  Map<String, List<CrewMemberDto>> byBroadcastRank,
                                                  Map<String, List<CrewMemberDto>> byRankToken) {
        List<CrewMemberDto> result = new ArrayList<>();
        int maxRounds = 0;
        for (List<CrewMemberDto> list : byGrade.values()) maxRounds = Math.max(maxRounds, list.size());
        for (List<CrewMemberDto> list : byBroadcastRank.values()) maxRounds = Math.max(maxRounds, list.size());
        for (List<CrewMemberDto> list : byRankToken.values()) maxRounds = Math.max(maxRounds, list.size());

        Set<String> added = new HashSet<>();
        for (int r = 0; r < maxRounds; r++) {
            for (List<CrewMemberDto> list : byRankToken.values()) {
                if (r < list.size() && added.add(list.get(r).getEmployeeId())) {
                    result.add(list.get(r));
                }
            }
            for (List<CrewMemberDto> list : byGrade.values()) {
                if (r < list.size() && added.add(list.get(r).getEmployeeId())) {
                    result.add(list.get(r));
                }
            }
            for (List<CrewMemberDto> list : byBroadcastRank.values()) {
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

    private static void logFpYyBalanceReport(FpYyBalanceReport report) {
        if (report == null) {
            return;
        }
        if (report.isBalanced()) {
            System.out.println("[TeamAssignmentService] " + report.getSummary());
        } else {
            System.err.println("[TeamAssignmentService] " + report.getSummary());
        }
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
