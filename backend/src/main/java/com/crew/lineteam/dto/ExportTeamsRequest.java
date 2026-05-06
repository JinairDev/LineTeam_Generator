package com.crew.lineteam.dto;

import lombok.Data;

import java.util.List;

/** 편성 결과 엑셀 요청: 팀 목록 + (선택) 업로드 시 헤더 순서 */
@Data
public class ExportTeamsRequest {

    private List<LineTeamDto> teams;
    /** 비어 있으면 기본 열 순서(기존 동작) */
    private List<String> columnHeaders;
}
