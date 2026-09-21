package com.icepark.controller;

import com.icepark.dto.IssueRecordDTO;
import com.icepark.dto.IssueRequestDTO;
import com.icepark.dto.ReturnRequestDTO;
import com.icepark.dto.SessionIssueOverviewDTO;
import com.icepark.service.IssueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/session/{sessionId}/issue")
@RequiredArgsConstructor
public class IssueController {

    private final IssueService issueService;

    /** 发装台总览：场次状态 + 绑定器材在库/占用 + 本场流水 */
    @GetMapping("/overview")
    public ResponseEntity<SessionIssueOverviewDTO> overview(@PathVariable Long sessionId) {
        return ResponseEntity.ok(issueService.getOverview(sessionId));
    }

    /** 按场次查询发装/归还流水 */
    @GetMapping("/records")
    public ResponseEntity<List<IssueRecordDTO>> records(@PathVariable Long sessionId) {
        return ResponseEntity.ok(issueService.getRecordsBySession(sessionId));
    }

    /** 发装（并发安全；返回 200 成功，409 表示该器材已被领用） */
    @PostMapping
    public ResponseEntity<IssueRecordDTO> issue(
            @PathVariable Long sessionId,
            @Valid @RequestBody IssueRequestDTO request) {
        return ResponseEntity.ok(issueService.issue(sessionId, request));
    }

    /** 归还 */
    @PutMapping("/{issueId}/return")
    public ResponseEntity<IssueRecordDTO> returnIssue(
            @PathVariable Long sessionId,
            @PathVariable Long issueId,
            @Valid @RequestBody ReturnRequestDTO request) {
        return ResponseEntity.ok(issueService.returnIssue(sessionId, issueId, request.getOperator()));
    }
}
