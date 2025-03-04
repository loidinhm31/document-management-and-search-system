package com.dms.document.interaction.service;

import com.dms.document.interaction.dto.MasterDataRequest;
import com.dms.document.interaction.dto.MasterDataResponse;
import com.dms.document.interaction.dto.TranslationDTO;
import com.dms.document.interaction.enums.MasterDataType;
import com.dms.document.interaction.exception.InvalidMasterDataException;
import com.dms.document.interaction.model.MasterData;
import com.dms.document.interaction.model.Translation;
import com.dms.document.interaction.repository.DocumentPreferencesRepository;
import com.dms.document.interaction.repository.DocumentRepository;
import com.dms.document.interaction.repository.MasterDataRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MasterDataService {
    private final MasterDataRepository masterDataRepository;
    private final DocumentRepository documentRepository;
    private final DocumentPreferencesRepository documentPreferencesRepository;

    public List<MasterDataResponse> getAllByType(MasterDataType type, Boolean isActive) {
        if (Objects.nonNull(isActive)) {
            return masterDataRepository.findByTypeAndIsActive(type, isActive).stream()
                    .map(this::toResponse)
                    .collect(Collectors.toList());
        }
        return masterDataRepository.findByType(type).stream()
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
        validateMasterDataRequest(request);

        MasterData masterData = new MasterData();
        updateMasterDataFromRequest(masterData, request);
        masterData.setCreatedAt(Instant.now());
        masterData.setUpdatedAt(Instant.now());

        return toResponse(masterDataRepository.save(masterData));
    }

    public MasterDataResponse update(String id, MasterDataRequest request) {
        validateMasterDataRequest(request);

        MasterData masterData = masterDataRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Master data not found"));

        boolean isFullUpdate = false;
        if (isItemInUse(id)) {
            // Only update safety fields
            masterData.setDescription(request.getDescription());
            masterData.setTranslations(toTranslation(request.getTranslations()));
            masterData.setActive(request.isActive());
        } else {
            isFullUpdate = true;
            updateMasterDataFromRequest(masterData, request);
        }
        masterData.setUpdatedAt(Instant.now());

        return toResponse(masterDataRepository.save(masterData))
                .withFullUpdate(isFullUpdate);
    }

    public void deleteById(String id) {
        // Check if the item is in use
        if (isItemInUse(id)) {
            throw new InvalidMasterDataException("MASTER_DATA_ALREADY_IN_USE");
        }
        masterDataRepository.deleteById(id);
    }

    public List<MasterDataResponse> getAllByTypeAndParentId(MasterDataType type, String parentId, Boolean isActive) {
        if (Objects.nonNull(isActive)) {
            return masterDataRepository.findByTypeAndParentIdAndIsActive(type, parentId, isActive).stream()
                    .map(this::toResponse)
                    .collect(Collectors.toList());
        }
        return masterDataRepository.findByTypeAndParentId(type, parentId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
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
                .parentId(masterData.getParentId())
                .build();
    }

    private void updateMasterDataFromRequest(MasterData masterData, MasterDataRequest request) {
        masterData.setType(request.getType());
        masterData.setCode(request.getCode());
        masterData.setTranslations(toTranslation(request.getTranslations()));
        masterData.setDescription(request.getDescription());
        masterData.setActive(request.isActive());
        masterData.setParentId(request.getParentId());
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

    private void validateMasterDataRequest(MasterDataRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Master data request cannot be null");
        }

        if (request.getType() == null) {
            throw new IllegalArgumentException("Master data type is required");
        }

        if (StringUtils.isEmpty(request.getCode())) {
            throw new IllegalArgumentException("Master data code is required");
        }

        // Validate translations
        if (request.getTranslations() == null ||
            StringUtils.isEmpty(request.getTranslations().getEn()) ||
            StringUtils.isEmpty(request.getTranslations().getVi())) {
            throw new IllegalArgumentException("Both English and Vietnamese translations are required");
        }

        // Validate parentId reference if specified for COURSE_CODE
        if (request.getType() == MasterDataType.COURSE_CODE &&
            StringUtils.isNotEmpty(request.getParentId())) {
            // Check if parent exists and is a MAJOR
            MasterData parent = masterDataRepository.findById(request.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent master data not found"));

            if (parent.getType() != MasterDataType.MAJOR) {
                throw new IllegalArgumentException("Course code parent must be a MAJOR");
            }
        }
    }

    public boolean isItemInUse(String masterDataId) {
        MasterData masterData = masterDataRepository.findById(masterDataId)
                .orElse(null);

        if (masterData == null) {
            return false;
        }

        String code = masterData.getCode();
        MasterDataType type = masterData.getType();

        // Check with master data type
        boolean isMasterDataUsed = switch (type) {
            case MAJOR:
                yield documentRepository.existsByMajorCode(code);

            case COURSE_CODE:
                yield documentRepository.existsByCourseCode(code) ||
                       documentPreferencesRepository.existsByPreferredCourseCode(code);

            case COURSE_LEVEL:
                yield documentRepository.existsByCourseLevelCode(code) ||
                       documentPreferencesRepository.existsByPreferredLevel(code);

            case DOCUMENT_CATEGORY:
                yield documentRepository.existsByCategoryCode(code) ||
                       documentPreferencesRepository.existsByPreferredCategory(code);

            default:
                yield false;
        };

        // Check depend on parent link
        boolean isLinked = CollectionUtils.isNotEmpty(masterDataRepository.findByParentId(masterDataId));
        return isLinked || isMasterDataUsed;
    }
}