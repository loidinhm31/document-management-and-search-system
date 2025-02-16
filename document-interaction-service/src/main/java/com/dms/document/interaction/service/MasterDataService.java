package com.dms.document.interaction.service;

import com.dms.document.interaction.dto.MasterDataRequest;
import com.dms.document.interaction.dto.MasterDataResponse;
import com.dms.document.interaction.dto.TranslationDTO;
import com.dms.document.interaction.enums.MasterDataType;
import com.dms.document.interaction.model.MasterData;
import com.dms.document.interaction.model.Translation;
import com.dms.document.interaction.repository.MasterDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MasterDataService {
    private final MasterDataRepository masterDataRepository;

    public List<MasterDataResponse> getAllByType(MasterDataType type) {
        return masterDataRepository.findByType(type).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<MasterDataResponse> getAllActiveByType(MasterDataType type) {
        return masterDataRepository.findByTypeAndIsActiveTrue(type).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public Optional<MasterDataResponse> getByTypeAndCode(MasterDataType type, String code) {
        return masterDataRepository.findByTypeAndCode(type, code)
                .map(this::toResponse);
    }

    public List<MasterDataResponse> searchByText(String searchText) {
        return masterDataRepository.searchByText(searchText).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public MasterDataResponse save(MasterDataRequest request) {
        MasterData masterData = new MasterData();
        updateMasterDataFromRequest(masterData, request);
        masterData.setCreatedAt(LocalDateTime.now());
        masterData.setUpdatedAt(LocalDateTime.now());

        return toResponse(masterDataRepository.save(masterData));
    }

    public MasterDataResponse update(String id, MasterDataRequest request) {
        MasterData masterData = masterDataRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Master data not found"));

        updateMasterDataFromRequest(masterData, request);
        masterData.setUpdatedAt(LocalDateTime.now());

        return toResponse(masterDataRepository.save(masterData));
    }

    public void deleteById(String id) {
        masterDataRepository.deleteById(id);
    }

    private MasterDataResponse toResponse(MasterData masterData) {
        return MasterDataResponse.builder()
                .id(masterData.getId())
                .type(masterData.getType())
                .code(masterData.getCode())
                .translations(toTranslationDTO(masterData.getTranslations()))
                .description(masterData.getDescription())
                .createdAt(masterData.getCreatedAt())
                .updatedAt(masterData.getUpdatedAt())
                .isActive(masterData.isActive())
                .build();
    }

    private void updateMasterDataFromRequest(MasterData masterData, MasterDataRequest request) {
        masterData.setType(request.getType());
        masterData.setCode(request.getCode());
        masterData.setTranslations(toTranslation(request.getTranslations()));
        masterData.setDescription(request.getDescription());
        masterData.setActive(request.isActive());
    }

    private TranslationDTO toTranslationDTO(Translation translation) {
        if (translation == null) return null;
        TranslationDTO dto = new TranslationDTO();
        dto.setEn(translation.getEn());
        dto.setVi(translation.getVi());
        return dto;
    }

    private Translation toTranslation(TranslationDTO dto) {
        if (dto == null) return null;
        Translation translation = new Translation();
        translation.setEn(dto.getEn());
        translation.setVi(dto.getVi());
        return translation;
    }
}