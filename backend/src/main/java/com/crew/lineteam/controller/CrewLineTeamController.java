package com.crew.lineteam.controller;

import com.crew.lineteam.dto.AssignRequest;
import com.crew.lineteam.dto.CrewMemberDto;
import com.crew.lineteam.dto.GoogleSpreadsheetImportRequest;
import com.crew.lineteam.dto.LineTeamDto;
import com.crew.lineteam.service.ExcelService;
import com.crew.lineteam.service.GoogleSpreadsheetImportService;
import com.crew.lineteam.service.TeamAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.StringReader;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CrewLineTeamController {

    private final ExcelService excelService;
    private final GoogleSpreadsheetImportService googleSpreadsheetImportService;
    private final TeamAssignmentService teamAssignmentService;

    /**
     * 1. 재직 현황 엑셀 업로드 → 파싱된 승무원 목록 반환
     */
    @PostMapping("/upload")
    public ResponseEntity<List<CrewMemberDto>> uploadExcel(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("파일을 선택해 주세요.");
        }
        String name = file.getOriginalFilename();
        if (name != null && !name.endsWith(".xlsx") && !name.endsWith(".xls")) {
            throw new IllegalArgumentException("엑셀 파일(.xlsx, .xls)만 업로드할 수 있습니다.");
        }
        try {
            List<CrewMemberDto> crew = excelService.parseCrewExcel(file.getInputStream());
            if (crew == null || crew.isEmpty()) {
                throw new IllegalArgumentException("사번·이름이 있는 데이터 행이 없습니다. 엑셀 첫 행에 헤더(사번, 이름, BASE 등)가 있는지 확인해 주세요.");
            }
            return ResponseEntity.ok(crew);
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
    public ResponseEntity<List<CrewMemberDto>> uploadFromGoogle(@RequestBody GoogleSpreadsheetImportRequest body) {
        if (body == null || body.getSpreadsheetUrl() == null || body.getSpreadsheetUrl().isBlank()) {
            throw new IllegalArgumentException("Google 스프레드시트 URL 또는 ID를 입력해 주세요.");
        }
        try {
            List<CrewMemberDto> crew = googleSpreadsheetImportService.importFromUrlOrId(body.getSpreadsheetUrl());
            return ResponseEntity.ok(crew);
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
     * 1. 재직 현황: 이미 받은 CSV 텍스트(브라우저·프록시 등) 파싱
     */
    @PostMapping(value = "/upload-csv", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<List<CrewMemberDto>> uploadCsv(@RequestBody String csvBody) {
        if (csvBody == null || csvBody.isBlank()) {
            throw new IllegalArgumentException("CSV 내용이 비어 있습니다.");
        }
        try {
            List<CrewMemberDto> crew = excelService.parseCrewCsv(new StringReader(csvBody));
            if (crew == null || crew.isEmpty()) {
                throw new IllegalArgumentException(
                        "사번·이름이 있는 데이터 행이 없습니다. 첫 행에 헤더(사번, 이름, BASE 등)가 있는지 확인해 주세요.");
            }
            return ResponseEntity.ok(crew);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("CSV 파싱 중 오류가 발생했습니다. " + (e.getMessage() != null ? e.getMessage() : ""), e);
        }
    }

    /**
     * 2. 편성 실행: 승무원 목록과 선택적 지역별 팀 수를 받아 라인팀 목록 반환
     */
    @PostMapping("/assign")
    public ResponseEntity<List<LineTeamDto>> assign(@RequestBody AssignRequest request) {
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
        List<LineTeamDto> teams;
        if (request.getPinMode() != null && request.getPreviousTeams() != null && !request.getPreviousTeams().isEmpty()) {
            teams = teamAssignmentService.assign(
                    request.getCrew(),
                    request.getTeamCountByBase(),
                    request.getPreviousTeams(),
                    request.getPinMode()
            );
        } else {
            teams = teamAssignmentService.assign(
                    request.getCrew(),
                    request.getTeamCountByBase()
            );
        }
        return ResponseEntity.ok(teams);
    }

    /**
     * 3. 수동 이동: 특정 멤버를 다른 팀으로 이동
     */
    @PatchMapping("/teams/move")
    public ResponseEntity<List<LineTeamDto>> moveMember(
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

        int insertIndex = (toIndex != null && toIndex >= 0)
                ? toIndex
                : -1; // -1 = append
        if (fromTeamId.equals(toTeamId) && fromIndex >= 0 && insertIndex > fromIndex) {
            insertIndex--;
        }

        for (LineTeamDto t : teams) {
            if (t.getTeamId().equals(toTeamId)) {
                if (insertIndex >= 0 && insertIndex <= t.getMembers().size()) {
                    t.getMembers().add(insertIndex, member);
                } else {
                    t.getMembers().add(member);
                }
                break;
            }
        }
        return ResponseEntity.ok(teams);
    }

    /**
     * 4. 편성 결과 엑셀 다운로드
     */
    @PostMapping("/export")
    public ResponseEntity<byte[]> exportExcel(@RequestBody List<LineTeamDto> teams) {
        if (teams == null || teams.isEmpty()) {
            throw new IllegalArgumentException("내보낼 팀 목록이 없습니다.");
        }
        try {
            byte[] bytes = excelService.exportTeamsToExcel(teams);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", "라인팀 생성 결과.xlsx");
            return ResponseEntity.ok().headers(headers).body(bytes);
        } catch (Exception e) {
            throw new RuntimeException("엑셀 생성 중 오류가 발생했습니다. " + (e.getMessage() != null ? e.getMessage() : ""), e);
        }
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
