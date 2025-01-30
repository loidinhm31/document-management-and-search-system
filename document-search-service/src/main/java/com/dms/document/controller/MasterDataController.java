package com.dms.document.controller;

import com.dms.document.enums.MasterDataType;
import com.dms.document.model.MasterData;
import com.dms.document.service.MasterDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/master-data")
@RequiredArgsConstructor
public class MasterDataController {
    private final MasterDataService masterDataService;

    @GetMapping("/{type}")
    public ResponseEntity<List<MasterData>> getAllByType(@PathVariable MasterDataType type) {
        return ResponseEntity.ok(masterDataService.getAllByType(type));
    }

    @GetMapping("/{type}/active")
    public ResponseEntity<List<MasterData>> getAllActiveByType(@PathVariable MasterDataType type) {
        return ResponseEntity.ok(masterDataService.getAllActiveByType(type));
    }

    @GetMapping("/{type}/{code}")
    public ResponseEntity<MasterData> getByTypeAndCode(
            @PathVariable MasterDataType type,
            @PathVariable String code) {
        return masterDataService.getByTypeAndCode(type, code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    public ResponseEntity<List<MasterData>> searchByText(@RequestParam String query) {
        return ResponseEntity.ok(masterDataService.searchByText(query));
    }

    @PostMapping
    public ResponseEntity<MasterData> create(@RequestBody MasterData masterData) {
        return ResponseEntity.ok(masterDataService.save(masterData));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MasterData> update(
            @PathVariable String id,
            @RequestBody MasterData masterData) {
        masterData.setId(id);
        return ResponseEntity.ok(masterDataService.save(masterData));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        masterDataService.deleteById(id);
        return ResponseEntity.ok().build();
    }
}