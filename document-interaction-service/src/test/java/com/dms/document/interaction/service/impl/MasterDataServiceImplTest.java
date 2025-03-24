package com.dms.document.interaction.service.impl;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MasterDataServiceImplTest {

    @Mock
    private MasterDataRepository masterDataRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentPreferencesRepository documentPreferencesRepository;

    @InjectMocks
    private MasterDataServiceImpl masterDataService;

    private MasterData majorMasterData;
    private MasterData courseCodeMasterData;
    private MasterData courseLevelMasterData;
    private MasterData categoryMasterData;
    private MasterDataRequest validRequest;
    private TranslationDTO validTranslationDTO;

    @BeforeEach
    void setUp() {
        // Setup valid translation
        validTranslationDTO = new TranslationDTO();
        validTranslationDTO.setEn("English");
        validTranslationDTO.setVi("Vietnamese");

        Translation translation = new Translation();
        translation.setEn("English");
        translation.setVi("Vietnamese");

        // Setup master data entities
        majorMasterData = new MasterData();
        majorMasterData.setId("major-id");
        majorMasterData.setType(MasterDataType.MAJOR);
        majorMasterData.setCode("CS");
        majorMasterData.setTranslations(translation);
        majorMasterData.setDescription("Computer Science");
        majorMasterData.setCreatedAt(Instant.now());
        majorMasterData.setUpdatedAt(Instant.now());
        majorMasterData.setActive(true);

        courseCodeMasterData = new MasterData();
        courseCodeMasterData.setId("course-code-id");
        courseCodeMasterData.setType(MasterDataType.COURSE_CODE);
        courseCodeMasterData.setCode("CS101");
        courseCodeMasterData.setTranslations(translation);
        courseCodeMasterData.setDescription("Introduction to Programming");
        courseCodeMasterData.setCreatedAt(Instant.now());
        courseCodeMasterData.setUpdatedAt(Instant.now());
        courseCodeMasterData.setActive(true);
        courseCodeMasterData.setParentId(majorMasterData.getId());

        courseLevelMasterData = new MasterData();
        courseLevelMasterData.setId("course-level-id");
        courseLevelMasterData.setType(MasterDataType.COURSE_LEVEL);
        courseLevelMasterData.setCode("BEGINNER");
        courseLevelMasterData.setTranslations(translation);
        courseLevelMasterData.setDescription("Beginner Level");
        courseLevelMasterData.setCreatedAt(Instant.now());
        courseLevelMasterData.setUpdatedAt(Instant.now());
        courseLevelMasterData.setActive(true);

        categoryMasterData = new MasterData();
        categoryMasterData.setId("category-id");
        categoryMasterData.setType(MasterDataType.DOCUMENT_CATEGORY);
        categoryMasterData.setCode("LECTURE");
        categoryMasterData.setTranslations(translation);
        categoryMasterData.setDescription("Lecture Materials");
        categoryMasterData.setCreatedAt(Instant.now());
        categoryMasterData.setUpdatedAt(Instant.now());
        categoryMasterData.setActive(true);

        // Setup valid request
        validRequest = new MasterDataRequest();
        validRequest.setType(MasterDataType.MAJOR);
        validRequest.setCode("NEW-MAJOR");
        validRequest.setTranslations(validTranslationDTO);
        validRequest.setDescription("New Major Description");
        validRequest.setActive(true);
    }

    @Test
    void getAllByType_ShouldReturnAllMasterDataOfTypeWhenActiveIsNull() {
        // Given
        List<MasterData> masterDataList = Arrays.asList(majorMasterData);
        when(masterDataRepository.findByType(MasterDataType.MAJOR)).thenReturn(masterDataList);

        // When
        List<MasterDataResponse> result = masterDataService.getAllByType(MasterDataType.MAJOR, null);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(majorMasterData.getId(), result.get(0).getId());
        assertEquals(majorMasterData.getCode(), result.get(0).getCode());
        verify(masterDataRepository).findByType(MasterDataType.MAJOR);
        verify(masterDataRepository, never()).findByTypeAndIsActive(any(), anyBoolean());
    }

    @Test
    void getAllByType_ShouldReturnActiveMasterDataWhenActiveIsTrue() {
        // Given
        List<MasterData> masterDataList = Arrays.asList(majorMasterData);
        when(masterDataRepository.findByTypeAndIsActive(MasterDataType.MAJOR, true)).thenReturn(masterDataList);

        // When
        List<MasterDataResponse> result = masterDataService.getAllByType(MasterDataType.MAJOR, true);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(majorMasterData.getId(), result.get(0).getId());
        verify(masterDataRepository).findByTypeAndIsActive(MasterDataType.MAJOR, true);
        verify(masterDataRepository, never()).findByType(any());
    }

    @Test
    void getByTypeAndCode_ShouldReturnMasterDataWhenFound() {
        // Given
        when(masterDataRepository.findByTypeAndCode(MasterDataType.MAJOR, "CS")).thenReturn(Optional.of(majorMasterData));

        // When
        Optional<MasterDataResponse> result = masterDataService.getByTypeAndCode(MasterDataType.MAJOR, "CS");

        // Then
        assertTrue(result.isPresent());
        assertEquals(majorMasterData.getId(), result.get().getId());
        assertEquals(majorMasterData.getCode(), result.get().getCode());
        verify(masterDataRepository).findByTypeAndCode(MasterDataType.MAJOR, "CS");
    }

    @Test
    void getByTypeAndCode_ShouldReturnEmptyOptionalWhenNotFound() {
        // Given
        when(masterDataRepository.findByTypeAndCode(MasterDataType.MAJOR, "NOT-EXIST")).thenReturn(Optional.empty());

        // When
        Optional<MasterDataResponse> result = masterDataService.getByTypeAndCode(MasterDataType.MAJOR, "NOT-EXIST");

        // Then
        assertFalse(result.isPresent());
        verify(masterDataRepository).findByTypeAndCode(MasterDataType.MAJOR, "NOT-EXIST");
    }

    @Test
    void searchByText_ShouldReturnMatchingMasterData() {
        // Given
        List<MasterData> masterDataList = Arrays.asList(majorMasterData, courseCodeMasterData);
        when(masterDataRepository.searchByText("sci")).thenReturn(masterDataList);

        // When
        List<MasterDataResponse> result = masterDataService.searchByText("sci");

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(masterDataRepository).searchByText("sci");
    }

    @Test
    void save_ShouldSaveMasterDataSuccessfully() {
        // Given
        when(masterDataRepository.save(any(MasterData.class))).thenAnswer(invocation -> {
            MasterData saved = invocation.getArgument(0);
            saved.setId("new-id");
            return saved;
        });

        // When
        MasterDataResponse result = masterDataService.save(validRequest);

        // Then
        assertNotNull(result);
        assertEquals("new-id", result.getId());
        assertEquals(validRequest.getCode(), result.getCode());
        assertEquals(validRequest.getType(), result.getType());

        ArgumentCaptor<MasterData> masterDataCaptor = ArgumentCaptor.forClass(MasterData.class);
        verify(masterDataRepository).save(masterDataCaptor.capture());

        MasterData savedData = masterDataCaptor.getValue();
        assertNotNull(savedData.getCreatedAt());
        assertNotNull(savedData.getUpdatedAt());
        assertEquals(validRequest.getDescription(), savedData.getDescription());
        assertEquals(validRequest.isActive(), savedData.isActive());
    }

    @Test
    void save_ShouldThrowExceptionWhenRequestIsInvalid() {
        // Given
        MasterDataRequest invalidRequest = new MasterDataRequest();
        invalidRequest.setType(MasterDataType.MAJOR);
        // Missing code and translations

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> masterDataService.save(invalidRequest));
        verify(masterDataRepository, never()).save(any());
    }

    @Test
    void save_ShouldValidateParentIdForCourseCode() {
        // Given
        MasterDataRequest courseCodeRequest = new MasterDataRequest();
        courseCodeRequest.setType(MasterDataType.COURSE_CODE);
        courseCodeRequest.setCode("CS202");
        courseCodeRequest.setTranslations(validTranslationDTO);
        courseCodeRequest.setDescription("Advanced Programming");
        courseCodeRequest.setActive(true);
        courseCodeRequest.setParentId("invalid-parent-id");

        when(masterDataRepository.findById("invalid-parent-id")).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> masterDataService.save(courseCodeRequest));
        verify(masterDataRepository, never()).save(any());
    }

    @Test
    void save_ShouldValidateParentTypeForCourseCode() {
        // Given
        MasterDataRequest courseCodeRequest = new MasterDataRequest();
        courseCodeRequest.setType(MasterDataType.COURSE_CODE);
        courseCodeRequest.setCode("CS202");
        courseCodeRequest.setTranslations(validTranslationDTO);
        courseCodeRequest.setDescription("Advanced Programming");
        courseCodeRequest.setActive(true);
        courseCodeRequest.setParentId("category-id"); // Not a MAJOR

        when(masterDataRepository.findById("category-id")).thenReturn(Optional.of(categoryMasterData));

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> masterDataService.save(courseCodeRequest));
        verify(masterDataRepository, never()).save(any());
    }

    @Test
    void update_ShouldUpdateFullyWhenNotInUse() {
        // Given
        when(masterDataRepository.findById("major-id")).thenReturn(Optional.of(majorMasterData));
        // Set up the repository mocks to make isItemInUse return false
        when(documentRepository.existsByMajorCode(anyString())).thenReturn(false);
        when(masterDataRepository.findByParentId(anyString())).thenReturn(Collections.emptyList());
        when(masterDataRepository.save(any(MasterData.class))).thenAnswer(i -> i.getArgument(0));

        MasterDataRequest updateRequest = new MasterDataRequest();
        updateRequest.setType(MasterDataType.MAJOR);
        updateRequest.setCode("UPDATED-CS");
        updateRequest.setTranslations(validTranslationDTO);
        updateRequest.setDescription("Updated Computer Science");
        updateRequest.setActive(true);

        // When
        MasterDataResponse result = masterDataService.update("major-id", updateRequest);

        // Then
        assertNotNull(result);
        assertTrue(result.isFullUpdate());
        assertEquals("UPDATED-CS", result.getCode());
        assertEquals("Updated Computer Science", result.getDescription());

        ArgumentCaptor<MasterData> masterDataCaptor = ArgumentCaptor.forClass(MasterData.class);
        verify(masterDataRepository).save(masterDataCaptor.capture());

        MasterData updatedData = masterDataCaptor.getValue();
        assertEquals(updateRequest.getCode(), updatedData.getCode());
        assertEquals(updateRequest.getDescription(), updatedData.getDescription());
        assertTrue(updatedData.isActive());
        assertNotNull(updatedData.getUpdatedAt());
    }

    @Test
    void update_ShouldUpdatePartiallyWhenInUse() {
        // Given
        when(masterDataRepository.findById("major-id")).thenReturn(Optional.of(majorMasterData));
        // Set up the repository mocks to make isItemInUse return true
        when(documentRepository.existsByMajorCode(anyString())).thenReturn(true);
        when(masterDataRepository.save(any(MasterData.class))).thenAnswer(i -> i.getArgument(0));

        MasterDataRequest updateRequest = new MasterDataRequest();
        updateRequest.setType(MasterDataType.MAJOR);
        updateRequest.setCode("UPDATED-CS");
        updateRequest.setTranslations(validTranslationDTO);
        updateRequest.setDescription("Updated Computer Science");
        updateRequest.setActive(false);

        // When
        MasterDataResponse result = masterDataService.update("major-id", updateRequest);

        // Then
        assertNotNull(result);
        assertFalse(result.isFullUpdate());
        assertEquals("CS", result.getCode()); // Code should not be updated
        assertEquals("Updated Computer Science", result.getDescription()); // Description should be updated
        assertFalse(result.isActive()); // Active status should be updated

        ArgumentCaptor<MasterData> masterDataCaptor = ArgumentCaptor.forClass(MasterData.class);
        verify(masterDataRepository).save(masterDataCaptor.capture());

        MasterData updatedData = masterDataCaptor.getValue();
        assertEquals("CS", updatedData.getCode()); // Original code preserved
        assertEquals(updateRequest.getDescription(), updatedData.getDescription());
        assertFalse(updatedData.isActive());
    }

    @Test
    void update_ShouldThrowExceptionWhenEntityNotFound() {
        // Given
        when(masterDataRepository.findById("non-existent-id")).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> masterDataService.update("non-existent-id", validRequest));
        verify(masterDataRepository, never()).save(any());
    }

    @Test
    void delete_ShouldDeleteSuccessfullyWhenNotInUse() {
        // Given
        String id = "deletable-id";
        // Set up repository mocks to make isItemInUse return false
        when(masterDataRepository.findById(id)).thenReturn(Optional.of(majorMasterData));
        when(documentRepository.existsByMajorCode(anyString())).thenReturn(false);
        when(masterDataRepository.findByParentId(id)).thenReturn(Collections.emptyList());

        // When
        masterDataService.deleteById(id);

        // Then
        verify(masterDataRepository).deleteById(id);
    }

    @Test
    void delete_ShouldThrowExceptionWhenInUse() {
        // Given
        String id = "in-use-id";
        // Set up repository mocks to make isItemInUse return true
        when(masterDataRepository.findById(id)).thenReturn(Optional.of(majorMasterData));
        when(documentRepository.existsByMajorCode(anyString())).thenReturn(true);

        // When & Then
        assertThrows(InvalidMasterDataException.class, () -> masterDataService.deleteById(id));
        verify(masterDataRepository, never()).deleteById(anyString());
    }

    @Test
    void getAllByTypeAndParentId_ShouldReturnAllWhenActiveIsNull() {
        // Given
        List<MasterData> masterDataList = Collections.singletonList(courseCodeMasterData);
        when(masterDataRepository.findByTypeAndParentId(MasterDataType.COURSE_CODE, "major-id"))
                .thenReturn(masterDataList);

        // When
        List<MasterDataResponse> result = masterDataService.getAllByTypeAndParentId(
                MasterDataType.COURSE_CODE, "major-id", null);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(courseCodeMasterData.getId(), result.get(0).getId());
        verify(masterDataRepository).findByTypeAndParentId(MasterDataType.COURSE_CODE, "major-id");
        verify(masterDataRepository, never()).findByTypeAndParentIdAndIsActive(any(), anyString(), anyBoolean());
    }

    @Test
    void getAllByTypeAndParentId_ShouldFilterByActiveWhenActiveIsProvided() {
        // Given
        List<MasterData> masterDataList = Collections.singletonList(courseCodeMasterData);
        when(masterDataRepository.findByTypeAndParentIdAndIsActive(MasterDataType.COURSE_CODE, "major-id", true))
                .thenReturn(masterDataList);

        // When
        List<MasterDataResponse> result = masterDataService.getAllByTypeAndParentId(
                MasterDataType.COURSE_CODE, "major-id", true);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(courseCodeMasterData.getId(), result.get(0).getId());
        verify(masterDataRepository).findByTypeAndParentIdAndIsActive(MasterDataType.COURSE_CODE, "major-id", true);
        verify(masterDataRepository, never()).findByTypeAndParentId(any(), anyString());
    }

    @Test
    void isItemInUse_ShouldReturnFalseWhenMasterDataNotFound() {
        // Given
        when(masterDataRepository.findById("non-existent-id")).thenReturn(Optional.empty());

        // When
        boolean result = masterDataService.isItemInUse("non-existent-id");

        // Then
        assertFalse(result);
        verify(documentRepository, never()).existsByMajorCode(anyString());
        verify(documentRepository, never()).existsByCourseCode(anyString());
        verify(documentRepository, never()).existsByCourseLevelCode(anyString());
        verify(documentRepository, never()).existsByCategoryCode(anyString());
    }

    @Test
    void isItemInUse_ShouldCheckMajorUsage() {
        // Given
        when(masterDataRepository.findById("major-id")).thenReturn(Optional.of(majorMasterData));
        when(documentRepository.existsByMajorCode("CS")).thenReturn(true);

        // When
        boolean result = masterDataService.isItemInUse("major-id");

        // Then
        assertTrue(result);
        verify(documentRepository).existsByMajorCode("CS");
    }

    @Test
    void isItemInUse_ShouldCheckCourseCodeUsage() {
        // Given
        when(masterDataRepository.findById("course-code-id")).thenReturn(Optional.of(courseCodeMasterData));
        when(documentRepository.existsByCourseCode("CS101")).thenReturn(false);
        when(documentPreferencesRepository.existsByPreferredCourseCode("CS101")).thenReturn(true);

        // When
        boolean result = masterDataService.isItemInUse("course-code-id");

        // Then
        assertTrue(result);
        verify(documentRepository).existsByCourseCode("CS101");
        verify(documentPreferencesRepository).existsByPreferredCourseCode("CS101");
    }

    @Test
    void isItemInUse_ShouldCheckCourseLevelUsage() {
        // Given
        when(masterDataRepository.findById("course-level-id")).thenReturn(Optional.of(courseLevelMasterData));
        when(documentRepository.existsByCourseLevelCode("BEGINNER")).thenReturn(false);
        when(documentPreferencesRepository.existsByPreferredLevel("BEGINNER")).thenReturn(true);

        // When
        boolean result = masterDataService.isItemInUse("course-level-id");

        // Then
        assertTrue(result);
        verify(documentRepository).existsByCourseLevelCode("BEGINNER");
        verify(documentPreferencesRepository).existsByPreferredLevel("BEGINNER");
    }

    @Test
    void isItemInUse_ShouldCheckCategoryUsage() {
        // Given
        when(masterDataRepository.findById("category-id")).thenReturn(Optional.of(categoryMasterData));
        when(documentRepository.existsByCategoryCode("LECTURE")).thenReturn(false);
        when(documentPreferencesRepository.existsByPreferredCategory("LECTURE")).thenReturn(false);

        // When
        boolean result = masterDataService.isItemInUse("category-id");

        // Then
        assertFalse(result);
        verify(documentRepository).existsByCategoryCode("LECTURE");
        verify(documentPreferencesRepository).existsByPreferredCategory("LECTURE");
    }

    @Test
    void isItemInUse_ShouldCheckForChildren() {
        // Given
        when(masterDataRepository.findById("major-id")).thenReturn(Optional.of(majorMasterData));
        when(documentRepository.existsByMajorCode("CS")).thenReturn(false);
        when(masterDataRepository.findByParentId("major-id")).thenReturn(Arrays.asList(courseCodeMasterData));

        // When
        boolean result = masterDataService.isItemInUse("major-id");

        // Then
        assertTrue(result);
        verify(documentRepository).existsByMajorCode("CS");
        verify(masterDataRepository).findByParentId("major-id");
    }
}