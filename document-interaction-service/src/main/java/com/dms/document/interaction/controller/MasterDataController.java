package com.dms.document.interaction.controller;

import com.dms.document.interaction.dto.MasterDataRequest;
import com.dms.document.interaction.dto.MasterDataResponse;
import com.dms.document.interaction.enums.MasterDataType;
import com.dms.document.interaction.service.MasterDataService;
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
    public ResponseEntity<List<MasterDataResponse>> getAllByType(@PathVariable MasterDataType type, @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(masterDataService.getAllByType(type, active));
    }

    @GetMapping("/{type}/{code}")
    public ResponseEntity<MasterDataResponse> getByTypeAndCode(
            @PathVariable MasterDataType type,
            @PathVariable String code) {
        return masterDataService.getByTypeAndCode(type, code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }


    @GetMapping("/{type}/parent/{parentId}")
    public ResponseEntity<List<MasterDataResponse>> getAllByTypeAndParentId(
            @PathVariable MasterDataType type,
            @PathVariable String parentId,
            @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(masterDataService.getAllByTypeAndParentId(type, parentId, active));
    }

    @GetMapping("/search")
    public ResponseEntity<List<MasterDataResponse>> searchByText(@RequestParam String query) {
        return ResponseEntity.ok(masterDataService.searchByText(query));
    }

    @PostMapping
    public ResponseEntity<MasterDataResponse> create(@RequestBody MasterDataRequest request) {
        return ResponseEntity.ok(masterDataService.save(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MasterDataResponse> update(
            @PathVariable String id,
            @RequestBody MasterDataRequest request) {
        return ResponseEntity.ok(masterDataService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        masterDataService.deleteById(id);
        return ResponseEntity.ok().build();
    }
}