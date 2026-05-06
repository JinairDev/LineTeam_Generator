package com.crew.lineteam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LineTeamDto {

    private String teamId;           // 팀 식별자 (SEL: A101~…, PUS: B101~…, 기타: BASE-01)
    private String base;             // 근거지
    private int indexInBase;         // 해당 지역 내 팀 순번
    private List<CrewMemberDto> members = new ArrayList<>();

    public int getMemberCount() {
        return members != null ? members.size() : 0;
    }
}
