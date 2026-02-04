package com.crew.lineteam.controller;

import com.crew.lineteam.dto.CrewMemberDto;
import com.crew.lineteam.dto.LineTeamDto;
import com.crew.lineteam.service.ExcelService;
import com.crew.lineteam.service.TeamAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CrewLineTeamController {

    private final ExcelService excelService;
    private final TeamAssignmentService teamAssignmentService;

    /**
     * 1. 재직 현황 엑셀 업로드 → 파싱된 승무원 목록 반환
     */
    @PostMapping("/upload")
    public ResponseEntity<List<CrewMemberDto>> uploadExcel(@RequestParam("file") MultipartFile file) {
        try {
            List<CrewMemberDto> crew = excelService.parseCrewExcel(file.getInputStream());
            return ResponseEntity.ok(crew);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 2. 편성 실행: 승무원 목록을 받아 라인팀 목록 반환
     */
    @PostMapping("/assign")
    public ResponseEntity<List<LineTeamDto>> assign(@RequestBody List<CrewMemberDto> crew) {
        List<LineTeamDto> teams = teamAssignmentService.assign(crew);
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

        CrewMemberDto member = null;
        int fromIndex = -1;
        for (LineTeamDto t : teams) {
            if (t.getTeamId().equals(fromTeamId)) {
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
        if (member == null) return ResponseEntity.badRequest().build();

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
        try {
            byte[] bytes = excelService.exportTeamsToExcel(teams);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", "라인팀 생성 결과.xlsx");
            return ResponseEntity.ok().headers(headers).body(bytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
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
