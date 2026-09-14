package com.icepark.controller;

import com.icepark.dto.AdjustRecordDTO;
import com.icepark.service.AdjustRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/adjust-record")
@RequiredArgsConstructor
public class AdjustRecordController {
    
    private final AdjustRecordService adjustRecordService;
    
    @GetMapping
    public ResponseEntity<List<AdjustRecordDTO>> getAllRecords() {
        return ResponseEntity.ok(adjustRecordService.getAllRecords());
    }
    
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<List<AdjustRecordDTO>> getRecordsBySession(@PathVariable Long sessionId) {
        return ResponseEntity.ok(adjustRecordService.getRecordsBySession(sessionId));
    }
}