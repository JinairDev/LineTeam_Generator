package com.crew.lineteam.dto;

import lombok.Data;

@Data
public class GoogleSpreadsheetImportRequest {
    /** Google 스프레드시트 공유 링크 또는 스프레드시트 ID */
    private String spreadsheetUrl;
}
