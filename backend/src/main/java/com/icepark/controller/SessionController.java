package com.icepark.controller;

import com.icepark.dto.SessionDTO;
import com.icepark.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSession(@PathVariable Long id) {
        sessionService.deleteSession(id);
        return ResponseEntity.noContent().build();
    }

    /** 开始场次：已安排 -> 进行中 */
    @PostMapping("/{id}/start")
    public ResponseEntity<SessionDTO> startSession(@PathVariable Long id) {
        return ResponseEntity.ok(sessionService.startSession(id));
    }

    /** 结束场次：进行中 -> 已结束，同事务兜底归还所有未归还器材 */
    @PostMapping("/{id}/end")
    public ResponseEntity<SessionDTO> endSession(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String operator = body == null ? null : body.get("operator");
        return ResponseEntity.ok(sessionService.endSession(id, operator));
    }
}