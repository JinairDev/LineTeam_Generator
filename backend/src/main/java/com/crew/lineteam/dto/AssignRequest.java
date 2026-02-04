package com.crew.lineteam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 편성 요청
 * - crew: 승무원 목록
 * - teamCountByBase: 지역별 팀 수 (예: SEL=5, PUS=3). 없으면 지역별 TP 수로 팀 수 결정
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignRequest {
    private List<CrewMemberDto> crew;
    /** 지역(BASE)별 팀 개수. 예: {"SEL": 5, "PUS": 3} */
    private Map<String, Integer> teamCountByBase;
}
