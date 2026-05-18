package com.crew.lineteam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** 편성 후 RANK FP·YY 팀별 균등 분배 검증 결과 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FpYyBalanceReport {

    /** 모든 베이스에서 FP·YY가 허용 편차(최대−최소 ≤ 1) 이내이면 true */
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
        private boolean fpBalanced;
        private boolean yyBalanced;
        private int fpMinPerTeam;
        private int fpMaxPerTeam;
        private int yyMinPerTeam;
        private int yyMaxPerTeam;
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
    }
}
