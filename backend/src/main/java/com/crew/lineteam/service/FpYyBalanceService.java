package com.crew.lineteam.service;

import com.crew.lineteam.dto.CrewMemberDto;
import com.crew.lineteam.dto.FpYyBalanceReport;
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
import java.util.stream.Collectors;

/**
 * RANK FP·YY·TS OJT, FROM LJ·BX·RS, 직급 PS·AP·SS·인턴 인원의 팀별 균등 분배 검증.
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
            if (!baseReport.isFpBalanced() || !baseReport.isYyBalanced()
                    || !baseReport.isTsOjtBalanced() || !baseReport.isYyMinimumCoverageMet()
                    || !baseReport.isLjBalanced() || !baseReport.isBxBalanced() || !baseReport.isRsBalanced()
                    || !baseReport.isPsBalanced() || !baseReport.isApBalanced()
                    || !baseReport.isSsBalanced() || !baseReport.isInternBalanced()) {
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
        int totalTsOjt = 0;
        int fpMin = Integer.MAX_VALUE;
        int fpMax = 0;
        int yyMin = Integer.MAX_VALUE;
        int yyMax = 0;
        int tsOjtMin = Integer.MAX_VALUE;
        int tsOjtMax = 0;
        int ljMin = Integer.MAX_VALUE;
        int ljMax = 0;
        int bxMin = Integer.MAX_VALUE;
        int bxMax = 0;
        int rsMin = Integer.MAX_VALUE;
        int rsMax = 0;
        int totalLj = 0;
        int totalBx = 0;
        int totalRs = 0;
        int totalPs = 0;
        int totalAp = 0;
        int totalSs = 0;
        int totalIntern = 0;
        int psMin = Integer.MAX_VALUE;
        int psMax = 0;
        int apMin = Integer.MAX_VALUE;
        int apMax = 0;
        int ssMin = Integer.MAX_VALUE;
        int ssMax = 0;
        int internMin = Integer.MAX_VALUE;
        int internMax = 0;

        for (LineTeamDto team : sorted) {
            int fp = countRankToken(team, RankTokenUtil.FP);
            int yy = countRankToken(team, RankTokenUtil.YY);
            int tsOjt = countRankToken(team, RankTokenUtil.TS_OJT);
            int lj = countLineQualification(team, LineQualificationUtil.LJ);
            int bx = countLineQualification(team, LineQualificationUtil.BX);
            int rs = countLineQualification(team, LineQualificationUtil.RS);
            int ps = countGradeToken(team, GradeTokenUtil.PS);
            int ap = countGradeToken(team, GradeTokenUtil.AP);
            int ss = countGradeToken(team, GradeTokenUtil.SS);
            int intern = countGradeToken(team, GradeTokenUtil.INTERN);
            totalFp += fp;
            totalYy += yy;
            totalTsOjt += tsOjt;
            totalLj += lj;
            totalBx += bx;
            totalRs += rs;
            totalPs += ps;
            totalAp += ap;
            totalSs += ss;
            totalIntern += intern;
            fpMin = Math.min(fpMin, fp);
            fpMax = Math.max(fpMax, fp);
            yyMin = Math.min(yyMin, yy);
            yyMax = Math.max(yyMax, yy);
            tsOjtMin = Math.min(tsOjtMin, tsOjt);
            tsOjtMax = Math.max(tsOjtMax, tsOjt);
            ljMin = Math.min(ljMin, lj);
            ljMax = Math.max(ljMax, lj);
            bxMin = Math.min(bxMin, bx);
            bxMax = Math.max(bxMax, bx);
            rsMin = Math.min(rsMin, rs);
            rsMax = Math.max(rsMax, rs);
            psMin = Math.min(psMin, ps);
            psMax = Math.max(psMax, ps);
            apMin = Math.min(apMin, ap);
            apMax = Math.max(apMax, ap);
            ssMin = Math.min(ssMin, ss);
            ssMax = Math.max(ssMax, ss);
            internMin = Math.min(internMin, intern);
            internMax = Math.max(internMax, intern);
            teamCounts.add(FpYyBalanceReport.TeamCount.builder()
                    .teamId(team.getTeamId())
                    .fpCount(fp)
                    .yyCount(yy)
                    .tsOjtCount(tsOjt)
                    .ljCount(lj)
                    .bxCount(bx)
                    .rsCount(rs)
                    .psCount(ps)
                    .apCount(ap)
                    .ssCount(ss)
                    .internCount(intern)
                    .build());
        }

        if (sorted.isEmpty()) {
            fpMin = 0;
            yyMin = 0;
            tsOjtMin = 0;
            ljMin = 0;
            bxMin = 0;
            rsMin = 0;
            psMin = 0;
            apMin = 0;
            ssMin = 0;
            internMin = 0;
        }

        boolean fpBalanced = totalFp == 0 || (fpMax - fpMin <= 1);
        boolean yyBalanced = totalYy == 0 || (yyMax - yyMin <= 1);
        boolean tsOjtBalanced = totalTsOjt == 0 || (tsOjtMax - tsOjtMin <= 1);
        boolean ljBalanced = totalLj == 0 || (ljMax - ljMin <= 1);
        boolean bxBalanced = totalBx == 0 || (bxMax - bxMin <= 1);
        boolean rsBalanced = totalRs == 0 || (rsMax - rsMin <= 1);
        boolean psBalanced = totalPs == 0 || (psMax - psMin <= 1);
        boolean apBalanced = totalAp == 0 || (apMax - apMin <= 1);
        boolean ssBalanced = totalSs == 0 || (ssMax - ssMin <= 1);
        boolean internBalanced = totalIntern == 0 || (internMax - internMin <= 1);
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
                .totalTsOjt(totalTsOjt)
                .fpBalanced(fpBalanced)
                .yyBalanced(yyBalanced)
                .tsOjtBalanced(tsOjtBalanced)
                .fpMinPerTeam(totalFp == 0 ? 0 : fpMin)
                .fpMaxPerTeam(fpMax)
                .yyMinPerTeam(totalYy == 0 ? 0 : yyMin)
                .yyMaxPerTeam(yyMax)
                .tsOjtMinPerTeam(totalTsOjt == 0 ? 0 : tsOjtMin)
                .tsOjtMaxPerTeam(tsOjtMax)
                .totalLj(totalLj)
                .totalBx(totalBx)
                .totalRs(totalRs)
                .ljBalanced(ljBalanced)
                .bxBalanced(bxBalanced)
                .rsBalanced(rsBalanced)
                .ljMinPerTeam(totalLj == 0 ? 0 : ljMin)
                .ljMaxPerTeam(ljMax)
                .bxMinPerTeam(totalBx == 0 ? 0 : bxMin)
                .bxMaxPerTeam(bxMax)
                .rsMinPerTeam(totalRs == 0 ? 0 : rsMin)
                .rsMaxPerTeam(rsMax)
                .totalPs(totalPs)
                .totalAp(totalAp)
                .totalSs(totalSs)
                .totalIntern(totalIntern)
                .psBalanced(psBalanced)
                .apBalanced(apBalanced)
                .ssBalanced(ssBalanced)
                .internBalanced(internBalanced)
                .psMinPerTeam(totalPs == 0 ? 0 : psMin)
                .psMaxPerTeam(psMax)
                .apMinPerTeam(totalAp == 0 ? 0 : apMin)
                .apMaxPerTeam(apMax)
                .ssMinPerTeam(totalSs == 0 ? 0 : ssMin)
                .ssMaxPerTeam(ssMax)
                .internMinPerTeam(totalIntern == 0 ? 0 : internMin)
                .internMaxPerTeam(internMax)
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

    private static int countLineQualification(LineTeamDto team, String qualification) {
        return LineQualificationUtil.countInTeam(team, qualification);
    }

    private static int countGradeToken(LineTeamDto team, String gradeToken) {
        return GradeTokenUtil.countInTeam(team, gradeToken);
    }

    private static String buildSummary(boolean allBalanced, List<FpYyBalanceReport.BaseBalance> bases) {
        if (bases.isEmpty()) {
            return "검증할 팀이 없습니다.";
        }
        if (allBalanced) {
            return "모든 베이스에서 RANK FP·YY·TS OJT, FROM LJ·BX·RS, 직급 PS·AP·SS·인턴이 팀별로 균등하게 분배되었습니다 (팀당 편차 ≤ 1명).";
        }
        StringBuilder sb = new StringBuilder("일부 베이스에서 분배가 불균형합니다. ");
        for (FpYyBalanceReport.BaseBalance b : bases) {
            if (b.isFpBalanced() && b.isYyBalanced() && b.isTsOjtBalanced() && b.isYyMinimumCoverageMet()
                    && b.isLjBalanced() && b.isBxBalanced() && b.isRsBalanced()
                    && b.isPsBalanced() && b.isApBalanced() && b.isSsBalanced() && b.isInternBalanced()) {
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
            if (!b.isTsOjtBalanced()) {
                sb.append("TS OJT 팀당 ").append(b.getTsOjtMinPerTeam()).append('~').append(b.getTsOjtMaxPerTeam()).append("명 ");
            }
            if (!b.isLjBalanced()) {
                sb.append("LJ 팀당 ").append(b.getLjMinPerTeam()).append('~').append(b.getLjMaxPerTeam()).append("명 ");
            }
            if (!b.isBxBalanced()) {
                sb.append("BX 팀당 ").append(b.getBxMinPerTeam()).append('~').append(b.getBxMaxPerTeam()).append("명 ");
            }
            if (!b.isRsBalanced()) {
                sb.append("RS 팀당 ").append(b.getRsMinPerTeam()).append('~').append(b.getRsMaxPerTeam()).append("명 ");
            }
            if (!b.isPsBalanced()) {
                sb.append("PS 팀당 ").append(b.getPsMinPerTeam()).append('~').append(b.getPsMaxPerTeam()).append("명 ");
            }
            if (!b.isApBalanced()) {
                sb.append("AP 팀당 ").append(b.getApMinPerTeam()).append('~').append(b.getApMaxPerTeam()).append("명 ");
            }
            if (!b.isSsBalanced()) {
                sb.append("SS 팀당 ").append(b.getSsMinPerTeam()).append('~').append(b.getSsMaxPerTeam()).append("명 ");
            }
            if (!b.isInternBalanced()) {
                sb.append("인턴 팀당 ").append(b.getInternMinPerTeam()).append('~').append(b.getInternMaxPerTeam()).append("명 ");
            }
        }
        return sb.toString().trim();
    }
}
