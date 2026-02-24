package com.crew.lineteam.service;

import com.crew.lineteam.dto.CrewMemberDto;
import com.crew.lineteam.dto.LineTeamDto;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 라인팀 편성 알고리즘
 * - 팀 수 = TP 인원 수 (지역별 TP 몇 명이면 팀이 그 개수만큼 생성, 고정 아님)
 * - TP/TS 자격 규칙: TP=LJ → TS는 LJ/BX/RS 가능, TP=BX/RS → TS는 LJ만
 * - TS 인원은 자격 규칙을 지키면서 팀별 TS 수가 균등하도록 배분 (항상 TS 수가 가장 적은 팀에 우선 배정)
 * - 팀당 11~15명 목표, 지역별 균등 배분
 */
@Service
public class TeamAssignmentService {

    private static final int MAX_TEAM_SIZE = 15;

    public List<LineTeamDto> assign(List<CrewMemberDto> allCrew, Map<String, Integer> teamCountByBase) {
        if (allCrew == null || allCrew.isEmpty()) return List.of();

        List<CrewMemberDto> tps = allCrew.stream().filter(CrewMemberDto::isTP).toList();
        List<CrewMemberDto> tss = allCrew.stream().filter(CrewMemberDto::isTS).toList();
        List<CrewMemberDto> others = allCrew.stream()
                .filter(c -> !c.isTP() && !c.isTS())
                .collect(Collectors.toList());

        Map<String, List<CrewMemberDto>> tpByBase = tps.stream().collect(Collectors.groupingBy(c -> normalizeBase(c.getBase())));
        List<LineTeamDto> teams = new ArrayList<>();
        List<CrewMemberDto> extraTps = new ArrayList<>();

        for (Map.Entry<String, List<CrewMemberDto>> e : tpByBase.entrySet()) {
            String base = e.getKey();
            List<CrewMemberDto> baseTps = new ArrayList<>(e.getValue());
            Collections.shuffle(baseTps);

            // 팀 수 = TP 인원 수 (무조건)
            int teamCount = baseTps.size();
            if (teamCount <= 0) continue;

            for (int i = 0; i < teamCount; i++) {
                List<CrewMemberDto> members = new ArrayList<>();
                members.add(baseTps.get(i));
                teams.add(LineTeamDto.builder()
                        .teamId(base + "-" + String.format("%02d", i + 1))
                        .base(base)
                        .indexInBase(i + 1)
                        .members(members)
                        .build());
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
        // 2) 나머지 TS를 규칙에 맞게 같은 BASE 팀에만 배정, 팀별 TS 수 균등 배분 (자격 규칙 준수)
        List<CrewMemberDto> tsRemaining = new ArrayList<>();
        tsRemaining.addAll(tsLj);
        tsRemaining.addAll(tsOther);
        Collections.shuffle(tsRemaining);
        List<CrewMemberDto> tsUnassigned = new ArrayList<>();
        for (CrewMemberDto ts : tsRemaining) {
            String base = normalizeBase(ts.getBase());
            List<LineTeamDto> baseTeams = teamsByBaseForTs.get(base);
            if (baseTeams == null) baseTeams = teams;
            LineTeamDto team = findTeamWithFewestTS(baseTeams, ts, MAX_TEAM_SIZE);
            if (team != null) team.getMembers().add(ts);
            else tsUnassigned.add(ts);
        }
        for (CrewMemberDto ts : tsUnassigned) {
            String base = normalizeBase(ts.getBase());
            List<LineTeamDto> baseTeams = teamsByBaseForTs.get(base);
            List<LineTeamDto> candidates = baseTeams != null ? baseTeams : teams;
            LineTeamDto team = findTeamWithFewestTS(candidates, ts, MAX_TEAM_SIZE);
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
            String base = normalizeBase(c.getBase());
            List<LineTeamDto> baseTeams = teamsByBase.get(base);
            if (baseTeams == null) baseTeams = teams;
            int startIdx = nextTeamIndexByBase.getOrDefault(base, 0);
            
            // 먼저 MAX_TEAM_SIZE 이하인 팀 찾기
            LineTeamDto team = findTeamWithCapacity(baseTeams, startIdx, MAX_TEAM_SIZE);
            
            // 같은 지역에 자리가 없으면 다른 지역 팀에도 배정 시도
            if (team == null) {
                team = findTeamWithCapacity(teams, 0, MAX_TEAM_SIZE);
            }
            
            // 그래도 없으면 팀 크기 제한을 완화하여 배정 (최대 20명까지)
            if (team == null) {
                team = findTeamWithCapacity(baseTeams, 0, MAX_TEAM_SIZE + 5);
            }
            
            // 여전히 없으면 모든 팀 중 가장 적은 인원의 팀에 배정
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

        // 모든 승무원이 배정되었는지 확인
        Set<String> allCrewIds = allCrew.stream().map(CrewMemberDto::getEmployeeId).collect(Collectors.toSet());
        Set<String> unassignedIds = new HashSet<>(allCrewIds);
        unassignedIds.removeAll(assignedIds);
        
        if (!unassignedIds.isEmpty()) {
            System.err.println(String.format("[TeamAssignmentService] 경고: %d명의 승무원이 배정되지 않았습니다.", unassignedIds.size()));
            
            // 배정 실패 원인 분석
            System.out.println(String.format("[TeamAssignmentService] 팀 현황: 총 %d개 팀", teams.size()));
            for (LineTeamDto team : teams) {
                System.out.println(String.format("  - %s: %d명 (최대: %d)", team.getTeamId(), team.getMemberCount(), MAX_TEAM_SIZE));
            }
            
            // 남은 승무원을 가능한 팀에 강제 배정
            final Set<String> finalUnassignedIds = new HashSet<>(unassignedIds); // final 복사본 생성
            List<CrewMemberDto> unassigned = allCrew.stream()
                    .filter(c -> finalUnassignedIds.contains(c.getEmployeeId()))
                    .collect(Collectors.toList());
            
            for (CrewMemberDto c : unassigned) {
                String base = nullToDefault(c.getBase());
                List<LineTeamDto> baseTeams = teamsByBase.get(base);
                if (baseTeams == null) baseTeams = teams;
                
                // 같은 지역 팀에 배정 시도 (크기 제한 완화)
                LineTeamDto team = findTeamWithCapacity(baseTeams, 0, MAX_TEAM_SIZE + 10);
                
                // 같은 지역에 없으면 다른 지역 팀에도 배정
                if (team == null) {
                    team = findTeamWithCapacity(teams, 0, MAX_TEAM_SIZE + 10);
                }
                
                // 그래도 없으면 가장 적은 인원의 팀에 배정
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
        
        // 최종 검증: 모든 승무원이 배정되었는지 확인
        allCrewIds = allCrew.stream().map(CrewMemberDto::getEmployeeId).collect(Collectors.toSet());
        unassignedIds = new HashSet<>(allCrewIds);
        unassignedIds.removeAll(assignedIds);
        if (!unassignedIds.isEmpty()) {
            System.err.println(String.format("[TeamAssignmentService] 심각: 여전히 %d명의 승무원이 배정되지 않았습니다: %s", 
                    unassignedIds.size(), unassignedIds));
        } else {
            System.out.println(String.format("[TeamAssignmentService] 성공: 모든 승무원(%d명)이 배정되었습니다.", allCrew.size()));
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

    /** 자격 규칙을 만족하고 여유 인원이 있는 팀 중, TS 수가 가장 적은 팀 반환 (TS 균등 배분용) */
    private LineTeamDto findTeamWithFewestTS(List<LineTeamDto> teams, CrewMemberDto ts, int maxSize) {
        LineTeamDto best = null;
        int bestTsCount = Integer.MAX_VALUE;
        int bestTotal = Integer.MAX_VALUE;
        for (LineTeamDto t : teams) {
            if (t.getMemberCount() >= maxSize) continue;
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
