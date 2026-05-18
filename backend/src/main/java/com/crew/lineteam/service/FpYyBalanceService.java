package com.crew.lineteam.service;

import com.crew.lineteam.dto.CrewMemberDto;
import com.crew.lineteam.dto.FpYyBalanceReport;
import com.crew.lineteam.dto.LineTeamDto;
import com.crew.lineteam.util.RankTokenUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RANK FP·YY 인원의 팀별 균등 분배 검증.
 * 균등 기준: 동일 베이스 내 팀별 인원 수의 최대−최소가 1 이하 (인원 0명인 팀 포함).
 */
@Service
public class FpYyBalanceService {

    public FpYyBalanceReport verify(List<LineTeamDto> teams) {
        if (teams == null || teams.isEmpty()) {
            return FpYyBalanceReport.builder()
                    .balanced(true)
                    .summary("편성된 팀이 없습니다.")
                    .bases(List.of())
                    .build();
        }

        Map<String, List<LineTeamDto>> byBase = teams.stream()
                .filter(t -> t != null && t.getBase() != null)
                .collect(Collectors.groupingBy(
                        t -> t.getBase().trim().toUpperCase(Locale.ROOT),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<FpYyBalanceReport.BaseBalance> baseReports = new ArrayList<>();
        boolean allBalanced = true;

        for (Map.Entry<String, List<LineTeamDto>> entry : byBase.entrySet()) {
            FpYyBalanceReport.BaseBalance baseReport = verifyBase(entry.getKey(), entry.getValue());
            baseReports.add(baseReport);
            if (!baseReport.isFpBalanced() || !baseReport.isYyBalanced() || !baseReport.isYyMinimumCoverageMet()) {
                allBalanced = false;
            }
        }

        String summary = buildSummary(allBalanced, baseReports);
        return FpYyBalanceReport.builder()
                .balanced(allBalanced)
                .summary(summary)
                .bases(baseReports)
                .build();
    }

    private static FpYyBalanceReport.BaseBalance verifyBase(String base, List<LineTeamDto> baseTeams) {
        List<LineTeamDto> sorted = new ArrayList<>(baseTeams);
        sorted.sort(Comparator.comparing(LineTeamDto::getTeamId, Comparator.nullsLast(String::compareTo)));

        List<FpYyBalanceReport.TeamCount> teamCounts = new ArrayList<>();
        int totalFp = 0;
        int totalYy = 0;
        int fpMin = Integer.MAX_VALUE;
        int fpMax = 0;
        int yyMin = Integer.MAX_VALUE;
        int yyMax = 0;

        for (LineTeamDto team : sorted) {
            int fp = countRankToken(team, RankTokenUtil.FP);
            int yy = countRankToken(team, RankTokenUtil.YY);
            totalFp += fp;
            totalYy += yy;
            fpMin = Math.min(fpMin, fp);
            fpMax = Math.max(fpMax, fp);
            yyMin = Math.min(yyMin, yy);
            yyMax = Math.max(yyMax, yy);
            teamCounts.add(FpYyBalanceReport.TeamCount.builder()
                    .teamId(team.getTeamId())
                    .fpCount(fp)
                    .yyCount(yy)
                    .build());
        }

        if (sorted.isEmpty()) {
            fpMin = 0;
            yyMin = 0;
        }

        boolean fpBalanced = totalFp == 0 || (fpMax - fpMin <= 1);
        boolean yyBalanced = totalYy == 0 || (yyMax - yyMin <= 1);
        int teamsWithoutYy = (int) sorted.stream()
                .filter(t -> countRankToken(t, RankTokenUtil.YY) == 0)
                .count();
        boolean yyMinimumCoverageMet = totalYy == 0
                || (totalYy >= sorted.size() ? teamsWithoutYy == 0 : teamsWithoutYy == sorted.size() - totalYy);

        return FpYyBalanceReport.BaseBalance.builder()
                .base(base)
                .teamCount(sorted.size())
                .totalFp(totalFp)
                .totalYy(totalYy)
                .fpBalanced(fpBalanced)
                .yyBalanced(yyBalanced)
                .fpMinPerTeam(totalFp == 0 ? 0 : fpMin)
                .fpMaxPerTeam(fpMax)
                .yyMinPerTeam(totalYy == 0 ? 0 : yyMin)
                .yyMaxPerTeam(yyMax)
                .teamsWithoutYy(teamsWithoutYy)
                .yyMinimumCoverageMet(yyMinimumCoverageMet)
                .teams(teamCounts)
                .build();
    }

    private static int countRankToken(LineTeamDto team, String token) {
        if (team == null || team.getMembers() == null) {
            return 0;
        }
        int count = 0;
        for (CrewMemberDto m : team.getMembers()) {
            if (RankTokenUtil.isToken(m, token)) {
                count++;
            }
        }
        return count;
    }

    private static String buildSummary(boolean allBalanced, List<FpYyBalanceReport.BaseBalance> bases) {
        if (bases.isEmpty()) {
            return "검증할 팀이 없습니다.";
        }
        if (allBalanced) {
            return "모든 베이스에서 RANK FP·YY가 팀별로 균등하게 분배되었습니다 (팀당 편차 ≤ 1명).";
        }
        StringBuilder sb = new StringBuilder("RANK FP·YY 분배가 일부 베이스에서 불균형합니다. ");
        for (FpYyBalanceReport.BaseBalance b : bases) {
            if (b.isFpBalanced() && b.isYyBalanced() && b.isYyMinimumCoverageMet()) {
                continue;
            }
            sb.append('[').append(b.getBase()).append("] ");
            if (!b.isYyMinimumCoverageMet() && b.getTeamsWithoutYy() > 0) {
                sb.append("YY 미배치 팀 ").append(b.getTeamsWithoutYy()).append("개 ");
            }
            if (!b.isFpBalanced()) {
                sb.append("FP 팀당 ").append(b.getFpMinPerTeam()).append('~').append(b.getFpMaxPerTeam()).append("명 ");
            }
            if (!b.isYyBalanced()) {
                sb.append("YY 팀당 ").append(b.getYyMinPerTeam()).append('~').append(b.getYyMaxPerTeam()).append("명 ");
            }
        }
        return sb.toString().trim();
    }
}
