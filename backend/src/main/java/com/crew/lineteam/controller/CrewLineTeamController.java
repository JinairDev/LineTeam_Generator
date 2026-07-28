package com.crew.lineteam.controller;

import com.crew.lineteam.dto.AssignRequest;
import com.crew.lineteam.dto.AssignResponse;
import com.crew.lineteam.dto.CreateTeamShellsRequest;
import com.crew.lineteam.dto.CrewMemberDto;
import com.crew.lineteam.dto.CrewUploadResponse;
import com.crew.lineteam.dto.ExportTeamsRequest;
import com.crew.lineteam.dto.FpYyBalanceReport;
import com.crew.lineteam.dto.GoogleSpreadsheetImportRequest;
import com.crew.lineteam.dto.LineTeamDto;
import com.crew.lineteam.dto.MoveMemberResponse;
import com.crew.lineteam.dto.TeamShellsResponse;
import com.crew.lineteam.service.ExcelService;
import com.crew.lineteam.service.FpYyBalanceService;
import com.crew.lineteam.service.GoogleSpreadsheetImportService;
import com.crew.lineteam.service.TeamAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CrewLineTeamController {

    private final ExcelService excelService;
    private final GoogleSpreadsheetImportService googleSpreadsheetImportService;
    private final TeamAssignmentService teamAssignmentService;
    private final FpYyBalanceService fpYyBalanceService;

    /**
     * 1. 재직 현황 엑셀 업로드 → 파싱된 승무원 목록 반환
     */
    @PostMapping("/upload")
    public ResponseEntity<CrewUploadResponse> uploadExcel(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("파일을 선택해 주세요.");
        }
        String name = file.getOriginalFilename();
        if (name != null && !name.endsWith(".xlsx") && !name.endsWith(".xls")) {
            throw new IllegalArgumentException("엑셀 파일(.xlsx, .xls)만 업로드할 수 있습니다.");
        }
        try {
            CrewUploadResponse result = excelService.parseCrewExcel(file.getInputStream());
            if (result.getCrew() == null || result.getCrew().isEmpty()) {
                throw new IllegalArgumentException("사번·이름이 있는 데이터 행이 없습니다. 엑셀 첫 행에 헤더(사번, 이름, BASE 등)가 있는지 확인해 주세요.");
            }
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("엑셀 파싱 중 오류가 발생했습니다. " + (e.getMessage() != null ? e.getMessage() : ""), e);
        }
    }

    /**
     * 1. 재직 현황: Google 스프레드시트(공개 링크)에서 CSV로 가져와 파싱
     */
    @PostMapping("/upload-from-google")
    public ResponseEntity<CrewUploadResponse> uploadFromGoogle(@RequestBody GoogleSpreadsheetImportRequest body) {
        if (body == null || body.getSpreadsheetUrl() == null || body.getSpreadsheetUrl().isBlank()) {
            throw new IllegalArgumentException("Google 스프레드시트 URL 또는 ID를 입력해 주세요.");
        }
        try {
            CrewUploadResponse result = googleSpreadsheetImportService.importFromUrlOrId(body.getSpreadsheetUrl());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Google 스프레드시트를 불러오는 중 오류가 발생했습니다. "
                            + (e.getMessage() != null ? e.getMessage() : ""),
                    e);
        }
    }

    /**
     * 1. 재직 현황: 이미 받은 CSV 텍스트 파싱.
     * - {@code multipart/form-data}: 붙여넣기·대용량에 권장(JSON 이스케이프로 본문이 비대해지거나 500이 나는 경우 방지)
     * - {@code application/json}: 호환용
     * - {@code text/plain}: 호환용
     */
    @PostMapping(value = "/upload-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CrewUploadResponse> uploadCsvMultipart(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("CSV 파일이 비어 있습니다.");
        }
        try {
            String csvBody = new String(file.getBytes(), StandardCharsets.UTF_8);
            return uploadCsvResponse(csvBody);
        } catch (IOException e) {
            throw new IllegalArgumentException("CSV를 읽을 수 없습니다.", e);
        }
    }

    @PostMapping(value = "/upload-csv", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CrewUploadResponse> uploadCsvJson(@RequestBody CsvTextRequest body) {
        String csvBody = body != null ? body.getCsv() : null;
        return uploadCsvResponse(csvBody);
    }

    @PostMapping(value = "/upload-csv", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<CrewUploadResponse> uploadCsvPlain(@RequestBody String csvBody) {
        return uploadCsvResponse(csvBody);
    }

    private ResponseEntity<CrewUploadResponse> uploadCsvResponse(String csvBody) {
        if (csvBody == null || csvBody.isBlank()) {
            throw new IllegalArgumentException("CSV 내용이 비어 있습니다.");
        }
        try {
            CrewUploadResponse result = excelService.parseCrewCsv(new StringReader(csvBody));
            if (result.getCrew() == null || result.getCrew().isEmpty()) {
                throw new IllegalArgumentException(
                        "사번·이름이 있는 데이터 행이 없습니다. 첫 행에 헤더(사번, 이름, BASE 등)가 있는지 확인해 주세요.");
            }
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "CSV 파싱 중 오류가 발생했습니다. "
                            + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()),
                    e);
        }
    }

    /**
     * 사전 TP/TS 배정용: 편성 규칙에 맞는 팀 껍데기 + 소속팀 선배치 결과
     */
    @PostMapping("/teams/shells")
    public ResponseEntity<TeamShellsResponse> createTeamShells(@RequestBody CreateTeamShellsRequest request) {
        if (request == null || request.getCrew() == null) {
            throw new IllegalArgumentException("승무원 목록이 없습니다.");
        }
        if (request.getCrew().isEmpty()) {
            throw new IllegalArgumentException("승무원 목록이 비어 있습니다.");
        }
        TeamShellsResponse response = teamAssignmentService.createTeamShells(request.getCrew());
        if (response.getTeams() == null || response.getTeams().isEmpty()) {
            throw new IllegalArgumentException("편성 대상 승무원이 없습니다. (차출·휴직 제외 후 인원 확인)");
        }
        return ResponseEntity.ok(response);
    }

    /**
     * 2. 편성 실행: 승무원 목록과 선택적 지역별 팀 수를 받아 라인팀 목록 반환
     */
    @PostMapping("/assign")
    public ResponseEntity<AssignResponse> assign(@RequestBody AssignRequest request) {
        if (request == null || request.getCrew() == null) {
            throw new IllegalArgumentException("승무원 목록이 없습니다.");
        }
        if (request.getCrew().isEmpty()) {
            throw new IllegalArgumentException("승무원 목록이 비어 있습니다.");
        }
        if (request.getPinMode() != null
                && (request.getPreviousTeams() == null || request.getPreviousTeams().isEmpty())) {
            throw new IllegalArgumentException("재편성(고정)에는 이전 팀 정보(previousTeams)가 필요합니다.");
        }
        AssignResponse response;
        if (request.getPinMode() != null && request.getPreviousTeams() != null && !request.getPreviousTeams().isEmpty()) {
            response = teamAssignmentService.assignWithBalanceReport(
                    request.getCrew(),
                    request.getTeamCountByBase(),
                    request.getPreviousTeams(),
                    request.getPinMode()
            );
        } else {
            response = teamAssignmentService.assignWithBalanceReport(
                    request.getCrew(),
                    request.getTeamCountByBase()
            );
        }
        return ResponseEntity.ok(response);
    }

    /**
     * 3. 수동 이동: 특정 멤버를 다른 팀으로 이동 (이동 후 균등 분배 검증 결과 포함)
     */
    @PatchMapping("/teams/move")
    public ResponseEntity<MoveMemberResponse> moveMember(
            @RequestBody MoveMemberRequest request
    ) {
        List<LineTeamDto> teams = request.getTeams();
        String employeeId = request.getEmployeeId();
        String fromTeamId = request.getFromTeamId();
        String toTeamId = request.getToTeamId();
        Integer toIndex = request.getToIndex();

        if (teams == null || teams.isEmpty()) {
            throw new IllegalArgumentException("팀 목록이 없습니다.");
        }
        if (employeeId == null || employeeId.isBlank()) {
            throw new IllegalArgumentException("대상 사번이 없습니다.");
        }

        CrewMemberDto member = null;
        int fromIndex = -1;
        for (LineTeamDto t : teams) {
            if (t != null && t.getTeamId() != null && t.getTeamId().equals(fromTeamId)) {
                for (int i = 0; i < t.getMembers().size(); i++) {
                    if (employeeId.equals(t.getMembers().get(i).getEmployeeId())) {
                        member = t.getMembers().get(i);
                        fromIndex = i;
                        break;
                    }
                }
                if (member != null) {
                    t.getMembers().remove(member);
                    break;
                }
            }
        }
        if (member == null) {
            throw new IllegalArgumentException("해당 팀에서 사번 " + employeeId + " 인원을 찾을 수 없습니다.");
        }
        boolean toTeamExists = teams.stream().anyMatch(t -> t != null && t.getTeamId() != null && t.getTeamId().equals(toTeamId));
        if (!toTeamExists) {
            throw new IllegalArgumentException("이동할 팀을 찾을 수 없습니다: " + toTeamId);
        }

        if (member.isTP() && !fromTeamId.equals(toTeamId)) {
            LineTeamDto targetTeam = null;
            for (LineTeamDto t : teams) {
                if (t != null && toTeamId.equals(t.getTeamId())) {
                    targetTeam = t;
                    break;
                }
            }
            if (targetTeam != null) {
                CrewMemberDto existingTp = null;
                int existingTpIndex = -1;
                for (int i = 0; i < targetTeam.getMembers().size(); i++) {
                    CrewMemberDto m = targetTeam.getMembers().get(i);
                    if (m != null && m.isTP()) {
                        existingTp = m;
                        existingTpIndex = i;
                        break;
                    }
                }
                if (existingTp != null && !employeeId.equals(existingTp.getEmployeeId())) {
                    targetTeam.getMembers().remove(existingTp);
                    int slot = existingTpIndex >= 0 ? existingTpIndex : 0;
                    if (slot > targetTeam.getMembers().size()) {
                        slot = targetTeam.getMembers().size();
                    }
                    targetTeam.getMembers().add(slot, member);
                    for (LineTeamDto fromT : teams) {
                        if (fromT != null && fromTeamId.equals(fromT.getTeamId())) {
                            fromT.getMembers().add(0, existingTp);
                            break;
                        }
                    }
                    return moveMemberResponse(teams);
                }
            }
        }

        int insertIndex = (toIndex != null && toIndex >= 0)
                ? toIndex
                : -1; // -1 = append
        if (member.isTP() && !fromTeamId.equals(toTeamId) && insertIndex < 0) {
            insertIndex = 0;
        }
        if (fromTeamId.equals(toTeamId) && fromIndex >= 0 && insertIndex > fromIndex) {
            insertIndex--;
        }

        for (LineTeamDto t : teams) {
            if (t.getTeamId().equals(toTeamId)) {
                if (member.isTP()) {
                    boolean hasOtherTp = t.getMembers().stream().anyMatch(CrewMemberDto::isTP);
                    if (hasOtherTp) {
                        throw new IllegalArgumentException("팀당 TP는 1명만 배정할 수 있습니다.");
                    }
                }
                if (insertIndex >= 0 && insertIndex <= t.getMembers().size()) {
                    t.getMembers().add(insertIndex, member);
                } else {
                    t.getMembers().add(member);
                }
                break;
            }
        }
        return moveMemberResponse(teams);
    }

    /**
     * 편성·수동 이동 후 팀 구성의 균등 분배 검증만 수행
     */
    @PostMapping("/teams/balance-verify")
    public ResponseEntity<FpYyBalanceReport> verifyTeamBalance(@RequestBody ExportTeamsRequest body) {
        if (body == null || body.getTeams() == null || body.getTeams().isEmpty()) {
            throw new IllegalArgumentException("검증할 팀 목록이 없습니다.");
        }
        return ResponseEntity.ok(fpYyBalanceService.verify(body.getTeams()));
    }

    private ResponseEntity<MoveMemberResponse> moveMemberResponse(List<LineTeamDto> teams) {
        return ResponseEntity.ok(MoveMemberResponse.builder()
                .teams(teams)
                .fpYyBalance(fpYyBalanceService.verify(teams))
                .build());
    }

    /**
     * 4. 편성 결과 엑셀 다운로드
     */
    @PostMapping("/export")
    public ResponseEntity<byte[]> exportExcel(
            @RequestBody ExportTeamsRequest body,
            @RequestParam(value = "sort", required = false) String sort) {
        if (body == null || body.getTeams() == null || body.getTeams().isEmpty()) {
            throw new IllegalArgumentException("내보낼 팀 목록이 없습니다.");
        }
        try {
            byte[] bytes = excelService.exportTeamsToExcel(
                    body.getTeams(),
                    ExcelService.ExportMemberSort.fromQueryParam(sort),
                    body.getColumnHeaders());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", "라인팀 생성 결과.xlsx");
            return ResponseEntity.ok().headers(headers).body(bytes);
        } catch (Exception e) {
            throw new RuntimeException("엑셀 생성 중 오류가 발생했습니다. " + (e.getMessage() != null ? e.getMessage() : ""), e);
        }
    }

    @lombok.Data
    public static class CsvTextRequest {
        /** CSV 전체 문자열 */
        private String csv;
    }

    @lombok.Data
    public static class MoveMemberRequest {
        private List<LineTeamDto> teams;
        private String employeeId;
        private String fromTeamId;
        private String toTeamId;
        /** 삽입할 위치 (0-based). 없으면 맨 뒤에 추가 */
        private Integer toIndex;
    }
}
