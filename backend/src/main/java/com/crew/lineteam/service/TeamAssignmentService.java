package com.crew.lineteam.service;

import com.crew.lineteam.dto.AssignResponse;
import com.crew.lineteam.dto.CrewMemberDto;
import com.crew.lineteam.dto.FpYyBalanceReport;
import com.crew.lineteam.dto.LineTeamDto;
import com.crew.lineteam.dto.PinMode;
import com.crew.lineteam.dto.TeamShellsResponse;
import com.crew.lineteam.util.DepartmentTeamResolver;
import com.crew.lineteam.util.GradeTokenUtil;
import com.crew.lineteam.util.LineQualificationUtil;
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
 * - 엑셀 「소속팀」또는 「GRP」가 채워진 인원만 해당 팀/그룹에 사전 배정한 뒤 나머지를 자동 편성
 * - 엑셀 「(구)TM」이 가리키는 이전 팀에는 동일인을 TP로 다시 배정하지 않음
 * - TP/TS 자격 규칙: TP=LJ → TS는 LJ/BX/RS 가능, TP=BX/RS → TS는 LJ만
 * - TS 인원은 자격 규칙을 지키면서 팀별 TS 수가 균등하도록 배분(동률 팀은 무작위)
 * - TP·기타 인원은 팀별 인원이 최대한 맞도록 두되, 가장 적은 팀이 여럿이면 그중 무작위 배치(A101 순서 고정 없음)
 * - SEL·PUS 고정 팀은 인원 상한 없이 전원 배분(그 외 베이스는 팀당 최대 15명 목표)
 * - RANK FP·YY·TS OJT, FROM LJ·BX·RS, 직급 PS·AP·SS·인턴은 팀별 해당 인원 수 최소 팀에 우선 배치
 *   (한 명 배치 시: FROM → RANK → 직급 → 팀 총원)
 * - 1차 편성 후 편차(최대−최소)가 허용치(2명)를 넘는 항목은 직급·RANK만 팀 간 스왑/이동으로 보정 (FROM은 1차 결과 불변)
 * - RANK YY는 같은 베이스 내 YY가 남는 한 모든 팀에 최소 1명 배치 (YY 부족 시 일부 팀은 0명)
 */
@Service
@RequiredArgsConstructor
public class TeamAssignmentService {

    private final FpYyBalanceService fpYyBalanceService;
    private final EvennessCorrectionService evennessCorrectionService;

    private static final int MAX_TEAM_SIZE = 15;
    /** SEL/PUS 고정 라인팀: 인원 전원 배분을 위해 사실상 상한 없음 */
    private static final int UNLIMITED_TEAM = 1_000_000;

    public AssignResponse assignWithBalanceReport(List<CrewMemberDto> allCrew, Map<String, Integer> teamCountByBase) {
        AssignBundle bundle = assignBundle(allCrew, teamCountByBase);
        FpYyBalanceReport report = fpYyBalanceService.verify(bundle.teams());
        logFpYyBalanceReport(report);
        return AssignResponse.builder()
                .teams(bundle.teams())
                .fpYyBalance(report)
                .departmentSeededCount(bundle.departmentSeeded())
                .departmentSeedSkippedCount(bundle.departmentSeedSkipped())
                .build();
    }

    /**
     * 사전 TP/TS 배정용 팀 껍데기 생성. 소속팀/GRP가 있는 인원은 미리 넣습니다.
     */
    public TeamShellsResponse createTeamShells(List<CrewMemberDto> allCrew) {
        List<CrewMemberDto> crew = assignableCrewOnly(allCrew);
        if (crew.isEmpty()) {
            return TeamShellsResponse.builder()
                    .teams(List.of())
                    .departmentSeededCount(0)
                    .departmentSeedSkippedCount(0)
                    .build();
        }
        List<LineTeamDto> teams = buildEmptyTeamShells(crew);
        SeedResult seed = seedMembersFromDepartment(teams, crew, new HashSet<>(), new HashSet<>());
        return TeamShellsResponse.builder()
                .teams(teams)
                .departmentSeededCount(seed.seeded())
                .departmentSeedSkippedCount(seed.skipped())
                .build();
    }

