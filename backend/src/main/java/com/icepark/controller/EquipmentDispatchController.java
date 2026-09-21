package com.icepark.controller;

import com.icepark.dto.EquipmentDispatchRecordDTO;
import com.icepark.dto.IssueRequestDTO;
import com.icepark.dto.ReturnRequestDTO;
import com.icepark.dto.SessionDispatchItemDTO;
import com.icepark.service.EquipmentDispatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 入场发装台：现场发装、归还、现场视图、流水查询。
 */
@RestController
@RequestMapping("/api/session/{sessionId}/dispatch")
@RequiredArgsConstructor
public class EquipmentDispatchController {

    private final EquipmentDispatchService equipmentDispatchService;

    /** 现场视图：本场次绑定的全部器材及当前发装状态 */
    @GetMapping("/items")
    public ResponseEntity<List<SessionDispatchItemDTO>> listItems(@PathVariable Long sessionId) {
        return ResponseEntity.ok(equipmentDispatchService.listSessionItems(sessionId));
    }

    /** 发装流水：谁在什么时候把哪件器材发给了哪位游客、是否归还、何时归还 */
    @GetMapping("/records")
    public ResponseEntity<List<EquipmentDispatchRecordDTO>> listRecords(@PathVariable Long sessionId) {
        return ResponseEntity.ok(equipmentDispatchService.listRecords(sessionId));
    }

    /**
     * 发装。并发领用同一件器材时，后来的一方收到 409「已被领用」。
     * 年龄段/气温不匹配、场次非进行中返回 400 并说明具体原因。
     */
    @PostMapping("/issue")
    public ResponseEntity<EquipmentDispatchRecordDTO> issue(
            @PathVariable Long sessionId,
            @Valid @RequestBody IssueRequestDTO request) {
        return ResponseEntity.ok(equipmentDispatchService.issue(sessionId, request));
    }

    /** 归还（按流水ID归还；流水ID从现场视图/流水中获取） */
    @PostMapping("/records/{recordId}/return")
    public ResponseEntity<EquipmentDispatchRecordDTO> returnEquipment(
            @PathVariable Long sessionId,
            @PathVariable Long recordId,
            @Valid @RequestBody ReturnRequestDTO request) {
        return ResponseEntity.ok(equipmentDispatchService.returnEquipment(
                sessionId, recordId, request.getOperator()));
    }
}
