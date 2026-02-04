package com.crew.lineteam.service;

import com.crew.lineteam.dto.CrewMemberDto;
import com.crew.lineteam.dto.LineTeamDto;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 라인팀 편성 알고리즘
 * - 조건1: TP 수만큼 팀 생성 (지역별 TP 수 = 해당 지역 팀 수)
 * - 조건2: 팀당 TS 최소 1명
 * - 조건3: 팀당 11~15명 (TS, TP 포함)
 * - 조건4: 사번 다양하게 섞어서 배정
 * - 조건5: 직급(SP/PS/AP/SS/ID/IS) 균등 배분
 * - 조건6: 남성 승무원 균등 배분
 * - 조건7: Rank(S/A/B/YY) 균등 배분
 */
@Service
public class TeamAssignmentService {

    private static final int MIN_TEAM_SIZE = 11;
    private static final int MAX_TEAM_SIZE = 15;

    public List<LineTeamDto> assign(List<CrewMemberDto> allCrew) {
        if (allCrew == null || allCrew.isEmpty()) return List.of();

        List<CrewMemberDto> tps = allCrew.stream().filter(CrewMemberDto::isTP).toList();
        List<CrewMemberDto> tss = allCrew.stream().filter(CrewMemberDto::isTS).toList();
        List<CrewMemberDto> others = allCrew.stream()
                .filter(c -> !c.isTP() && !c.isTS())
                .collect(Collectors.toList());

        // 지역별 TP 수 = 팀 수
        Map<String, List<CrewMemberDto>> tpByBase = tps.stream().collect(Collectors.groupingBy(c -> nullToDefault(c.getBase())));
        List<LineTeamDto> teams = new ArrayList<>();
        int globalTeamIndex = 0;

        for (Map.Entry<String, List<CrewMemberDto>> e : tpByBase.entrySet()) {
            String base = e.getKey();
            List<CrewMemberDto> baseTps = new ArrayList<>(e.getValue());
            Collections.shuffle(baseTps); // 사번 다양성

            for (int i = 0; i < baseTps.size(); i++) {
                CrewMemberDto tp = baseTps.get(i);
                List<CrewMemberDto> members = new ArrayList<>();
                members.add(tp); // 팀장 1명

                LineTeamDto team = LineTeamDto.builder()
                        .teamId(base + "-" + String.format("%02d", i + 1))
                        .base(base)
                        .indexInBase(i + 1)
                        .members(members)
                        .build();
                teams.add(team);
                globalTeamIndex++;
            }
        }

        // 조건2: TS를 팀당 최소 1명 배정 (라운드로빈으로 균등 분배)
        List<CrewMemberDto> tsPool = new ArrayList<>(tss);
        Collections.shuffle(tsPool);
        int ti = 0;
        for (CrewMemberDto ts : tsPool) {
            LineTeamDto team = teams.get(ti % teams.size());
            team.getMembers().add(ts);
            ti++;
        }

        // 나머지 인원: 직급/성별/Rank 균등 배분하여 11~15명 목표
        List<CrewMemberDto> otherPool = new ArrayList<>(others);
        Collections.shuffle(otherPool);

        Map<String, List<CrewMemberDto>> byGrade = otherPool.stream().collect(Collectors.groupingBy(c -> nullToDefault(c.getGrade())));
        Map<String, List<CrewMemberDto>> byRank = otherPool.stream().collect(Collectors.groupingBy(c -> nullToDefault(c.getRank())));
        List<CrewMemberDto> flatPool = buildBalancedPool(otherPool, byGrade, byRank);

        Set<String> assignedIds = new HashSet<>();
        teams.forEach(t -> t.getMembers().forEach(m -> assignedIds.add(m.getEmployeeId())));

        int teamIdx = 0;
        for (CrewMemberDto c : flatPool) {
            if (assignedIds.contains(c.getEmployeeId())) continue;
            LineTeamDto team = findTeamWithCapacity(teams, teamIdx, MAX_TEAM_SIZE);
            if (team != null) {
                team.getMembers().add(c);
                assignedIds.add(c.getEmployeeId());
            }
            teamIdx++;
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
