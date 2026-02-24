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

import static java.util.Map.entry;

/**
 * 재직 현황 엑셀 읽기 / 편성 결과 엑셀 쓰기
 * - 1행(또는 헤더가 있는 행)을 컬럼명으로 인식하고, 그 다음 행부터 데이터로 읽습니다.
 * - positionCode = Rank 컬럼(TP, TS 등) / rank = 자격 컬럼(LJ, BX, RS 또는 S/A/B/YY 등)
 */
@Service
public class ExcelService {

    private static final int MAX_HEADER_SCAN_ROWS = 20;
    /** 데이터 행 읽을 때, 이 개수만큼 연속으로 비어있으면 읽기 중단 (getLastRowNum 사용 안 함) */
    private static final int MAX_CONSECUTIVE_EMPTY_ROWS = 50;
    private static final int MAX_DATA_ROWS = 5_000;
    private static final int MAX_COLUMNS = 128;

    /**
     * 엑셀 컬럼 헤더 후보
     * - positionCode: Rank 컬럼 (TP, TS) → 팀장/선임 구분
     * - rank: 자격 컬럼 (LJ, BX, RS 또는 방송자격) → 라인자격/방송자격
     */
    private static final Map<String, String[]> COLUMN_ALIASES = Map.ofEntries(
            entry("employeeId", new String[]{"사번", "employeeId", "EMPLOYEE_ID", "사원번호"}),
            entry("name", new String[]{"이름", "name", "NAME", "성명"}),
            entry("gender", new String[]{"성별", "gender", "GENDER", "성"}),
            entry("base", new String[]{"BASE", "Base", "근거지", "base", "BASE_CD", "지역", "지역코드", "LOCATION", "근무지"}),
            entry("positionCode", new String[]{"Rank", "RANK", "직위", "직책"}),
            entry("line", new String[]{"Line", "LINE", "라인"}),
            entry("grade", new String[]{"직급", "grade", "GRADE", "직급코드"}),
            entry("status", new String[]{"재직상태", "구분", "status", "STATUS"}),
            entry("rank", new String[]{"FROM", "자격", "방송자격", "자격코드", "라인자격"}),
            entry("annc", new String[]{"ANNC", "Annc", "annc"}),
            entry("qualification", new String[]{"Qualification", "QUALIFICATION", "자격(심사관등)"})
    );

    /** 헤더로 인정할 키워드가 하나라도 있으면 그 행을 헤더로 사용 */
    private static final String[] HEADER_MARKERS = {"사번", "이름", "Rank", "RANK", "BASE", "직급"};

    private final DataFormatter dataFormatter = new DataFormatter();

