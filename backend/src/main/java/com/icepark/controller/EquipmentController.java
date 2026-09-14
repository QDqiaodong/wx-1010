package com.icepark.controller;

import com.icepark.dto.EquipmentDTO;
import com.icepark.dto.AgeGroupSummaryDTO;
import com.icepark.service.EquipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/equipment")
@RequiredArgsConstructor
public class EquipmentController {
    
    private final EquipmentService equipmentService;
    
    @GetMapping
    public ResponseEntity<List<EquipmentDTO>> getAllEquipments(
            @RequestParam(required = false) String ageGroup) {
        if (ageGroup != null && !ageGroup.isEmpty()) {
            return ResponseEntity.ok(equipmentService.getEquipmentsByAgeGroup(ageGroup));
        }
        return ResponseEntity.ok(equipmentService.getAllEquipments());
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<EquipmentDTO> getEquipmentById(@PathVariable Long id) {
        return ResponseEntity.ok(equipmentService.getEquipmentById(id));
    }
    
    @GetMapping("/age-group/{ageGroup}")
    public ResponseEntity<List<EquipmentDTO>> getEquipmentsByAgeGroup(@PathVariable String ageGroup) {
        return ResponseEntity.ok(equipmentService.getEquipmentsByAgeGroup(ageGroup));
    }
    
    @GetMapping("/summary")
    public ResponseEntity<List<AgeGroupSummaryDTO>> getSummaryByAgeGroup() {
        return ResponseEntity.ok(equipmentService.getSummaryByAgeGroup());
    }
    
    @PostMapping
    public ResponseEntity<EquipmentDTO> createEquipment(@Valid @RequestBody EquipmentDTO dto) {
        return ResponseEntity.ok(equipmentService.createEquipment(dto));
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<EquipmentDTO> updateEquipment(
            @PathVariable Long id, @Valid @RequestBody EquipmentDTO dto) {
        return ResponseEntity.ok(equipmentService.updateEquipment(id, dto));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEquipment(@PathVariable Long id) {
        equipmentService.deleteEquipment(id);
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/refresh-cache")
    public ResponseEntity<Map<String, String>> refreshCache() {
        equipmentService.refreshCache();
        return ResponseEntity.ok(Map.of("message", "缓存已刷新"));
    }
}