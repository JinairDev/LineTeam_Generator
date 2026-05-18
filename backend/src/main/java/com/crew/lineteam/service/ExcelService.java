package com.crew.lineteam.service;

import com.crew.lineteam.dto.CrewMemberDto;
import com.crew.lineteam.dto.LineTeamDto;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import com.crew.lineteam.dto.CrewUploadResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Map.entry;

/**
 * 재직 현황 엑셀 읽기 / 편성 결과 엑셀 쓰기
 * - FROM → fromColumn, RANK → positionCode, ANNC → annc, Qualification → qualification(표시) / rank(방송자격)
 */
@Service
public class ExcelService {

    private static final int HEADER_ROW = 0;
    private static final int DATA_START_ROW = 1;
    private static final Pattern TEAM_NUMBER_PATTERN = Pattern.compile("(\\d+)");

    /**
     * 엑셀 컬럼 헤더 후보: FROM, RANK, ANNC, Qualification, 재직상태 등
     * - RANK → positionCode (TP/TS/FP/YY), FROM → fromColumn (LJ/BX/RS), ANNC → annc, Qualification → rank(방송자격) + qualification(심사관 등)
     */
    private static final Map<String, String[]> COLUMN_ALIASES = Map.ofEntries(
            entry("employeeId", new String[]{"사번", "employeeId", "EMPLOYEE_ID"}),
            entry("name", new String[]{"이름", "name", "NAME"}),
            entry("gender", new String[]{"성별", "gender", "GENDER", "성"}),
            entry("base", new String[]{"BASE", "근거지", "base", "BASE_CD", "지역"}),
            entry("positionCode", new String[]{"RANK", "Rank", "rank"}),
            entry("line", new String[]{"Line", "LINE"}),
            entry("grade", new String[]{"직급", "grade", "GRADE", "직급코드"}),
            entry("status", new String[]{"재직상태", "구분", "status", "STATUS"}),
            entry("department", new String[]{"소속팀", "부서", "department", "DEPARTMENT"}),
            entry("rank", new String[]{"Qualification", "자격", "방송자격", "자격코드"}),
            entry("from", new String[]{"FROM", "from", "From"}),
            entry("annc", new String[]{"ANNC", "Annc", "annc"}),
            entry("qualification", new String[]{"Qualification", "QUALIFICATION", "자격(심사관등)"} )
    );

