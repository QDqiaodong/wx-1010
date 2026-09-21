package com.icepark.controller;

import com.icepark.dto.SessionDTO;
import com.icepark.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/session")
@RequiredArgsConstructor
public class SessionController {
    
    private final SessionService sessionService;
    
    @GetMapping
    public ResponseEntity<List<SessionDTO>> getAllSessions() {
        return ResponseEntity.ok(sessionService.getAllSessions());
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<SessionDTO> getSessionById(@PathVariable Long id) {
        return ResponseEntity.ok(sessionService.getSessionById(id));
    }
    
    @PostMapping
    public ResponseEntity<SessionDTO> createSession(@Valid @RequestBody SessionDTO dto) {
        return ResponseEntity.ok(sessionService.createSession(dto));
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<SessionDTO> updateSession(
            @PathVariable Long id, @Valid @RequestBody SessionDTO dto) {
        return ResponseEntity.ok(sessionService.updateSession(id, dto));
    }

    /** 场次开始：已安排 -> 进行中，开始后才允许发装 */
    @PostMapping("/{id}/start")
    public ResponseEntity<SessionDTO> startSession(@PathVariable Long id) {
        return ResponseEntity.ok(sessionService.startSession(id));
    }

    /** 场次结束：进行中 -> 已结束，同事务兜底收回全部未归还器材 */
    @PostMapping("/{id}/end")
    public ResponseEntity<SessionDTO> endSession(@PathVariable Long id) {
        return ResponseEntity.ok(sessionService.endSession(id));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSession(@PathVariable Long id) {
        sessionService.deleteSession(id);
        return ResponseEntity.noContent().build();
    }
}