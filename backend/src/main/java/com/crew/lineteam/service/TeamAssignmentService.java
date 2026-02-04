package com.crew.lineteam.service;

import com.crew.lineteam.dto.CrewMemberDto;
import com.crew.lineteam.dto.LineTeamDto;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 라인팀 편성 알고리즘
 * - 조건1: 지역별 팀 수 = teamCountByBase 지정값 또는 지역별 TP 수
 * - 조건2: 팀당 TS 최소 1명 (TP/TS 자격 규칙: TP가 LJ면 TS는 LJ/BX/RS 가능, TP가 BX/RS면 TS는 LJ만)
 * - 조건3: 팀당 11~15명 (TS, TP 포함)
 * - 조건4~7: 직급/성별/Rank 균등 배분
 */
@Service
public class TeamAssignmentService {

    private static final int MIN_TEAM_SIZE = 11;
    private static final int MAX_TEAM_SIZE = 15;

    public List<LineTeamDto> assign(List<CrewMemberDto> allCrew, Map<String, Integer> teamCountByBase) {
        if (allCrew == null || allCrew.isEmpty()) return List.of();

        List<CrewMemberDto> tps = allCrew.stream().filter(CrewMemberDto::isTP).toList();
        List<CrewMemberDto> tss = allCrew.stream().filter(CrewMemberDto::isTS).toList();
        List<CrewMemberDto> others = allCrew.stream()
                .filter(c -> !c.isTP() && !c.isTS())
                .collect(Collectors.toList());

        Map<String, List<CrewMemberDto>> tpByBase = tps.stream().collect(Collectors.groupingBy(c -> nullToDefault(c.getBase())));
        List<LineTeamDto> teams = new ArrayList<>();
        List<CrewMemberDto> extraTps = new ArrayList<>();

        for (Map.Entry<String, List<CrewMemberDto>> e : tpByBase.entrySet()) {
            String base = e.getKey();
            List<CrewMemberDto> baseTps = new ArrayList<>(e.getValue());
            Collections.shuffle(baseTps);

            int teamCount = teamCountByBase != null && teamCountByBase.containsKey(base)
                    ? Math.max(1, teamCountByBase.get(base))
                    : baseTps.size();
            if (teamCount <= 0) teamCount = baseTps.size();

            for (int i = 0; i < teamCount; i++) {
                List<CrewMemberDto> members = new ArrayList<>();
                if (i < baseTps.size()) {
                    members.add(baseTps.get(i));
                }
                teams.add(LineTeamDto.builder()
                        .teamId(base + "-" + String.format("%02d", i + 1))
                        .base(base)
                        .indexInBase(i + 1)
                        .members(members)
                        .build());
            }
            if (baseTps.size() > teamCount) {
                for (int i = teamCount; i < baseTps.size(); i++) {
                    extraTps.add(baseTps.get(i));
                }
            }
        }
        // TP가 없는 지역도 teamCountByBase에 있으면 팀만 생성 (TP 없이)
        if (teamCountByBase != null) {
            for (Map.Entry<String, Integer> e : teamCountByBase.entrySet()) {
                String base = e.getKey();
                if (tpByBase.containsKey(base)) continue;
                int teamCount = Math.max(1, e.getValue());
                for (int i = 0; i < teamCount; i++) {
                    teams.add(LineTeamDto.builder()
                            .teamId(base + "-" + String.format("%02d", i + 1))
                            .base(base)
                            .indexInBase(i + 1)
                            .members(new ArrayList<>())
                            .build());
                }
            }
        }
        others.addAll(extraTps);

        // TS 배정: TP 자격 규칙 (TP=LJ → TS는 LJ/BX/RS 가능, TP=BX/RS → TS는 LJ만)
        List<CrewMemberDto> tsLj = tss.stream().filter(CrewMemberDto::isLineQualificationLJ).collect(Collectors.toList());
        List<CrewMemberDto> tsOther = tss.stream().filter(t -> !t.isLineQualificationLJ()).collect(Collectors.toList());
        Collections.shuffle(tsLj);
        Collections.shuffle(tsOther);

        Map<String, List<LineTeamDto>> teamsByBaseForTs = teams.stream().collect(Collectors.groupingBy(LineTeamDto::getBase));
        // 1) BX/RS TP 팀에는 LJ TS만 배정 (같은 BASE만), 1명씩
        for (LineTeamDto team : teams) {
            CrewMemberDto tp = team.getMembers().stream().filter(CrewMemberDto::isTP).findFirst().orElse(null);
            if (tp == null || !tp.isLineQualificationBX() && !tp.isLineQualificationRS()) continue;
            String base = team.getBase();
            CrewMemberDto toAdd = null;
            for (CrewMemberDto ts : tsLj) {
                if (nullToDefault(ts.getBase()).equals(base)) {
                    toAdd = ts;
                    break;
                }
            }
            if (toAdd != null) {
                tsLj.remove(toAdd);
                team.getMembers().add(toAdd);
            }
        }
        // 2) 나머지 TS를 규칙에 맞게 같은 BASE 팀에만 배정 (지역 내 라운드로빈)
        List<CrewMemberDto> tsRemaining = new ArrayList<>();
        tsRemaining.addAll(tsLj);
        tsRemaining.addAll(tsOther);
        Collections.shuffle(tsRemaining);
        Map<String, Integer> tsNextIdxByBase = new HashMap<>();
        List<CrewMemberDto> tsUnassigned = new ArrayList<>();
        for (CrewMemberDto ts : tsRemaining) {
            String base = nullToDefault(ts.getBase());
            List<LineTeamDto> baseTeams = teamsByBaseForTs.get(base);
            if (baseTeams == null) baseTeams = teams;
            int startIdx = tsNextIdxByBase.getOrDefault(base, 0);
            LineTeamDto team = null;
            for (int i = 0; i < baseTeams.size(); i++) {
                LineTeamDto t = baseTeams.get((startIdx + i) % baseTeams.size());
                CrewMemberDto tp = t.getMembers().stream().filter(CrewMemberDto::isTP).findFirst().orElse(null);
                if (ts.canBeTSInTeamWithTP(tp) && t.getMemberCount() < MAX_TEAM_SIZE) {
                    team = t;
                    tsNextIdxByBase.put(base, (startIdx + i + 1) % baseTeams.size());
                    break;
                }
            }
            if (team != null) team.getMembers().add(ts);
            else tsUnassigned.add(ts);
        }
        for (CrewMemberDto ts : tsUnassigned) {
            String base = nullToDefault(ts.getBase());
            List<LineTeamDto> baseTeams = teamsByBaseForTs.get(base);
            LineTeamDto team = baseTeams != null ? findTeamWithCapacity(baseTeams, 0, MAX_TEAM_SIZE) : findTeamWithCapacity(teams, 0, MAX_TEAM_SIZE);
            if (team != null) team.getMembers().add(ts);
        }

        // 나머지 인원: 지역(BASE)별로만 배정, 같은 지역 내에서는 팀에 균등 배분
        List<CrewMemberDto> otherPool = new ArrayList<>(others);
        Collections.shuffle(otherPool);

        Map<String, List<CrewMemberDto>> byGrade = otherPool.stream().collect(Collectors.groupingBy(c -> nullToDefault(c.getGrade())));
        Map<String, List<CrewMemberDto>> byRank = otherPool.stream().collect(Collectors.groupingBy(c -> nullToDefault(c.getRank())));
        List<CrewMemberDto> flatPool = buildBalancedPool(otherPool, byGrade, byRank);

        Set<String> assignedIds = new HashSet<>();
        teams.forEach(t -> t.getMembers().forEach(m -> assignedIds.add(m.getEmployeeId())));

        Map<String, List<LineTeamDto>> teamsByBase = teams.stream().collect(Collectors.groupingBy(LineTeamDto::getBase));
        Map<String, Integer> nextTeamIndexByBase = new HashMap<>();

        for (CrewMemberDto c : flatPool) {
            if (assignedIds.contains(c.getEmployeeId())) continue;
            String base = nullToDefault(c.getBase());
            List<LineTeamDto> baseTeams = teamsByBase.get(base);
            if (baseTeams == null) baseTeams = teams;
            int startIdx = nextTeamIndexByBase.getOrDefault(base, 0);
            LineTeamDto team = findTeamWithCapacity(baseTeams, startIdx, MAX_TEAM_SIZE);
            if (team != null) {
                team.getMembers().add(c);
                assignedIds.add(c.getEmployeeId());
                int foundIdx = baseTeams.indexOf(team);
                nextTeamIndexByBase.put(base, foundIdx >= 0 ? (foundIdx + 1) % baseTeams.size() : (startIdx + 1) % baseTeams.size());
            }
        }

        return teams;
    }

    private LineTeamDto findTeamWithCapacity(List<LineTeamDto> teams, int startIdx, int maxSize) {
        for (int i = 0; i < teams.size(); i++) {
            LineTeamDto t = teams.get((startIdx + i) % teams.size());
            if (t.getMemberCount() < maxSize) return t;
        }
        return null;
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
}