    /**
     * 엑셀 파일에서 재직 현황(승무원 목록) 파싱
     * 첫 행은 헤더, 2행부터 데이터. 컬럼명은 위 COLUMN_ALIASES 기준 매칭.
     */
    public CrewUploadResponse parseCrewExcel(InputStream inputStream) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                return new CrewUploadResponse(List.of(), List.of());
            }

            Row headerRow = sheet.getRow(HEADER_ROW);
            if (headerRow == null) {
                return new CrewUploadResponse(List.of(), List.of());
            }

            List<String> headers = extractHeaderLabels(headerRow);
            Map<String, Integer> colIndex = resolveColumnIndicesFromHeaders(headers);

            System.out.println("=== 엑셀 파일 헤더 칼럼 ===");
            for (int i = 0; i < headers.size(); i++) {
                System.out.println(String.format("칼럼 %d: %s", i + 1, headers.get(i).isEmpty() ? "(비어있음)" : headers.get(i)));
            }
            System.out.println("===========================");

            System.out.println("=== 칼럼 인덱스 매핑 ===");
            colIndex.forEach((key, idx) -> {
                if (idx >= 0 && idx < headers.size()) {
                    System.out.println(String.format("%s -> 인덱스 %d: %s", key, idx, headers.get(idx)));
                } else {
                    System.out.println(String.format("%s -> 매핑되지 않음", key));
                }
            });
            System.out.println("========================");

            List<CrewMemberDto> list = new ArrayList<>();

            int employeeIdColIdx = colIndex.getOrDefault("employeeId", -1);
            if (employeeIdColIdx < 0) {
                throw new IllegalArgumentException("사번 컬럼을 찾을 수 없습니다. 엑셀 파일의 헤더에 '사번' 컬럼이 있는지 확인하세요.");
            }

            int emptyRowCount = 0;
            final int MAX_EMPTY_ROWS = 5;
            int processedRows = 0;

            for (int i = DATA_START_ROW; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);

                if (row == null) {
                    emptyRowCount++;
                    if (emptyRowCount >= MAX_EMPTY_ROWS) {
                        break;
                    }
                    continue;
                }

                Cell employeeIdCell = row.getCell(employeeIdColIdx);
                String employeeId = getCellString(employeeIdCell);

                if (employeeId == null || employeeId.trim().isEmpty()) {
                    emptyRowCount++;
                    if (emptyRowCount >= MAX_EMPTY_ROWS) {
                        break;
                    }
                    continue;
                }

                emptyRowCount = 0;
                processedRows++;

                Map<String, String> importCols = buildExcelImportColumns(headers, row);
                CrewMemberDto dto = rowToCrewMember(row, colIndex, importCols);
                if (dto.getEmployeeId() != null && !dto.getEmployeeId().isBlank()) {
                    list.add(dto);
                }
            }

            System.out.println(String.format("[ExcelService] 총 처리 행 수: %d, 승무원 수: %d", processedRows, list.size()));

            return new CrewUploadResponse(list, headers);
        }
    }

    /**
     * CSV(구글 스프레드시트 내보내기 등)에서 재직 현황 파싱. 첫 행은 헤더, 엑셀과 동일한 컬럼 규칙.
     * 줄 단위 파싱(셀 내 줄바꿈은 미지원).
     */
    public CrewUploadResponse parseCrewCsv(Reader reader) throws IOException {
        try (BufferedReader br = new BufferedReader(reader)) {
            String firstLine = br.readLine();
            if (firstLine == null) {
                return new CrewUploadResponse(List.of(), List.of());
            }
            if (firstLine.startsWith("\uFEFF")) {
                firstLine = firstLine.substring(1);
            }
            List<String> headers = parseCsvLine(firstLine);
            for (int i = 0; i < headers.size(); i++) {
                headers.set(i, headers.get(i).trim());
            }

            Map<String, Integer> colIndex = resolveColumnIndicesFromHeaders(headers);
            int employeeIdColIdx = colIndex.getOrDefault("employeeId", -1);
            if (employeeIdColIdx < 0) {
                throw new IllegalArgumentException("사번 컬럼을 찾을 수 없습니다. 첫 행 헤더에 '사번' 컬럼이 있는지 확인해 주세요.");
            }

            List<CrewMemberDto> list = new ArrayList<>();
            int emptyRowCount = 0;
            final int maxEmptyRows = 5;
            int processedRows = 0;

            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> cells = parseCsvLine(line);
                String employeeId = getListCell(cells, employeeIdColIdx);
                if (employeeId == null || employeeId.trim().isEmpty()) {
                    emptyRowCount++;
                    if (emptyRowCount >= maxEmptyRows) {
                        break;
                    }
                    continue;
                }
                emptyRowCount = 0;
                processedRows++;

                Map<String, String> importCols = buildCsvImportColumns(headers, cells);
                CrewMemberDto dto = crewMemberFromColumnValues(colIndex, key -> {
                    int idx = colIndex.getOrDefault(key, -1);
                    if (idx < 0) {
                        return null;
                    }
                    return getListCell(cells, idx);
                });
                dto.setImportColumns(importCols);
                if (dto.getEmployeeId() != null && !dto.getEmployeeId().isBlank()) {
                    list.add(dto);
                }
            }

            System.out.println(String.format("[ExcelService CSV] 총 처리 행 수: %d, 승무원 수: %d", processedRows, list.size()));
            return new CrewUploadResponse(list, headers);
        }
    }

    /**
     * 한 줄 파싱: 엑셀 복사·붙여넣기는 탭 구분, CSV 내보내기는 쉼표(RFC 4180) 구분.
     */
    static List<String> parseCsvLine(String line) {
        if (line.indexOf('\t') >= 0) {
            String[] parts = line.split("\t", -1);
            List<String> fields = new ArrayList<>(parts.length);
            for (String p : parts) {
                fields.add(p);
            }
            return fields;
        }
        List<String> fields = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        sb.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    sb.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(sb.toString());
                    sb.setLength(0);
                } else {
                    sb.append(c);
                }
            }
        }
        fields.add(sb.toString());
        return fields;
    }

    private static String getListCell(List<String> row, int idx) {
        if (idx < 0 || idx >= row.size()) return null;
        String v = row.get(idx);
        return v == null ? null : v.trim();
    }

    private List<String> extractHeaderLabels(Row headerRow) {
        List<String> headers = new ArrayList<>();
        if (headerRow == null) {
            return headers;
        }
        int n = headerRow.getLastCellNum();
        for (int i = 0; i < n; i++) {
            Cell cell = headerRow.getCell(i);
            String val = getCellString(cell);
            headers.add(val == null ? "" : val.trim());
        }
        return headers;
    }

    private Map<String, String> buildExcelImportColumns(List<String> headers, Row row) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String h = headers.get(i);
            if (h.isBlank()) {
                continue;
            }
            Cell cell = row == null ? null : row.getCell(i);
            String v = getCellString(cell);
            m.put(h, v == null ? "" : v.trim());
        }
        return m;
    }

    private Map<String, String> buildCsvImportColumns(List<String> headers, List<String> cells) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String h = headers.get(i);
            if (h.isBlank()) {
                continue;
            }
            String v = getListCell(cells, i);
            m.put(h, v == null ? "" : v);
        }
        return m;
    }

    private Map<String, Integer> resolveColumnIndicesFromHeaders(List<String> headers) {
        return COLUMN_ALIASES.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> findColumnIndexInList(headers, e.getValue())
                ));
    }

    private int findColumnIndexInList(List<String> headers, String[] aliases) {
        for (int i = 0; i < headers.size(); i++) {
            String val = headers.get(i);
            if (val == null) continue;
            String trimmed = val.trim();
            for (String alias : aliases) {
                if (alias.equalsIgnoreCase(trimmed)) return i;
            }
        }
        return -1;
    }

    private CrewMemberDto rowToCrewMember(Row row, Map<String, Integer> colIndex, Map<String, String> importColumns) {
        CrewMemberDto dto = crewMemberFromColumnValues(colIndex, key -> {
            int idx = colIndex.getOrDefault(key, -1);
            if (idx < 0) {
                return null;
            }
            return getCellString(row.getCell(idx));
        });
        dto.setImportColumns(importColumns);
        return dto;
    }

    private CrewMemberDto crewMemberFromColumnValues(Map<String, Integer> colIndex, Function<String, String> get) {
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
                .department(nullToEmpty(get.apply("department")))
                .rank(nullToEmpty(get.apply("rank")))
                .department(nullToEmpty(get.apply("department")))
                .annc(nullToEmpty(get.apply("annc")))
                .qualification(nullToEmpty(get.apply("qualification")))
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

    /** 엑셀 추출 시 팀 내 승무원 행 정렬 방식 */
    public enum ExportMemberSort {
        /** 사번 오름차순 (숫자로 파싱되면 숫자 비교) */
        EMPLOYEE_ID,
        /**
         * 직급·역할 순: 팀장(TP) → 사무장(TS) → 기타, 이후 README 조건과 동일한 SP·PS·AP·SS·ID·IS 토큰 순,
         * 동일하면 사번 오름차순
         */
        GRADE;

        public static ExportMemberSort fromQueryParam(String s) {
            if (s == null || s.isBlank()) return EMPLOYEE_ID;
            String u = s.trim().toLowerCase(Locale.ROOT);
            if ("grade".equals(u) || "직급".equals(u)) return GRADE;
            return EMPLOYEE_ID;
        }
    }

    private static final List<String> DEFAULT_EXPORT_HEADERS = List.of(
            "사번", "이름", "성별", "BASE", "Rank", "Line", "직급", "구분", "(구)TM", "소속팀", "FROM", "ANNC", "Qualification");

    /**
     * 편성 결과를 엑셀 파일로 생성 (바이트 배열 반환). 팀 내 행은 사번 오름차순.
     */
    public byte[] exportTeamsToExcel(List<LineTeamDto> teams) throws Exception {
        return exportTeamsToExcel(teams, ExportMemberSort.EMPLOYEE_ID, null);
    }

    /**
     * 편성 결과를 엑셀 파일로 생성 (바이트 배열 반환)
     *
     * @param memberSort 팀별 멤버 행 출력 순서
     */
    public byte[] exportTeamsToExcel(List<LineTeamDto> teams, ExportMemberSort memberSort) throws Exception {
        return exportTeamsToExcel(teams, memberSort, null);
    }

    /**
     * @param columnHeaders 업로드 시트 헤더 순서(비어 있으면 {@link #DEFAULT_EXPORT_HEADERS})
     */
    public byte[] exportTeamsToExcel(List<LineTeamDto> teams, ExportMemberSort memberSort, List<String> columnHeaders)
            throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("라인팀 편성 결과");
            CellStyle headerStyle = createHeaderStyle(workbook);
            int rowNum = 0;
            ExportMemberSort sort = memberSort != null ? memberSort : ExportMemberSort.EMPLOYEE_ID;

            List<String> headersOut = (columnHeaders != null && !columnHeaders.isEmpty())
                    ? columnHeaders.stream().filter(h -> h != null && !h.isBlank()).collect(Collectors.toList())
                    : DEFAULT_EXPORT_HEADERS;

            for (LineTeamDto team : teams) {
                Row teamHeader = sheet.createRow(rowNum++);
                teamHeader.createCell(0).setCellValue(team.getTeamId());
                teamHeader.getCell(0).setCellStyle(headerStyle);
                int headCount = team.getMemberCount();
                teamHeader.createCell(1).setCellValue(headCount + "명");
                teamHeader.getCell(1).setCellStyle(headerStyle);
                for (int c = 2; c < headersOut.size(); c++) {
                    teamHeader.createCell(c).setCellValue("");
                }

                Row colHeaderRow = sheet.createRow(rowNum++);
                for (int i = 0; i < headersOut.size(); i++) {
                    Cell cell = colHeaderRow.createCell(i);
                    cell.setCellValue(headersOut.get(i));
                    cell.setCellStyle(headerStyle);
                }

                List<CrewMemberDto> membersOut = new ArrayList<>(team.getMembers());
                sortMembersForExport(membersOut, sort);

                for (CrewMemberDto m : membersOut) {
                    Row row = sheet.createRow(rowNum++);
                    for (int i = 0; i < headersOut.size(); i++) {
                        row.createCell(i).setCellValue(exportValueForHeader(team, m, headersOut.get(i)));
                    }
                }
                rowNum++;
            }

            for (int i = 0; i < headersOut.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static String exportValueForHeader(LineTeamDto team, CrewMemberDto m, String header) {
        if (header == null) {
            return "";
        }
        String h = header.trim();
        // 소속팀·(구)TM 은 importColumns(원본 행)보다 편성 결과/원본 소속(department)이 우선
        if ("소속팀".equals(h)) {
            return nz(buildAssignedDepartment(team != null ? team.getTeamId() : null, m));
        }
        if (isLegacyTmColumn(h)) {
            return nz(originalInputDepartment(m));
        }
        if (m.getImportColumns() != null) {
            String v = m.getImportColumns().get(header);
            if (v != null) {
                return v;
            }
        }
        return fallbackExportValue(team, m, h);
    }

    /** RAW의 '소속팀' 컬럼 값(파싱 department, 없으면 importColumns의 소속팀) */
    private static String originalInputDepartment(CrewMemberDto m) {
        String d = nz(m.getDepartment());
        if (!d.isEmpty()) {
            return d;
        }
        if (m.getImportColumns() != null) {
            String fromImport = m.getImportColumns().get("소속팀");
            if (fromImport != null && !fromImport.isBlank()) {
                return fromImport.trim();
            }
        }
        return "";
    }

    /** (구)TM / 구(TM) 등 레거시 TM 열 */
    private static boolean isLegacyTmColumn(String header) {
        if (header == null || header.isBlank()) {
            return false;
        }
        String t = header.replace(" ", "").trim();
        if ("구(TM)".equalsIgnoreCase(t)) {
            return true;
        }
        if ("(구)TM".equalsIgnoreCase(t)) {
            return true;
        }
        String u = t.toUpperCase(Locale.ROOT);
        return u.contains("구") && u.contains("TM");
    }

    private static String fallbackExportValue(LineTeamDto team, CrewMemberDto m, String header) {
        if (header.isEmpty()) {
            return "";
        }
        if ("사번".equals(header)) {
            return nz(m.getEmployeeId());
        }
        if ("이름".equals(header)) {
            return nz(m.getName());
        }
        if ("성별".equals(header)) {
            return nz(m.getGender());
        }
        if ("BASE".equalsIgnoreCase(header)) {
            return nz(m.getBase());
        }
        if ("직급".equals(header)) {
            return nz(m.getGrade());
        }
        if ("FROM".equalsIgnoreCase(header)) {
            return nz(m.getFromColumn());
        }
        if ("RANK".equalsIgnoreCase(header) || "Rank".equals(header)) {
            return nz(m.getPositionCode());
        }
        if ("ANNC".equalsIgnoreCase(header)) {
            return nz(m.getAnnc());
        }
        if ("Qualification".equals(header)) {
            String q = m.getQualification();
            if (q != null && !q.isEmpty()) {
                return q;
            }
            return nz(m.getRank());
        }
        if ("자격".equals(header) || "방송자격".equals(header)) {
            return nz(m.getRank());
        }
        if ("Line".equalsIgnoreCase(header)) {
            return nz(m.getLine());
        }
        if ("구분".equals(header) || "재직상태".equals(header)) {
            return nz(m.getStatus());
        }
        if (isLegacyTmColumn(header)) {
            return nz(originalInputDepartment(m));
        }
        if ("소속팀".equals(header)) {
            return nz(buildAssignedDepartment(team != null ? team.getTeamId() : null, m));
        }
        return "";
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    /** README 직급 균등 조건과 동일한 토큰 순 (앞일수록 상위로 정렬) */
    private static final String[] GRADE_TOKENS_ORDER = {"SP", "PS", "AP", "SS", "ID", "IS"};

    private static void sortMembersForExport(List<CrewMemberDto> members, ExportMemberSort sort) {
        if (members == null || members.size() <= 1) return;
        Comparator<String> byEmpId = ExcelService::compareEmployeeIdStrings;
        Comparator<CrewMemberDto> cmp = switch (sort) {
            case GRADE -> Comparator
                    .comparingInt(ExcelService::exportRoleOrder)
                    .thenComparingInt(ExcelService::gradeTokenOrder)
                    .thenComparing(ExcelService::compareKeysEmployeeId, byEmpId);
            case EMPLOYEE_ID -> Comparator.comparing(ExcelService::compareKeysEmployeeId, byEmpId);
        };
        members.sort(cmp);
    }

    /** TP → TS → 기타 */
    private static int exportRoleOrder(CrewMemberDto m) {
        if (m.isTP()) return 0;
        if (m.isTS()) return 1;
        return 2;
    }

    /**
     * 직급·Rank 컬럼 문자열에서 SP/PS/AP/SS/ID/IS 중 가장 앞선(우선순위 높은) 코드 인덱스.
     * 없으면 맨 뒤로.
     */
    private static int gradeTokenOrder(CrewMemberDto m) {
        String hay = (nullToEmpty(m.getGrade()) + " " + nullToEmpty(m.getPositionCode()))
                .toUpperCase(Locale.ROOT);
        int best = GRADE_TOKENS_ORDER.length;
        for (int i = 0; i < GRADE_TOKENS_ORDER.length; i++) {
            if (hay.contains(GRADE_TOKENS_ORDER[i])) {
                best = Math.min(best, i);
            }
        }
        return best;
    }

    private static String compareKeysEmployeeId(CrewMemberDto m) {
        return m.getEmployeeId() == null ? "" : m.getEmployeeId().trim();
    }

    private static int compareEmployeeIdStrings(String a, String b) {
        if (a == null || a.isEmpty()) return (b == null || b.isEmpty()) ? 0 : -1;
        if (b == null || b.isEmpty()) return 1;
        try {
            return Long.compare(Long.parseLong(a), Long.parseLong(b));
        } catch (NumberFormatException e) {
            return a.compareTo(b);
        }
    }

    /**
     * 소속팀 표기 규칙:
     * - 팀ID 숫자부 + 접미사(RANK 기반)
     * - RANK(TP/TS 등) 칼럼만 사용
     * - RANK가 TS이면 DP로 치환
     * - 그 외는 RANK 값을 그대로 사용 (예: TP -> TP)
     *   예) A101 + TS -> 101DP, A101 + TP -> 101TP
     */
    private static String buildAssignedDepartment(String teamId, CrewMemberDto m) {
        String normalizedTeam = teamId == null ? "" : teamId.trim().toUpperCase(Locale.ROOT);
        String teamNumber = normalizedTeam;
        Matcher matcher = TEAM_NUMBER_PATTERN.matcher(normalizedTeam);
        if (matcher.find()) {
            teamNumber = matcher.group(1);
        }
        String position = m != null && m.getPositionCode() != null
                ? m.getPositionCode().trim().toUpperCase(Locale.ROOT)
                : "";
        if (position.isEmpty()) {
            return teamNumber;
        }
        if ("TS".equals(position)) {
            return teamNumber + "DP";
        }
        return teamNumber + position;
    }
}
