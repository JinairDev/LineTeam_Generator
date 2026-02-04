package com.crew.lineteam.service;

import com.crew.lineteam.dto.CrewMemberDto;
import com.crew.lineteam.dto.LineTeamDto;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 재직 현황 엑셀 읽기 / 편성 결과 엑셀 쓰기
 */
@Service
public class ExcelService {

    private static final int HEADER_ROW = 0;
    private static final int DATA_START_ROW = 1;

    /**
     * 엑셀 컬럼 헤더 후보 (승무원 리스트 Test.xlsx: 사번, 이름, 성별, BASE, Rank, Line, 직급, 구분, 자격)
     * - Rank 컬럼: TP, TS 등 팀장/선임 구분 → positionCode (별칭에 "rank" 넣지 않음, "Rank"와 겹침)
     * - 자격 컬럼: S/A/B/YY 방송자격 → rank
     */
    private static final Map<String, String[]> COLUMN_ALIASES = Map.of(
            "employeeId", new String[]{"사번", "employeeId", "EMPLOYEE_ID"},
            "name", new String[]{"이름", "name", "NAME"},
            "gender", new String[]{"성별", "gender", "GENDER", "성"},
            "base", new String[]{"BASE", "근거지", "base", "BASE_CD", "지역"},
            "positionCode", new String[]{"Rank", "RANK"},  // TP, TS 등 팀장/선임 구분 (엑셀 컬럼명 Rank)
            "line", new String[]{"Line", "LINE"},
            "grade", new String[]{"직급", "grade", "GRADE", "직급코드"},
            "status", new String[]{"구분", "status", "STATUS"},
            "rank", new String[]{"자격", "방송자격", "자격코드"}  // S/A/B/YY 방송자격 (별칭에 rank 미포함 → Rank 컬럼과 구분)
    );

    /**
     * 엑셀 파일에서 재직 현황(승무원 목록) 파싱
     * 첫 행은 헤더, 2행부터 데이터. 컬럼명은 위 COLUMN_ALIASES 기준 매칭.
     */
    public List<CrewMemberDto> parseCrewExcel(InputStream inputStream) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) return List.of();

            Row headerRow = sheet.getRow(HEADER_ROW);
            if (headerRow == null) return List.of();

            Map<String, Integer> colIndex = resolveColumnIndices(headerRow);
            List<CrewMemberDto> list = new ArrayList<>();

            for (int i = DATA_START_ROW; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                CrewMemberDto dto = rowToCrewMember(row, colIndex);
                if (dto.getEmployeeId() != null && !dto.getEmployeeId().isBlank()) {
                    list.add(dto);
                }
            }
            return list;
        }
    }

    private Map<String, Integer> resolveColumnIndices(Row headerRow) {
        return COLUMN_ALIASES.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> findColumnIndex(headerRow, e.getValue())
                ));
    }

    private int findColumnIndex(Row headerRow, String[] aliases) {
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            String val = getCellString(cell);
            if (val == null) continue;
            String trimmed = val.trim();
            for (String alias : aliases) {
                if (alias.equalsIgnoreCase(trimmed)) return i;
            }
        }
        return -1;
    }

    private CrewMemberDto rowToCrewMember(Row row, Map<String, Integer> colIndex) {
        Function<String, String> get = key -> {
            int idx = colIndex.getOrDefault(key, -1);
            if (idx < 0) return null;
            return getCellString(row.getCell(idx));
        };
        String employeeId = get.apply("employeeId");
        if (employeeId != null) employeeId = employeeId.trim();
        return CrewMemberDto.builder()
                .employeeId(employeeId)
                .name(nullToEmpty(get.apply("name")))
                .gender(nullToEmpty(get.apply("gender")))
                .base(nullToEmpty(get.apply("base")))
                .positionCode(nullToEmpty(get.apply("positionCode")))
                .line(nullToEmpty(get.apply("line")))
                .grade(nullToEmpty(get.apply("grade")))
                .status(nullToEmpty(get.apply("status")))
                .rank(nullToEmpty(get.apply("rank")))
                .build();
    }

    private static String getCellString(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    /**
     * 편성 결과를 엑셀 파일로 생성 (바이트 배열 반환)
     */
    public byte[] exportTeamsToExcel(List<LineTeamDto> teams) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("라인팀 편성 결과");
            CellStyle headerStyle = createHeaderStyle(workbook);
            int rowNum = 0;

            for (LineTeamDto team : teams) {
                // 팀 헤더 행
                Row teamHeader = sheet.createRow(rowNum++);
                teamHeader.createCell(0).setCellValue("팀ID: " + team.getTeamId());
                teamHeader.getCell(0).setCellStyle(headerStyle);
                for (int c = 1; c <= 8; c++) {
                    teamHeader.createCell(c).setCellValue("");
                }

                // 컬럼 헤더 (승무원 리스트 Test.xlsx와 동일한 순서)
                Row headerRow = sheet.createRow(rowNum++);
                String[] headers = {"사번", "이름", "성별", "BASE", "Rank", "Line", "직급", "구분", "자격"};
                for (int i = 0; i < headers.length; i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headers[i]);
                    cell.setCellStyle(headerStyle);
                }

                for (CrewMemberDto m : team.getMembers()) {
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(m.getEmployeeId());
                    row.createCell(1).setCellValue(m.getName());
                    row.createCell(2).setCellValue(m.getGender());
                    row.createCell(3).setCellValue(m.getBase());
                    row.createCell(4).setCellValue(m.getPositionCode() != null ? m.getPositionCode() : "");
                    row.createCell(5).setCellValue(m.getLine() != null ? m.getLine() : "");
                    row.createCell(6).setCellValue(m.getGrade());
                    row.createCell(7).setCellValue(m.getStatus() != null ? m.getStatus() : "");
                    row.createCell(8).setCellValue(m.getRank());
                }
                rowNum++; // 팀 간 빈 행
            }

            for (int i = 0; i < 9; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }
}
