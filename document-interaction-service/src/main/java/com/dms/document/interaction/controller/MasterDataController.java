package com.dms.document.interaction.controller;

import com.dms.document.interaction.constant.ApiConstant;
import com.dms.document.interaction.dto.MasterDataRequest;
import com.dms.document.interaction.dto.MasterDataResponse;
import com.dms.document.interaction.dto.ReportTypeResponse;
import com.dms.document.interaction.enums.MasterDataType;
import com.dms.document.interaction.service.DocumentHistoryService;
import com.dms.document.interaction.service.DocumentReportService;
import com.dms.document.interaction.service.MasterDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(ApiConstant.API_VERSION + ApiConstant.MASTER_DATA)
@RequiredArgsConstructor
@Tag(name = "Master Data", description = "APIs for managing system master data like majors, course codes, levels, and categories")
public class MasterDataController {
    private final MasterDataService masterDataService;
    private final DocumentReportService documentReportService;

    @Operation(summary = "Get master data by type",
            description = "Retrieve all master data entries of specified type")
    @GetMapping("/{type}")
    public ResponseEntity<List<MasterDataResponse>> getAllByType(@PathVariable MasterDataType type, @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(masterDataService.getAllByType(type, active));
    }

    @Operation(summary = "Get master data by type and code",
            description = "Retrieve specific master data entry by type and code")
    @GetMapping("/{type}/{code}")
    public ResponseEntity<MasterDataResponse> getByTypeAndCode(
            @PathVariable MasterDataType type,
            @PathVariable String code) {
        return masterDataService.getByTypeAndCode(type, code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }


    @Operation(summary = "Get master data by parent",
            description = "Retrieve master data entries by type and parent ID")
    @GetMapping("/{type}/parent/{parentId}")
    public ResponseEntity<List<MasterDataResponse>> getAllByTypeAndParentId(
            @PathVariable MasterDataType type,
            @PathVariable String parentId,
            @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(masterDataService.getAllByTypeAndParentId(type, parentId, active));
    }

    @Operation(summary = "Search master data",
            description = "Search master data entries across all types")
    @GetMapping("/search")
    public ResponseEntity<List<MasterDataResponse>> searchByText(@RequestParam String query) {
        return ResponseEntity.ok(masterDataService.searchByText(query));
    }

    @Operation(summary = "Create master data",
            description = "Create new master data entry")
    @PostMapping
    public ResponseEntity<MasterDataResponse> create(@RequestBody MasterDataRequest request) {
        return ResponseEntity.ok(masterDataService.save(request));
    }

    @Operation(summary = "Update master data",
            description = "Update existing master data entry")
    @PutMapping("/{id}")
    public ResponseEntity<MasterDataResponse> update(
            @PathVariable String id,
            @RequestBody MasterDataRequest request) {
        return ResponseEntity.ok(masterDataService.update(id, request));
    }

    @Operation(summary = "Delete master data",
            description = "Delete master data entry and update its children")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        masterDataService.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Get available report types",
            description = "Get list of available report types with translations")
    @GetMapping("/reports/types")
    public ResponseEntity<List<ReportTypeResponse>> getReportTypes() {
        return ResponseEntity.ok(documentReportService.getReportTypes());
    }
}