    /**
     * 엑셀 파일에서 재직 현황(승무원 목록) 파싱
     * 첫 행 또는 상위 몇 행 중 헤더 행을 찾고, 그 다음 행부터 데이터로 읽습니다.
     */
    public List<CrewMemberDto> parseCrewExcel(InputStream inputStream) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) return List.of();

            int headerRowIndex = findHeaderRow(sheet, evaluator);
            if (headerRowIndex < 0) return List.of();

            Row headerRow = sheet.getRow(headerRowIndex);
            if (headerRow == null) return List.of();

            Map<String, Integer> colIndex = resolveColumnIndices(headerRow, evaluator);
            if (colIndex.getOrDefault("employeeId", -1) < 0) {
                return List.of();
            }

            List<CrewMemberDto> list = new ArrayList<>();
            int consecutiveEmpty = 0;
            int rowIndex = headerRowIndex + 1;
            while (rowIndex < MAX_DATA_ROWS && consecutiveEmpty < MAX_CONSECUTIVE_EMPTY_ROWS) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    consecutiveEmpty++;
                    rowIndex++;
                    continue;
                }
                CrewMemberDto dto = rowToCrewMember(row, colIndex, evaluator);
                String eid = dto.getEmployeeId();
                if (eid == null || eid.isBlank()) {
                    consecutiveEmpty++;
                    rowIndex++;
                    continue;
                }
                String name = dto.getName();
                if (name != null) name = name.trim();
                if (colIndex.getOrDefault("name", -1) >= 0 && (name == null || name.isBlank())) {
                    consecutiveEmpty++;
                    rowIndex++;
                    continue;
                }
                list.add(dto);
                consecutiveEmpty = 0;
                rowIndex++;
            }

            return list;
        }
    }

    /** 시트에서 헤더 행 인덱스 찾기. 행을 0부터 직접 순회해 헤더 키워드가 있는 행을 반환. */
    private int findHeaderRow(Sheet sheet, FormulaEvaluator evaluator) {
        for (int r = 0; r < MAX_HEADER_SCAN_ROWS; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (int c = 0; c < MAX_COLUMNS; c++) {
                Cell cell = row.getCell(c);
                if (cell == null) continue;
                String val = normalizeHeader(getCellString(cell, evaluator));
                if (val == null || val.isEmpty()) continue;
                for (String marker : HEADER_MARKERS) {
                    if (marker.equalsIgnoreCase(val)) return r;
                }
            }
        }
        return 0;
    }

    private static String normalizeHeader(String s) {
        if (s == null) return null;
        return s.trim().replace("\uFEFF", "").replaceAll("\\s+", " ");
    }

    private Map<String, Integer> resolveColumnIndices(Row headerRow, FormulaEvaluator evaluator) {
        return COLUMN_ALIASES.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> findColumnIndex(headerRow, e.getValue(), evaluator)
                ));
    }

    /** 헤더 행에서 컬럼 인덱스 찾기. getLastCellNum()에 의존하지 않고 0~MAX_COLUMNS 직접 순회. */
    private int findColumnIndex(Row headerRow, String[] aliases, FormulaEvaluator evaluator) {
        for (int i = 0; i < MAX_COLUMNS; i++) {
            Cell cell = headerRow.getCell(i);
            if (cell == null) continue;
            String val = normalizeHeader(getCellString(cell, evaluator));
            if (val == null || val.isEmpty()) continue;
            for (String alias : aliases) {
                if (alias.equalsIgnoreCase(val)) return i;
            }
        }
        return -1;
    }

    private CrewMemberDto rowToCrewMember(Row row, Map<String, Integer> colIndex, FormulaEvaluator evaluator) {
        Function<String, String> get = key -> {
            int idx = colIndex.getOrDefault(key, -1);
            if (idx < 0) return null;
            return getCellString(row.getCell(idx), evaluator);
        };
        String employeeId = get.apply("employeeId");
        if (employeeId != null) employeeId = employeeId.trim();
        return CrewMemberDto.builder()
                .employeeId(employeeId)
                .name(nullToEmpty(get.apply("name")))
                .gender(nullToEmpty(get.apply("gender")))
                .base(normalizeBaseValue(nullToEmpty(get.apply("base"))))
                .positionCode(nullToEmpty(get.apply("positionCode")))
                .line(nullToEmpty(get.apply("line")))
                .grade(nullToEmpty(get.apply("grade")))
                .status(nullToEmpty(get.apply("status")))
                .rank(nullToEmpty(get.apply("rank")))
                .annc(nullToEmpty(get.apply("annc")))
                .qualification(nullToEmpty(get.apply("qualification")))
                .build();
    }

    /** 셀 값을 문자열로 읽기. 수식 셀은 평가 후, 숫자 셀은 Excel에 보이는 그대로 반환. 행/열 인덱스로 직접 접근. */
    private String getCellString(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case FORMULA:
                if (evaluator != null) {
                    try {
                        CellValue cv = evaluator.evaluate(cell);
                        if (cv == null) return null;
                        switch (cv.getCellType()) {
                            case STRING -> { return cv.getStringValue(); }
                            case NUMERIC -> { return formatNumeric(cv.getNumberValue()); }
                            case BOOLEAN -> { return String.valueOf(cv.getBooleanValue()); }
                            default -> { return null; }
                        }
                    } catch (Exception ignored) {
                        return dataFormatter.formatCellValue(cell);
                    }
                }
                return dataFormatter.formatCellValue(cell);
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return dataFormatter.formatCellValue(cell);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                String formatted = dataFormatter.formatCellValue(cell);
                return formatted == null || formatted.isEmpty() ? null : formatted.trim();
        }
    }

    private static String formatNumeric(double value) {
        long l = (long) value;
        if (Math.abs(value - l) < 1e-9) return String.valueOf(l);
        return String.valueOf(value);
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

    /** BASE 값 정규화: PUS/부산 포함 → PUS, SEL/서울 포함 → SEL (팀 ID 통일). 부분 일치·숫자코드(1=SEL,2=PUS) 지원 */
    private static String normalizeBaseValue(String s) {
        if (s == null) return "";
        String v = s.trim().replace("\u00A0", " ").trim();
        if (v.isEmpty()) return "";
        String u = v.toUpperCase();
        if ("2".equals(v) || "02".equals(v) || "2.0".equals(v)) return "PUS";
        if ("1".equals(v) || "01".equals(v) || "1.0".equals(v)) return "SEL";
        if (u.contains("PUS") || v.contains("부산") || u.contains("BUSAN")) return "PUS";
        if (u.contains("SEL") || v.contains("서울") || u.contains("SEOUL")) return "SEL";
        if ("PUS".equals(u) || "부산".equals(v) || "BUSAN".equals(u)) return "PUS";
        if ("SEL".equals(u) || "서울".equals(v) || "SEOUL".equals(u)) return "SEL";
        return v;
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

                // 컬럼 헤더 (사번~구분, FROM, ANNC, Qualification)
                Row headerRow = sheet.createRow(rowNum++);
                String[] headers = {"사번", "이름", "성별", "BASE", "Rank", "Line", "직급", "구분", "FROM", "ANNC", "Qualification"};
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
                    row.createCell(8).setCellValue(m.getRank() != null ? m.getRank() : "");
                    row.createCell(9).setCellValue(m.getAnnc() != null ? m.getAnnc() : "");
                    row.createCell(10).setCellValue(m.getQualification() != null ? m.getQualification() : "");
                }
                rowNum++; // 팀 간 빈 행
            }

            for (int i = 0; i < 11; i++) {
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
