package com.crew.lineteam.service;

import com.crew.lineteam.dto.CrewMemberDto;
import com.crew.lineteam.dto.LineTeamDto;
import com.crew.lineteam.util.GradeTokenUtil;
import com.crew.lineteam.util.LineQualificationUtil;
import com.crew.lineteam.util.RankTokenUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * 1차 우선순위(FROM → RANK → 직급) 편성 후,
 * 팀당 편차가 {@link FpYyBalanceService#MAX_ALLOWED_SPREAD}를 넘는 항목을
 * 팀 간 스왑/이동으로 줄입니다.
 * <p>
 * FROM(LJ/BX/RS)은 1차 배치 결과를 <b>불변</b>으로 둡니다 — FROM 보정 없음, FROM 인원은 이동·스왑 제외.
 * 보정 순서: 직급 → RANK (FROM은 건드리지 않음).
 * <p>
 * 하드 제약: 같은 베이스만, TP는 이동하지 않음, TS는 대상 팀 TP 자격 규칙을 만족할 때만.
 * 엑셀 소속팀/GRP 사전 배정 인원({@code fixedEmployeeIds})도 이동·스왑하지 않습니다.
 */
@Service
public class EvennessCorrectionService {

    private static final int MAX_PASSES_PER_BASE = 400;

    public void correct(List<LineTeamDto> teams) {
        correct(teams, Set.of());
    }

    public void correct(List<LineTeamDto> teams, Set<String> fixedEmployeeIds) {
        if (teams == null || teams.isEmpty()) {
            return;
        }
        Set<String> fixed = fixedEmployeeIds != null ? fixedEmployeeIds : Set.of();
        Map<String, List<LineTeamDto>> byBase = teams.stream()
                .filter(t -> t != null && t.getBase() != null)
                .collect(Collectors.groupingBy(
                        t -> t.getBase().trim().toUpperCase(Locale.ROOT),
                        LinkedHashMap::new,
                        Collectors.toList()));

        for (List<LineTeamDto> baseTeams : byBase.values()) {
            correctBase(baseTeams, fixed);
        }
    }

    private void correctBase(List<LineTeamDto> teams, Set<String> fixedEmployeeIds) {
        if (teams == null || teams.size() < 2) {
            return;
        }
        for (int pass = 0; pass < MAX_PASSES_PER_BASE; pass++) {
            boolean improved = false;
            // FROM은 1차 배치 불변 — 직급 → RANK 순으로만 보정
            improved |= correctMetric(teams, fixedEmployeeIds, m -> GradeTokenUtil.PS.equals(m.getGradeToken()),
                    t -> GradeTokenUtil.countInTeam(t, GradeTokenUtil.PS));
            improved |= correctMetric(teams, fixedEmployeeIds, m -> GradeTokenUtil.AP.equals(m.getGradeToken()),
                    t -> GradeTokenUtil.countInTeam(t, GradeTokenUtil.AP));
            improved |= correctMetric(teams, fixedEmployeeIds, m -> GradeTokenUtil.SS.equals(m.getGradeToken()),
                    t -> GradeTokenUtil.countInTeam(t, GradeTokenUtil.SS));
            improved |= correctMetric(teams, fixedEmployeeIds, m -> GradeTokenUtil.INTERN.equals(m.getGradeToken()),
                    t -> GradeTokenUtil.countInTeam(t, GradeTokenUtil.INTERN));

            improved |= correctMetric(teams, fixedEmployeeIds, m -> RankTokenUtil.isToken(m, RankTokenUtil.FP),
                    t -> countRank(t, RankTokenUtil.FP));
            improved |= correctMetric(teams, fixedEmployeeIds, m -> RankTokenUtil.isToken(m, RankTokenUtil.YY),
                    t -> countRank(t, RankTokenUtil.YY));
            improved |= correctMetric(teams, fixedEmployeeIds, m -> RankTokenUtil.isToken(m, RankTokenUtil.TS_OJT),
                    t -> countRank(t, RankTokenUtil.TS_OJT));

            if (!improved) {
                break;
            }
        }
    }

    /**
     * 편차(max−min)가 허용치를 넘으면, 많은 팀 ↔ 적은 팀 스왑(또는 이동)으로 1회 개선.
     */
    private static boolean correctMetric(
            List<LineTeamDto> teams,
            Set<String> fixedEmployeeIds,
            Predicate<CrewMemberDto> hasToken,
            ToIntFunction<LineTeamDto> countFn) {
        int min = Integer.MAX_VALUE;
        int max = 0;
        for (LineTeamDto t : teams) {
            int c = countFn.applyAsInt(t);
            min = Math.min(min, c);
            max = Math.max(max, c);
        }
        if (max - min <= FpYyBalanceService.MAX_ALLOWED_SPREAD) {
            return false;
        }

        List<LineTeamDto> high = new ArrayList<>();
        List<LineTeamDto> low = new ArrayList<>();
        for (LineTeamDto t : teams) {
            int c = countFn.applyAsInt(t);
            if (c == max) high.add(t);
            if (c == min) low.add(t);
        }
        high.sort(Comparator.comparingInt(LineTeamDto::getMemberCount).reversed());
        low.sort(Comparator.comparingInt(LineTeamDto::getMemberCount));

        // 1) 스왑: 많은 팀의 토큰 보유자 ↔ 적은 팀의 비보유자
        for (LineTeamDto fromTeam : high) {
            List<CrewMemberDto> donors = movableWithToken(fromTeam, hasToken, fixedEmployeeIds);
            if (donors.isEmpty()) continue;
            for (LineTeamDto toTeam : low) {
                if (fromTeam == toTeam) continue;
                List<CrewMemberDto> receivers = movableWithoutToken(toTeam, hasToken, fixedEmployeeIds);
                for (CrewMemberDto donor : donors) {
                    for (CrewMemberDto receiver : receivers) {
                        if (!canPlaceInTeam(donor, toTeam, receiver) || !canPlaceInTeam(receiver, fromTeam, donor)) {
                            continue;
                        }
                        swap(fromTeam, donor, toTeam, receiver);
                        return true;
                    }
                }
            }
        }

        // 2) 이동: 스왑 불가 시 많은 팀 → 적은 팀으로 한 명 이동
        for (LineTeamDto fromTeam : high) {
            List<CrewMemberDto> donors = movableWithToken(fromTeam, hasToken, fixedEmployeeIds);
            for (CrewMemberDto donor : donors) {
                for (LineTeamDto toTeam : low) {
                    if (fromTeam == toTeam) continue;
                    if (!canPlaceInTeam(donor, toTeam, null)) continue;
                    fromTeam.getMembers().remove(donor);
                    toTeam.getMembers().add(donor);
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isFixed(CrewMemberDto m, Set<String> fixedEmployeeIds) {
        if (m == null || m.getEmployeeId() == null || fixedEmployeeIds == null || fixedEmployeeIds.isEmpty()) {
            return false;
        }
        return fixedEmployeeIds.contains(m.getEmployeeId());
    }

    private static List<CrewMemberDto> movableWithToken(
            LineTeamDto team, Predicate<CrewMemberDto> hasToken, Set<String> fixedEmployeeIds) {
        List<CrewMemberDto> out = new ArrayList<>();
        for (CrewMemberDto m : team.getMembers()) {
            if (m == null || m.isTP() || isFixed(m, fixedEmployeeIds)) continue;
            if (LineQualificationUtil.isFromDistributionMember(m)) continue;
            if (hasToken.test(m)) out.add(m);
        }
        // 일반 팀원 우선, TS는 나중 (자격 제약 때문에)
        out.sort(Comparator.comparingInt((CrewMemberDto m) -> m.isTS() ? 1 : 0));
        return out;
    }

    private static List<CrewMemberDto> movableWithoutToken(
            LineTeamDto team, Predicate<CrewMemberDto> hasToken, Set<String> fixedEmployeeIds) {
        List<CrewMemberDto> out = new ArrayList<>();
        for (CrewMemberDto m : team.getMembers()) {
            if (m == null || m.isTP() || isFixed(m, fixedEmployeeIds)) continue;
            if (LineQualificationUtil.isFromDistributionMember(m)) continue;
            if (!hasToken.test(m)) out.add(m);
        }
        out.sort(Comparator.comparingInt((CrewMemberDto m) -> m.isTS() ? 1 : 0));
        return out;
    }

    /**
     * {@code excluding} 멤버를 팀에서 잠깐 없다고 보고(스왑 상대), 대상 팀에 배치 가능한지.
     */
    private static boolean canPlaceInTeam(CrewMemberDto member, LineTeamDto targetTeam, CrewMemberDto excluding) {
        if (member == null || targetTeam == null) return false;
        if (member.isTP()) return false;
        if (!member.isTS()) return true;

        CrewMemberDto tp = null;
        for (CrewMemberDto m : targetTeam.getMembers()) {
            if (m == null || (excluding != null && excluding.getEmployeeId() != null
                    && excluding.getEmployeeId().equals(m.getEmployeeId()))) {
                continue;
            }
            if (m.isTP()) {
                tp = m;
                break;
            }
        }
        return member.canBeTSInTeamWithTP(tp);
    }

    private static void swap(LineTeamDto a, CrewMemberDto fromA, LineTeamDto b, CrewMemberDto fromB) {
        int ia = a.getMembers().indexOf(fromA);
        int ib = b.getMembers().indexOf(fromB);
        if (ia < 0 || ib < 0) return;
        a.getMembers().set(ia, fromB);
        b.getMembers().set(ib, fromA);
    }

    private static int countRank(LineTeamDto team, String token) {
        if (team == null || team.getMembers() == null) return 0;
        int n = 0;
        for (CrewMemberDto m : team.getMembers()) {
            if (RankTokenUtil.isToken(m, token)) n++;
        }
        return n;
    }
}
