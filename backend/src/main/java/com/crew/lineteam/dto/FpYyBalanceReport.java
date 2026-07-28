package com.crew.lineteam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** 편성 후 RANK FP·YY·TS OJT, FROM LJ·BX·RS, 직급 PS·AP·SS·인턴 팀별 균등 분배 검증 결과 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FpYyBalanceReport {

    /** FP·YY·TS OJT·FROM LJ/BX/RS·직급 PS/AP/SS/인턴이 허용 편차(최대−최소 ≤ 1) 이내이면 true */
    private boolean balanced;

    private String summary;

    @Builder.Default
    private List<BaseBalance> bases = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BaseBalance {
        private String base;
        private int teamCount;
        private int totalFp;
        private int totalYy;
        private int totalTsOjt;
        private boolean fpBalanced;
        private boolean yyBalanced;
        private boolean tsOjtBalanced;
        private int fpMinPerTeam;
        private int fpMaxPerTeam;
        private int yyMinPerTeam;
        private int yyMaxPerTeam;
        private int tsOjtMinPerTeam;
        private int tsOjtMaxPerTeam;
        private int totalLj;
        private int totalBx;
        private int totalRs;
        private boolean ljBalanced;
        private boolean bxBalanced;
        private boolean rsBalanced;
        private int ljMinPerTeam;
        private int ljMaxPerTeam;
        private int bxMinPerTeam;
        private int bxMaxPerTeam;
        private int rsMinPerTeam;
        private int rsMaxPerTeam;
        private int totalPs;
        private int totalAp;
        private int totalSs;
        private int totalIntern;
        private boolean psBalanced;
        private boolean apBalanced;
        private boolean ssBalanced;
        private boolean internBalanced;
        private int psMinPerTeam;
        private int psMaxPerTeam;
        private int apMinPerTeam;
        private int apMaxPerTeam;
        private int ssMinPerTeam;
        private int ssMaxPerTeam;
        private int internMinPerTeam;
        private int internMaxPerTeam;
        /** YY가 1명도 없는 팀 수 */
        private int teamsWithoutYy;
        /** YY 인원이 팀 수 이상이면 모든 팀에 YY≥1, 미만이면 보유 YY를 최대한 1팀 1명 배치한 상태 */
        private boolean yyMinimumCoverageMet;

        @Builder.Default
        private List<TeamCount> teams = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamCount {
        private String teamId;
        private int fpCount;
        private int yyCount;
        private int tsOjtCount;
        private int ljCount;
        private int bxCount;
        private int rsCount;
        private int psCount;
        private int apCount;
        private int ssCount;
        private int internCount;
    }
}