    private AssignBundle assignBundle(List<CrewMemberDto> allCrew, Map<String, Integer> teamCountByBase) {
        List<CrewMemberDto> crew = assignableCrewOnly(allCrew);
        if (crew.isEmpty()) {
            return new AssignBundle(List.of(), 0, 0);
        }

        List<LineTeamDto> teams = buildEmptyTeamShells(crew);

        Set<String> assignedIds = new HashSet<>();
        Set<String> departmentPinnedIds = new HashSet<>();
        SeedResult seed = seedMembersFromDepartment(teams, crew, assignedIds, departmentPinnedIds);
        placeRemainingTpsForPinnedTeams(teams, crew, assignedIds);

        assignTsAndOthers(teams, crew, assignedIds);
        evennessCorrectionService.correct(teams, departmentPinnedIds);
        return new AssignBundle(teams, seed.seeded(), seed.skipped());
    }

    /** 하위 호환용 */
    public List<LineTeamDto> assign(List<CrewMemberDto> allCrew, Map<String, Integer> teamCountByBase) {
        return assignBundle(allCrew, teamCountByBase).teams();
    }

    /** 차출·휴직(구분)은 라인 편성 대상에서 제외 */
    private static List<CrewMemberDto> assignableCrewOnly(List<CrewMemberDto> allCrew) {
        if (allCrew == null) return List.of();
        return allCrew.stream()
                .filter(c -> c != null && !c.isExcludedFromLineAssignment())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static List<LineTeamDto> buildEmptyTeamShells(List<CrewMemberDto> crew) {
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
        return teams;
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

    /**
     * 소속팀(특정 팀) → 없으면 GRP(그룹) 순으로 사전 배정.
     * 둘 다 비어 있으면 시드하지 않음(이후 자동 배정). 팀당 TP 1명.
     */
    private static SeedResult seedMembersFromDepartment(
            List<LineTeamDto> teams,
            List<CrewMemberDto> crew,
            Set<String> assignedIds,
            Set<String> departmentPinnedIds) {
        if (teams == null || teams.isEmpty() || crew == null || crew.isEmpty()) {
            return new SeedResult(0, 0);
        }
        Map<String, LineTeamDto> byTeamId = new HashMap<>();
        Map<String, List<String>> teamIdsByBase = new HashMap<>();
        for (LineTeamDto t : teams) {
            if (t == null || t.getTeamId() == null) continue;
            byTeamId.put(t.getTeamId(), t);
            String base = normalizeBase(t.getBase());
            teamIdsByBase.computeIfAbsent(base, k -> new ArrayList<>()).add(t.getTeamId());
        }

        int seeded = 0;
        int skipped = 0;
        for (CrewMemberDto c : crew) {
            if (c == null || c.getEmployeeId() == null) continue;
            if (assignedIds.contains(c.getEmployeeId())) continue;
            String dept = DepartmentTeamResolver.inputDepartment(c);
            String grp = DepartmentTeamResolver.inputGrp(c);
            if (dept == null && grp == null) continue;

            String base = normalizeBase(c.getBase());
            List<String> idsInBase = teamIdsByBase.getOrDefault(base, List.of());
            LineTeamDto team = resolveSeedTeam(c, dept, grp, base, idsInBase, byTeamId);
            if (team == null) {
                skipped++;
                System.out.println(String.format(
                        "[TeamAssignmentService] 소속팀/GRP 미매칭 스킵: %s (%s) dept=%s grp=%s base=%s",
                        c.getEmployeeId(), c.getName(), dept, grp, base));
                continue;
            }
            String teamId = team.getTeamId();
            if (c.isTP() && team.getMembers().stream().anyMatch(CrewMemberDto::isTP)) {
                skipped++;
                System.out.println(String.format(
                        "[TeamAssignmentService] 소속팀/GRP TP 충돌 스킵: %s → %s (이미 TP 있음)",
                        c.getEmployeeId(), teamId));
                continue;
            }
            if (c.isTP() && DepartmentTeamResolver.isBlockedAsTpForPreviousTm(c, teamId, base, idsInBase)) {
                skipped++;
                System.out.println(String.format(
                        "[TeamAssignmentService] (구)TM 동일팀 TP 금지 스킵: %s → %s (이전=%s)",
                        c.getEmployeeId(), teamId, DepartmentTeamResolver.inputLegacyTm(c)));
                continue;
            }
            team.getMembers().add(c);
            assignedIds.add(c.getEmployeeId());
            departmentPinnedIds.add(c.getEmployeeId());
            seeded++;
        }
        if (seeded > 0 || skipped > 0) {
            System.out.println(String.format(
                    "[TeamAssignmentService] 소속팀/GRP 사전배정: %d명 배치, %d명 스킵", seeded, skipped));
        }
        return new SeedResult(seeded, skipped);
    }

    /**
     * ① 소속팀 있으면 해당 팀만. ② 소속팀 없고 GRP만 있으면 해당 그룹 내 균등 선택.
     */
    private static LineTeamDto resolveSeedTeam(
            CrewMemberDto c,
            String dept,
            String grp,
            String base,
            List<String> idsInBase,
            Map<String, LineTeamDto> byTeamId) {
        if (dept != null) {
            String teamId = DepartmentTeamResolver.resolveTeamId(dept, base, idsInBase);
            return teamId != null ? byTeamId.get(teamId) : null;
        }
        Integer group = DepartmentTeamResolver.resolveGroupNumber(grp);
        if (group == null) {
            return null;
        }
        List<String> groupIds = DepartmentTeamResolver.filterTeamIdsByGroup(idsInBase, group);
        if (groupIds.isEmpty()) {
            return null;
        }
        List<LineTeamDto> candidates = new ArrayList<>();
        for (String id : groupIds) {
            LineTeamDto t = byTeamId.get(id);
            if (t == null) continue;
            if (c.isTP() && t.getMembers().stream().anyMatch(CrewMemberDto::isTP)) continue;
            if (c.isTP() && DepartmentTeamResolver.isBlockedAsTpForPreviousTm(c, id, base, idsInBase)) continue;
            candidates.add(t);
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return pickTeamForMember(candidates, c);
    }

    private record SeedResult(int seeded, int skipped) {}

    private record AssignBundle(List<LineTeamDto> teams, int departmentSeeded, int departmentSeedSkipped) {}

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
        return assignWithBalanceReport(allCrew, teamCountByBase, previousTeams, pinMode, null);
    }

    public AssignResponse assignWithBalanceReport(
            List<CrewMemberDto> allCrew,
            Map<String, Integer> teamCountByBase,
            List<LineTeamDto> previousTeams,
            PinMode pinMode,
            List<String> pinnedEmployeeIds) {
        AssignBundle bundle = assignBundle(allCrew, teamCountByBase, previousTeams, pinMode, pinnedEmployeeIds);
        FpYyBalanceReport report = fpYyBalanceService.verify(bundle.teams());
        logFpYyBalanceReport(report);
        return AssignResponse.builder()
                .teams(bundle.teams())
                .fpYyBalance(report)
                .departmentSeededCount(bundle.departmentSeeded())
                .departmentSeedSkippedCount(bundle.departmentSeedSkipped())
                .build();
    }

    private AssignBundle assignBundle(
            List<CrewMemberDto> allCrew,
            Map<String, Integer> teamCountByBase,
            List<LineTeamDto> previousTeams,
            PinMode pinMode) {
        return assignBundle(allCrew, teamCountByBase, previousTeams, pinMode, null);
    }

    private AssignBundle assignBundle(
            List<CrewMemberDto> allCrew,
            Map<String, Integer> teamCountByBase,
            List<LineTeamDto> previousTeams,
            PinMode pinMode,
            List<String> pinnedEmployeeIds) {
        if (allCrew == null || allCrew.isEmpty()) {
            return new AssignBundle(List.of(), 0, 0);
        }
        if (previousTeams == null || previousTeams.isEmpty() || pinMode == null) {
            return assignBundle(allCrew, teamCountByBase);
        }

        List<CrewMemberDto> crew = assignableCrewOnly(allCrew);
        if (crew.isEmpty()) {
            return new AssignBundle(List.of(), 0, 0);
        }

        Map<String, CrewMemberDto> byId = crew.stream()
                .collect(Collectors.toMap(CrewMemberDto::getEmployeeId, c -> c, (a, b) -> a));
        Set<String> lockedIds = new HashSet<>();
        if (pinnedEmployeeIds != null) {
            for (String id : pinnedEmployeeIds) {
                if (id != null && !id.isBlank()) {
                    lockedIds.add(id.trim());
                }
            }
        }

        List<LineTeamDto> teams = new ArrayList<>();
        Set<String> assignedIds = new HashSet<>();
        Set<String> departmentPinnedIds = new HashSet<>();

        for (LineTeamDto prev : previousTeams) {
            List<CrewMemberDto> keep = new ArrayList<>();
            for (CrewMemberDto m : prev.getMembers()) {
                CrewMemberDto c = byId.get(m.getEmployeeId());
                if (c == null) continue;
                if (pinMode == PinMode.LOCKED_FIXED) {
                    if (lockedIds.contains(c.getEmployeeId()) || DepartmentTeamResolver.hasPrefillDepartment(c)) {
                        keep.add(c);
                    }
                } else if (pinMode == PinMode.BOTH_FIXED) {
                    // 사전배정 보드에 올라온 전원 유지(소속팀 시드·수동 TP/TS 포함)
                    keep.add(c);
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
            for (CrewMemberDto x : keep) {
                assignedIds.add(x.getEmployeeId());
                if (pinMode == PinMode.LOCKED_FIXED || DepartmentTeamResolver.hasPrefillDepartment(x)) {
                    departmentPinnedIds.add(x.getEmployeeId());
                }
            }
        }

        // 엑셀 소속팀이 있는데 아직 팀에 없는 인원 보강 (API 직접 호출 등)
        SeedResult seed = seedMembersFromDepartment(teams, crew, assignedIds, departmentPinnedIds);
        // 알림용: 이전 팀에서 유지된 소속팀 인원 + 이번 시드
        int seededForNotice = departmentPinnedIds.size();

        if (pinMode == PinMode.BOTH_FIXED || pinMode == PinMode.LOCKED_FIXED) {
            placeRemainingTpsForPinnedTeams(teams, crew, assignedIds);
            assignTsAndOthers(teams, crew, assignedIds);
            evennessCorrectionService.correct(teams, departmentPinnedIds);
            return new AssignBundle(teams, seededForNotice, seed.skipped());
        }

        if (pinMode == PinMode.TS_FIXED) {
            placeRemainingTpsForPinnedTeams(teams, crew, assignedIds);
        }

        assignTsAndOthers(teams, crew, assignedIds);
        evennessCorrectionService.correct(teams, departmentPinnedIds);
        return new AssignBundle(teams, seededForNotice, seed.skipped());
    }

    public List<LineTeamDto> assign(
            List<CrewMemberDto> allCrew,
            Map<String, Integer> teamCountByBase,
            List<LineTeamDto> previousTeams,
            PinMode pinMode) {
        return assignBundle(allCrew, teamCountByBase, previousTeams, pinMode, null).teams();
    }

    /**
     * 이미 고정된 TP는 유지하고, TP가 없는 팀에 미배정 TP를 채웁니다.
     * 팀이 모두 TP를 가진 뒤에도 TP가 남으면 기존 자동 편성과 같이 최소 인원 팀에 배치합니다.
     */
    private static void placeRemainingTpsForPinnedTeams(
            List<LineTeamDto> teams,
            List<CrewMemberDto> crew,
            Set<String> assignedIds) {
        List<CrewMemberDto> tpPool = crew.stream()
                .filter(CrewMemberDto::isTP)
                .filter(c -> !assignedIds.contains(c.getEmployeeId()))
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(tpPool);

        Map<String, List<String>> teamIdsByBase = teamIdsByBase(teams);

        for (LineTeamDto team : teams) {
            boolean hasTp = team.getMembers().stream().anyMatch(CrewMemberDto::isTP);
            if (hasTp) continue;
            CrewMemberDto pick = pickTpForTeamAvoidingPreviousTm(tpPool, team, teamIdsByBase);
            if (pick != null) {
                team.getMembers().add(0, pick);
                assignedIds.add(pick.getEmployeeId());
            }
        }

        Map<String, List<LineTeamDto>> byBase = teams.stream()
                .collect(Collectors.groupingBy(t -> normalizeBase(t.getBase()), LinkedHashMap::new, Collectors.toList()));
        while (!tpPool.isEmpty()) {
            CrewMemberDto tp = tpPool.remove(0);
            String base = normalizeBase(tp.getBase());
            List<LineTeamDto> baseTeams = byBase.get(base);
            if (baseTeams == null || baseTeams.isEmpty()) {
                baseTeams = teams;
            }
            List<LineTeamDto> allowed = filterTeamsAllowedForTp(baseTeams, tp, teamIdsByBase.getOrDefault(base, List.of()));
            if (allowed.isEmpty()) {
                System.err.println(String.format(
                        "[TeamAssignmentService] 경고: (구)TM으로 배치 가능한 팀이 없어 예외 배치 — %s prev=%s",
                        tp.getEmployeeId(), DepartmentTeamResolver.inputLegacyTm(tp)));
                allowed = baseTeams;
            }
            LineTeamDto target = pickTeamForMember(allowed, tp);
            if (target == null) {
                target = pickRandomTeamAmongMinLoadWithCapacity(allowed);
            }
            if (target == null) {
                continue;
            }
            target.getMembers().add(tp);
            assignedIds.add(tp.getEmployeeId());
        }
    }

    private static Map<String, List<String>> teamIdsByBase(List<LineTeamDto> teams) {
        Map<String, List<String>> map = new HashMap<>();
        if (teams == null) return map;
        for (LineTeamDto t : teams) {
            if (t == null || t.getTeamId() == null) continue;
            map.computeIfAbsent(normalizeBase(t.getBase()), k -> new ArrayList<>()).add(t.getTeamId());
        }
        return map;
    }

    private static List<LineTeamDto> filterTeamsAllowedForTp(
            List<LineTeamDto> teams,
            CrewMemberDto tp,
            List<String> teamIdsInBase) {
        if (teams == null || teams.isEmpty() || tp == null) {
            return List.of();
        }
        String base = normalizeBase(tp.getBase());
        List<LineTeamDto> out = new ArrayList<>();
        for (LineTeamDto t : teams) {
            if (t == null) continue;
            if (DepartmentTeamResolver.isBlockedAsTpForPreviousTm(tp, t.getTeamId(), base, teamIdsInBase)) {
                continue;
            }
            out.add(t);
        }
        return out;
    }

    /**
     * TP가 (구)TM 이전 팀에 배정되면 안 되는지 (수동 이동·검증용).
     */
    public static boolean isTpBlockedFromPreviousTm(
            CrewMemberDto member,
            String candidateTeamId,
            List<LineTeamDto> teams) {
        if (member == null || candidateTeamId == null || teams == null) {
            return false;
        }
        String base = normalizeBase(member.getBase());
        List<String> ids = new ArrayList<>();
        for (LineTeamDto t : teams) {
            if (t != null && t.getTeamId() != null && normalizeBase(t.getBase()).equals(base)) {
                ids.add(t.getTeamId());
            }
        }
        return DepartmentTeamResolver.isBlockedAsTpForPreviousTm(member, candidateTeamId, base, ids);
    }

    /** 베이스 일치 + (구)TM 이전 팀이 아닌 TP를 우선 선택 */
    private static CrewMemberDto pickTpForTeamAvoidingPreviousTm(
            List<CrewMemberDto> tpPool,
            LineTeamDto team,
            Map<String, List<String>> teamIdsByBase) {
        if (tpPool == null || tpPool.isEmpty() || team == null) {
            return null;
        }
        String nb = normalizeBase(team.getBase());
        List<String> ids = teamIdsByBase.getOrDefault(nb, List.of());
        for (int i = 0; i < tpPool.size(); i++) {
            CrewMemberDto c = tpPool.get(i);
            if (!normalizeBase(c.getBase()).equals(nb)) continue;
            if (DepartmentTeamResolver.isBlockedAsTpForPreviousTm(c, team.getTeamId(), nb, ids)) continue;
            return tpPool.remove(i);
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
            LineTeamDto team;
            if (ts.isTsOjt()) {
                team = findTeamForBalancedTsOjt(baseTeams, ts);
            } else {
                team = findTeamWithFewestTS(baseTeams, ts);
            }
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
            LineTeamDto team;
            if (ts.isTsOjt()) {
                team = findTeamForBalancedTsOjt(candidates, ts);
            } else {
                team = findTeamWithFewestTS(candidates, ts);
            }
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

        Map<String, List<CrewMemberDto>> byGradeToken = otherPool.stream()
                .filter(c -> GradeTokenUtil.isBalancedDistributionGrade(c.getGradeToken()))
                .collect(Collectors.groupingBy(CrewMemberDto::getGradeToken));
        Map<String, List<CrewMemberDto>> byBroadcastRank = otherPool.stream().collect(Collectors.groupingBy(c -> nullToDefault(c.getRank())));
        Map<String, List<CrewMemberDto>> byRankToken = otherPool.stream()
                .filter(c -> RankTokenUtil.isBalancedDistributionToken(c.getRankToken()))
                .collect(Collectors.groupingBy(CrewMemberDto::getRankToken));
        Map<String, List<CrewMemberDto>> byFrom = otherPool.stream()
                .filter(c -> LineQualificationUtil.isBalancedDistributionQualification(c.getLineQualification()))
                .collect(Collectors.groupingBy(CrewMemberDto::getLineQualification));
        List<CrewMemberDto> flatPool = buildBalancedPool(otherPool, byGradeToken, byBroadcastRank, byRankToken, byFrom);

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
        if (ties.isEmpty()) return null;
        String fromQ = ts.getLineQualification();
        if (LineQualificationUtil.isBalancedDistributionQualification(fromQ)) {
            LineTeamDto fromBalanced = pickTeamForBalancedLineQualification(ties, fromQ);
            if (fromBalanced != null) return fromBalanced;
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
        // 우선순위: FROM → RANK → 직급 → 팀 총원 (한 명에게는 하나만 적용)
        String fromQ = member.getLineQualification();
        if (LineQualificationUtil.isBalancedDistributionQualification(fromQ)) {
            LineTeamDto fromBalanced = pickTeamForBalancedLineQualification(teams, fromQ);
            if (fromBalanced != null) {
                return fromBalanced;
            }
        }
        String token = member.getRankToken();
        if (RankTokenUtil.isBalancedDistributionToken(token)) {
            LineTeamDto balanced = pickTeamForBalancedRankToken(teams, token);
            if (balanced != null) {
                return balanced;
            }
        }
        String gradeToken = member.getGradeToken();
        if (GradeTokenUtil.isBalancedDistributionGrade(gradeToken)) {
            LineTeamDto gradeBalanced = pickTeamForBalancedGradeToken(teams, gradeToken);
            if (gradeBalanced != null) {
                return gradeBalanced;
            }
        }
        LineTeamDto team = pickRandomTeamAmongMinLoadWithCapacity(teams);
        if (team != null) {
            return team;
        }
        return findSmallestTeam(teams);
    }

    /**
     * TS OJT: 자격 규칙을 만족하는 팀 중 TS OJT 인원이 가장 적은 팀에 배치.
     */
    private LineTeamDto findTeamForBalancedTsOjt(List<LineTeamDto> teams, CrewMemberDto ts) {
        if (teams == null || teams.isEmpty() || ts == null) {
            return null;
        }
        List<LineTeamDto> valid = new ArrayList<>();
        for (LineTeamDto t : teams) {
            if (t.getMemberCount() >= maxTeamSize(t)) continue;
            CrewMemberDto tp = t.getMembers().stream().filter(CrewMemberDto::isTP).findFirst().orElse(null);
            if (!ts.canBeTSInTeamWithTP(tp)) continue;
            valid.add(t);
        }
        if (valid.isEmpty()) {
            return null;
        }
        String fromQ = ts.getLineQualification();
        if (LineQualificationUtil.isBalancedDistributionQualification(fromQ)) {
            LineTeamDto fromBalanced = pickTeamForBalancedLineQualification(valid, fromQ);
            if (fromBalanced != null) {
                return fromBalanced;
            }
        }
        LineTeamDto balanced = pickTeamForBalancedRankToken(valid, RankTokenUtil.TS_OJT);
        if (balanced != null) {
            return balanced;
        }
        return findTeamWithFewestTS(valid, ts);
    }

    /**
     * 정원 미만 팀 중 해당 FROM 자격(LJ/BX/RS) 인원이 가장 적은 팀을 선택 (동률이면 총원 최소 → 무작위).
     */
    private static LineTeamDto pickTeamForBalancedLineQualification(List<LineTeamDto> teams, String qualification) {
        if (teams == null || teams.isEmpty() || qualification == null) {
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
        int minCount = withCap.stream()
                .mapToInt(t -> LineQualificationUtil.countInTeam(t, qualification))
                .min()
                .orElse(0);
        List<LineTeamDto> atMin = new ArrayList<>();
        for (LineTeamDto t : withCap) {
            if (LineQualificationUtil.countInTeam(t, qualification) == minCount) {
                atMin.add(t);
            }
        }
        int minMembers = atMin.stream().mapToInt(LineTeamDto::getMemberCount).min().orElse(0);
        List<LineTeamDto> ties = new ArrayList<>();
        for (LineTeamDto t : atMin) {
            if (t.getMemberCount() == minMembers) {
                ties.add(t);
            }
        }
        return ties.get(ThreadLocalRandom.current().nextInt(ties.size()));
    }

    /**
     * 정원 미만 팀 중 해당 RANK 토큰(FP/YY/TS OJT) 인원이 가장 적은 팀을 선택 (동률이면 총원 최소 → 무작위).
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

    /**
     * 정원 미만 팀 중 해당 직급 토큰(PS/AP/SS/인턴) 인원이 가장 적은 팀을 선택 (동률이면 총원 최소 → 무작위).
     */
    private static LineTeamDto pickTeamForBalancedGradeToken(List<LineTeamDto> teams, String gradeToken) {
        if (teams == null || teams.isEmpty() || gradeToken == null) {
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
        int minCount = withCap.stream()
                .mapToInt(t -> GradeTokenUtil.countInTeam(t, gradeToken))
                .min()
                .orElse(0);
        List<LineTeamDto> atMin = new ArrayList<>();
        for (LineTeamDto t : withCap) {
            if (GradeTokenUtil.countInTeam(t, gradeToken) == minCount) {
                atMin.add(t);
            }
        }
        int minMembers = atMin.stream().mapToInt(LineTeamDto::getMemberCount).min().orElse(0);
        List<LineTeamDto> ties = new ArrayList<>();
        for (LineTeamDto t : atMin) {
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
                                                  Map<String, List<CrewMemberDto>> byGradeToken,
                                                  Map<String, List<CrewMemberDto>> byBroadcastRank,
                                                  Map<String, List<CrewMemberDto>> byRankToken,
                                                  Map<String, List<CrewMemberDto>> byFrom) {
        List<CrewMemberDto> result = new ArrayList<>();
        int maxRounds = 0;
        for (List<CrewMemberDto> list : byGradeToken.values()) maxRounds = Math.max(maxRounds, list.size());
        for (List<CrewMemberDto> list : byBroadcastRank.values()) maxRounds = Math.max(maxRounds, list.size());
        for (List<CrewMemberDto> list : byRankToken.values()) maxRounds = Math.max(maxRounds, list.size());
        for (List<CrewMemberDto> list : byFrom.values()) maxRounds = Math.max(maxRounds, list.size());

        Set<String> added = new HashSet<>();
        for (int r = 0; r < maxRounds; r++) {
            for (List<CrewMemberDto> list : byRankToken.values()) {
                if (r < list.size() && added.add(list.get(r).getEmployeeId())) {
                    result.add(list.get(r));
                }
            }
            for (List<CrewMemberDto> list : byFrom.values()) {
                if (r < list.size() && added.add(list.get(r).getEmployeeId())) {
                    result.add(list.get(r));
                }
            }
            for (List<CrewMemberDto> list : byGradeToken.values()) {
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
