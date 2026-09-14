package com.icepark.controller;

import com.icepark.dto.AdjustRequestDTO;
import com.icepark.dto.SessionEquipmentDTO;
import com.icepark.entity.AdjustRecord;
import com.icepark.service.SessionEquipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/session/{sessionId}/equipment")
@RequiredArgsConstructor
public class SessionEquipmentController {
    
    private final SessionEquipmentService sessionEquipmentService;
    
    @GetMapping
    public ResponseEntity<List<SessionEquipmentDTO>> getSessionEquipments(@PathVariable Long sessionId) {
        return ResponseEntity.ok(sessionEquipmentService.getSessionEquipments(sessionId));
    }
    
    @PostMapping
    public ResponseEntity<SessionEquipmentDTO> bindEquipment(
            @PathVariable Long sessionId,
            @RequestBody Map<String, Object> request) {
        Long equipmentId = Long.valueOf(request.get("equipmentId").toString());
        String targetAgeGroup = request.get("targetAgeGroup").toString();
        return ResponseEntity.ok(sessionEquipmentService.bindEquipment(sessionId, equipmentId, targetAgeGroup));
    }
    
    @DeleteMapping("/{equipmentId}")
    public ResponseEntity<Void> unbindEquipment(
            @PathVariable Long sessionId,
            @PathVariable Long equipmentId) {
        sessionEquipmentService.unbindEquipment(sessionId, equipmentId);
        return ResponseEntity.noContent().build();
    }
    
    @PutMapping("/{equipmentId}/adjust")
    public ResponseEntity<Void> adjustEquipmentAgeGroup(
            @PathVariable Long sessionId,
            @PathVariable Long equipmentId,
            @RequestBody Map<String, String> request) {
        String newAgeGroup = request.get("newAgeGroup");
        String adjustReason = request.get("adjustReason");
        String operator = request.get("operator");
        sessionEquipmentService.adjustEquipmentAgeGroup(sessionId, equipmentId, newAgeGroup, adjustReason, operator);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/auto-bind")
    public ResponseEntity<Map<String, String>> autoBindByRatio(@PathVariable Long sessionId) {
        sessionEquipmentService.autoBindByRatio(sessionId);
        return ResponseEntity.ok(Map.of("message", "自动绑定完成"));
    }
    
    @PutMapping("/auto-adjust")
    public ResponseEntity<List<AdjustRecord>> autoAdjustByRatio(
            @PathVariable Long sessionId,
            @Valid @RequestBody AdjustRequestDTO request) {
        request.setSessionId(sessionId);
        List<AdjustRecord> records = sessionEquipmentService.autoAdjustByRatio(request);
        return ResponseEntity.ok(records);
    }
}