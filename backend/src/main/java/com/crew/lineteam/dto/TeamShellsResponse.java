package com.crew.lineteam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamShellsResponse {

    private List<LineTeamDto> teams;
    /** 엑셀 소속팀으로 팀에 미리 넣은 인원 수 */
    private int departmentSeededCount;
    /** 소속팀이 있었으나 팀 ID 미매칭·TP 충돌 등으로 스킵한 수 */
    private int departmentSeedSkippedCount;
}
