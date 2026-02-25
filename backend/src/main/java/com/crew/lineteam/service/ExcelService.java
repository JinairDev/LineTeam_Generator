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
     * 엑셀 컬럼 헤더 후보 (CPS 라인팀 TEST용 기준: 사번, 이름, 성별, BASE, 직급, FROM, RANK, Qualification, 재직상태 등)
     * - RANK 컬럼: TP, TS 등 팀장/선임 구분 → positionCode
     * - Qualification 컬럼: 방송자격 → rank
     * - 재직상태 컬럼: 재직 상태 → status
     */
    private static final Map<String, String[]> COLUMN_ALIASES = Map.of(
            "employeeId", new String[]{"사번", "employeeId", "EMPLOYEE_ID"},
            "name", new String[]{"이름", "name", "NAME"},
            "gender", new String[]{"성별", "gender", "GENDER", "성"},
            "base", new String[]{"BASE", "근거지", "base", "BASE_CD", "지역"},
            "positionCode", new String[]{"RANK", "Rank", "rank"},  // TP, TS 등 팀장/선임 구분
            "line", new String[]{"Line", "LINE"},  // 선택적 (없을 수 있음)
            "grade", new String[]{"직급", "grade", "GRADE", "직급코드"},
            "status", new String[]{"재직상태", "구분", "status", "STATUS"},
            "rank", new String[]{"Qualification", "자격", "방송자격", "자격코드"},  // 방송자격
            "from", new String[]{"FROM", "from", "From"}  // FROM 칼럼: LJ, RS, BX 중 하나
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

            // 디버깅: 헤더 칼럼명 출력
            System.out.println("=== 엑셀 파일 헤더 칼럼 ===");
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i);
                String value = getCellString(cell);
                System.out.println(String.format("칼럼 %d: %s", i + 1, value != null ? value : "(비어있음)"));
            }
            System.out.println("===========================");

            Map<String, Integer> colIndex = resolveColumnIndices(headerRow);
            
            // 디버깅: 칼럼 인덱스 매핑 확인
            System.out.println("=== 칼럼 인덱스 매핑 ===");
            colIndex.forEach((key, idx) -> {
                if (idx >= 0) {
                    Cell cell = headerRow.getCell(idx);
                    String value = getCellString(cell);
                    System.out.println(String.format("%s -> 인덱스 %d: %s", key, idx, value != null ? value : "(비어있음)"));
                } else {
                    System.out.println(String.format("%s -> 매핑되지 않음", key));
                }
            });
            System.out.println("========================");
            List<CrewMemberDto> list = new ArrayList<>();
            
            // 사번 컬럼 인덱스 확인
            int employeeIdColIdx = colIndex.getOrDefault("employeeId", -1);
            if (employeeIdColIdx < 0) {
                throw new IllegalArgumentException("사번 컬럼을 찾을 수 없습니다. 엑셀 파일의 헤더에 '사번' 컬럼이 있는지 확인하세요.");
            }
            
            int emptyRowCount = 0; // 연속된 빈 행 카운트
            final int MAX_EMPTY_ROWS = 5; // 연속된 빈 행이 5개 이상이면 중단
            int processedRows = 0; // 처리한 행 수 (디버깅용)

            for (int i = DATA_START_ROW; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                
                // 행이 null이면 건너뛰기
                if (row == null) {
                    emptyRowCount++;
                    if (emptyRowCount >= MAX_EMPTY_ROWS) {
                        break;
                    }
                    continue;
                }
                
                // 사번 컬럼만 정확히 체크
                Cell employeeIdCell = row.getCell(employeeIdColIdx);
                String employeeId = getCellString(employeeIdCell);
                
                // 사번이 없거나 비어있으면 빈 행으로 간주
                if (employeeId == null || employeeId.trim().isEmpty()) {
                    emptyRowCount++;
                    if (emptyRowCount >= MAX_EMPTY_ROWS) {
                        break;
                    }
                    continue;
                }
                
                emptyRowCount = 0; // 사번이 있는 행을 만나면 카운트 리셋
                processedRows++;

                CrewMemberDto dto = rowToCrewMember(row, colIndex);
                // 사번이 있는 행만 추가 (이미 위에서 체크했지만 다시 확인)
                if (dto.getEmployeeId() != null && !dto.getEmployeeId().isBlank()) {
                    list.add(dto);
                }
            }
            
            // 디버깅 정보 (로깅은 나중에 추가 가능)
            System.out.println(String.format("[ExcelService] 총 처리 행 수: %d, 승무원 수: %d", processedRows, list.size()));
            
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
        CrewMemberDto dto = CrewMemberDto.builder()
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
        dto.setFromColumn(nullToEmpty(get.apply("from")));
        return dto;
    }

    /** Single-arg overload for contexts without FormulaEvaluator (e.g. isRowEmpty). */
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
     * 행이 완전히 비어있는지 확인 (모든 셀이 null이거나 빈 문자열)
     */
    private static boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null) {
                String val = getCellString(cell);
                if (val != null && !val.trim().isEmpty()) {
                    return false; // 데이터가 있는 셀 발견
                }
            }
        }
        return true; // 모든 셀이 비어있음
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
                String[] headers = {"사번", "이름", "성별", "BASE", "Rank", "Line", "직급", "구분", "자격", "FROM"};
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
                    row.createCell(9).setCellValue(m.getFromColumn() != null ? m.getFromColumn() : "");
                }
                rowNum++; // 팀 간 빈 행
            }

            for (int i = 0; i < 10; i++) {
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
