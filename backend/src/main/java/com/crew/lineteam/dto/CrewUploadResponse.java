package com.crew.lineteam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 재직 현황 업로드(엑셀·CSV) 결과: 승무원 목록 + 시트 첫 행 헤더 순서 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrewUploadResponse {

    private List<CrewMemberDto> crew;
    /** 업로드 파일 1행 헤더(왼쪽→오른쪽). 엑셀 추출 시 동일 순서로 출력 */
    private List<String> columnHeaders;
}
