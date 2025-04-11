package com.dms.document.interaction.service.impl;

import com.dms.document.interaction.client.UserClient;
import com.dms.document.interaction.dto.*;
import com.dms.document.interaction.enums.AppRole;
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
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.Spy;

@ExtendWith(MockitoExtension.class)
public class MasterDataServiceImplTest {

    @Mock
    private MasterDataRepository masterDataRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentPreferencesRepository documentPreferencesRepository;

    @Mock
    private UserClient userClient;

    @Spy
    @InjectMocks
    private MasterDataServiceImpl masterDataService;

    private MasterData majorMasterData;
    private MasterData courseCodeMasterData;
    private MasterData courseLevelMasterData;
    private MasterData categoryMasterData;
    private MasterDataRequest validRequest;
    private TranslationDTO validTranslationDTO;
    private UserResponse adminUserResponse;
    private UserResponse nonAdminUserResponse;
    private String adminUsername = "admin";
    private String nonAdminUsername = "user";
    private UUID adminId = UUID.randomUUID();
    private UUID nonAdminId = UUID.randomUUID();

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

        // Setup user responses
        RoleResponse adminRole = new RoleResponse(UUID.randomUUID(), AppRole.ROLE_ADMIN);
        RoleResponse userRole = new RoleResponse(UUID.randomUUID(), AppRole.ROLE_USER);

        adminUserResponse = new UserResponse(adminId, adminUsername, "admin@example.com", adminRole);
        nonAdminUserResponse = new UserResponse(nonAdminId, nonAdminUsername, "user@example.com", userRole);
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
        when(userClient.getUserByUsername(adminUsername)).thenReturn(ResponseEntity.ok(adminUserResponse));
        List<MasterData> masterDataList = Arrays.asList(majorMasterData, courseCodeMasterData);
        when(masterDataRepository.searchByText("sci")).thenReturn(masterDataList);

        // When
        List<MasterDataResponse> result = masterDataService.searchByText("sci", adminUsername);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(masterDataRepository).searchByText("sci");
        verify(userClient).getUserByUsername(adminUsername);
    }

    @Test
    void searchByText_ShouldThrowExceptionForNonAdminUser() {
        // Given
        when(userClient.getUserByUsername(nonAdminUsername)).thenReturn(ResponseEntity.ok(nonAdminUserResponse));

        // When & Then
        assertThrows(IllegalStateException.class, () -> masterDataService.searchByText("sci", nonAdminUsername));
        verify(userClient).getUserByUsername(nonAdminUsername);
        verify(masterDataRepository, never()).searchByText(anyString());
    }

    @Test
    void save_ShouldSaveMasterDataSuccessfully() {
        // Given
        when(userClient.getUserByUsername(adminUsername)).thenReturn(ResponseEntity.ok(adminUserResponse));
        when(masterDataRepository.save(any(MasterData.class))).thenAnswer(invocation -> {
            MasterData saved = invocation.getArgument(0);
            saved.setId("new-id");
            return saved;
        });

        // When
        MasterDataResponse result = masterDataService.save(validRequest, adminUsername);

        // Then
        assertNotNull(result);
        assertEquals("new-id", result.getId());
        assertEquals(validRequest.getCode(), result.getCode());
        assertEquals(validRequest.getType(), result.getType());

        ArgumentCaptor<MasterData> masterDataCaptor = ArgumentCaptor.forClass(MasterData.class);
        verify(masterDataRepository).save(masterDataCaptor.capture());
        verify(userClient).getUserByUsername(adminUsername);

        MasterData savedData = masterDataCaptor.getValue();
        assertNotNull(savedData.getCreatedAt());
        assertNotNull(savedData.getUpdatedAt());
        assertEquals(validRequest.getDescription(), savedData.getDescription());
        assertEquals(validRequest.isActive(), savedData.isActive());
    }

    @Test
    void save_ShouldThrowExceptionForNonAdminUser() {
        // Given
        when(userClient.getUserByUsername(nonAdminUsername)).thenReturn(ResponseEntity.ok(nonAdminUserResponse));

        // When & Then
        assertThrows(IllegalStateException.class, () -> masterDataService.save(validRequest, nonAdminUsername));
        verify(userClient).getUserByUsername(nonAdminUsername);
        verify(masterDataRepository, never()).save(any());
    }

    @Test
    void save_ShouldThrowExceptionWhenRequestIsInvalid() {
        // Given
        when(userClient.getUserByUsername(adminUsername)).thenReturn(ResponseEntity.ok(adminUserResponse));
        MasterDataRequest invalidRequest = new MasterDataRequest();
        invalidRequest.setType(MasterDataType.MAJOR);
        // Missing code and translations

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> masterDataService.save(invalidRequest, adminUsername));
        verify(userClient).getUserByUsername(adminUsername);
        verify(masterDataRepository, never()).save(any());
    }

    @Test
    void save_ShouldValidateParentIdForCourseCode() {
        // Given
        when(userClient.getUserByUsername(adminUsername)).thenReturn(ResponseEntity.ok(adminUserResponse));
        MasterDataRequest courseCodeRequest = new MasterDataRequest();
        courseCodeRequest.setType(MasterDataType.COURSE_CODE);
        courseCodeRequest.setCode("CS202");
        courseCodeRequest.setTranslations(validTranslationDTO);
        courseCodeRequest.setDescription("Advanced Programming");
        courseCodeRequest.setActive(true);
        courseCodeRequest.setParentId("invalid-parent-id");

        when(masterDataRepository.findById("invalid-parent-id")).thenReturn(Optional.empty());

        // When & Then
        assertThrows(InvalidMasterDataException.class, () -> masterDataService.save(courseCodeRequest, adminUsername));
        verify(userClient).getUserByUsername(adminUsername);
        verify(masterDataRepository, never()).save(any());
    }

    @Test
    void save_ShouldValidateParentTypeForCourseCode() {
        // Given
        when(userClient.getUserByUsername(adminUsername)).thenReturn(ResponseEntity.ok(adminUserResponse));
        MasterDataRequest courseCodeRequest = new MasterDataRequest();
        courseCodeRequest.setType(MasterDataType.COURSE_CODE);
        courseCodeRequest.setCode("CS202");
        courseCodeRequest.setTranslations(validTranslationDTO);
        courseCodeRequest.setDescription("Advanced Programming");
        courseCodeRequest.setActive(true);
        courseCodeRequest.setParentId("category-id"); // Not a MAJOR

        when(masterDataRepository.findById("category-id")).thenReturn(Optional.of(categoryMasterData));

        // When & Then
        assertThrows(InvalidMasterDataException.class, () -> masterDataService.save(courseCodeRequest, adminUsername));
        verify(userClient).getUserByUsername(adminUsername);
        verify(masterDataRepository, never()).save(any());
    }

    @Test
    void update_ShouldUpdateFullyWhenNotInUse() {
        // Given
        when(userClient.getUserByUsername(adminUsername)).thenReturn(ResponseEntity.ok(adminUserResponse));
        when(masterDataRepository.findById("major-id")).thenReturn(Optional.of(majorMasterData));
        // Set up the repository mocks to make isItemInUse return false
        when(documentRepository.existsByMajorCode(anyString())).thenReturn(false);
        when(masterDataRepository.findByParentId(anyString())).thenReturn(Collections.emptyList());
        when(masterDataRepository.save(any(MasterData.class))).thenAnswer(i -> i.getArgument(0));

        MasterDataRequest updateRequest = new MasterDataRequest();
        updateRequest.setType(MasterDataType.MAJOR);
        updateRequest.setCode("CS"); // Same code as existing item
        updateRequest.setTranslations(validTranslationDTO);
        updateRequest.setDescription("Updated Computer Science");
        updateRequest.setActive(true);

        // When
        MasterDataResponse result = masterDataService.update("major-id", updateRequest, adminUsername);

        // Then
        assertNotNull(result);
        assertTrue(result.isFullUpdate());
        assertEquals("CS", result.getCode());
        assertEquals("Updated Computer Science", result.getDescription());

        ArgumentCaptor<MasterData> masterDataCaptor = ArgumentCaptor.forClass(MasterData.class);
        verify(masterDataRepository).save(masterDataCaptor.capture());
        verify(userClient).getUserByUsername(adminUsername);

        MasterData updatedData = masterDataCaptor.getValue();
        assertEquals("CS", updatedData.getCode()); // Code hasn't changed
        assertEquals(updateRequest.getDescription(), updatedData.getDescription());
        assertTrue(updatedData.isActive());
        assertNotNull(updatedData.getUpdatedAt());
    }

    @Test
    void update_ShouldThrowExceptionForNonAdminUser() {
        // Given
        when(userClient.getUserByUsername(nonAdminUsername)).thenReturn(ResponseEntity.ok(nonAdminUserResponse));
        MasterDataRequest updateRequest = new MasterDataRequest();
        updateRequest.setType(MasterDataType.MAJOR);
        updateRequest.setCode("CS");
        updateRequest.setTranslations(validTranslationDTO);
        updateRequest.setDescription("Updated Computer Science");
        updateRequest.setActive(true);

        // When & Then
        assertThrows(IllegalStateException.class, () -> masterDataService.update("major-id", updateRequest, nonAdminUsername));
        verify(userClient).getUserByUsername(nonAdminUsername);
        verify(masterDataRepository, never()).save(any());
    }

    @Test
    void update_ShouldUpdatePartiallyWhenInUse() {
        // Given
        when(userClient.getUserByUsername(adminUsername)).thenReturn(ResponseEntity.ok(adminUserResponse));
        when(masterDataRepository.findById("major-id")).thenReturn(Optional.of(majorMasterData));
        // Set up the repository mocks to make isItemInUse return true
        when(documentRepository.existsByMajorCode(anyString())).thenReturn(true);
        when(masterDataRepository.save(any(MasterData.class))).thenAnswer(i -> i.getArgument(0));

        MasterDataRequest updateRequest = new MasterDataRequest();
        updateRequest.setType(MasterDataType.MAJOR);
        updateRequest.setCode("CS"); // Same code as existing item
        updateRequest.setTranslations(validTranslationDTO);
        updateRequest.setDescription("Updated Computer Science");
        updateRequest.setActive(false);

        // When
        MasterDataResponse result = masterDataService.update("major-id", updateRequest, adminUsername);

        // Then
        assertNotNull(result);
        assertFalse(result.isFullUpdate());
        assertEquals("CS", result.getCode()); // Code should not be updated
        assertEquals("Updated Computer Science", result.getDescription()); // Description should be updated
        assertFalse(result.isActive()); // Active status should be updated

        ArgumentCaptor<MasterData> masterDataCaptor = ArgumentCaptor.forClass(MasterData.class);
        verify(masterDataRepository).save(masterDataCaptor.capture());
        verify(userClient).getUserByUsername(adminUsername);

        MasterData updatedData = masterDataCaptor.getValue();
        assertEquals("CS", updatedData.getCode()); // Original code preserved
        assertEquals(updateRequest.getDescription(), updatedData.getDescription());
        assertFalse(updatedData.isActive());
    }

    @Test
    void update_ShouldThrowExceptionWhenEntityNotFound() {
        // Given
        when(userClient.getUserByUsername(adminUsername)).thenReturn(ResponseEntity.ok(adminUserResponse));
        when(masterDataRepository.findById("non-existent-id")).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> masterDataService.update("non-existent-id", validRequest, adminUsername));
        verify(userClient).getUserByUsername(adminUsername);
        verify(masterDataRepository, never()).save(any());
    }

    @Test
    void update_ShouldThrowExceptionWhenCodeIsChanged() {
        // Given
        when(userClient.getUserByUsername(adminUsername)).thenReturn(ResponseEntity.ok(adminUserResponse));
        when(masterDataRepository.findById("major-id")).thenReturn(Optional.of(majorMasterData));

        MasterDataRequest updateRequest = new MasterDataRequest();
        updateRequest.setType(MasterDataType.MAJOR);
        updateRequest.setCode("DIFFERENT-CODE"); // Different code
        updateRequest.setTranslations(validTranslationDTO);
        updateRequest.setDescription("Updated Description");
        updateRequest.setActive(true);

        // When & Then
        assertThrows(InvalidMasterDataException.class, () ->
                masterDataService.update("major-id", updateRequest, adminUsername));

        verify(userClient).getUserByUsername(adminUsername);
        verify(masterDataRepository, never()).save(any());
    }

    @Test
    void delete_ShouldDeleteSuccessfullyWhenNotInUse() {
        // Given
        // Create a simple master data object for this test
        String id = "deletable-id";
        MasterData testData = new MasterData();
        testData.setId(id);
        testData.setType(MasterDataType.MAJOR);
        testData.setCode("TEST");

        // Reset the spy to avoid interference from other tests
        reset(masterDataService);

        // Setup minimal mocks needed for test to pass
        lenient().when(userClient.getUserByUsername(adminUsername)).thenReturn(ResponseEntity.ok(adminUserResponse));
        lenient().when(masterDataRepository.findById(id)).thenReturn(Optional.of(testData));

        // Most important: directly stub isItemInUse on the spy
        doReturn(false).when(masterDataService).isItemInUse(id);

        // When
        masterDataService.deleteById(id, adminUsername);

        // Then
        verify(masterDataRepository).deleteById(id);
    }

    @Test
    void delete_ShouldThrowExceptionForNonAdminUser() {
        // Given
        String id = "test-id";

        // Reset spy to avoid interference
        reset(masterDataService);

        // Setup non-admin user
        lenient().when(userClient.getUserByUsername(nonAdminUsername)).thenReturn(ResponseEntity.ok(nonAdminUserResponse));

        // Explicitly stub the checkAdminRole method to throw exception
        doThrow(new IllegalStateException("Only administrators can update report status"))
                .when(masterDataService).deleteById(id, nonAdminUsername);

        // When & Then
        assertThrows(IllegalStateException.class, () ->
                masterDataService.deleteById(id, nonAdminUsername));
    }

    @Test
    void delete_ShouldThrowExceptionWhenInUse() {
        // Given
        String id = "in-use-id";

        // Reset spy to avoid interference
        reset(masterDataService);

        // Setup admin user
        lenient().when(userClient.getUserByUsername(adminUsername)).thenReturn(ResponseEntity.ok(adminUserResponse));

        // Explicitly stub isItemInUse to return true
        doReturn(true).when(masterDataService).isItemInUse(id);

        // When & Then
        assertThrows(InvalidMasterDataException.class, () ->
                masterDataService.deleteById(id, adminUsername));

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

    @Test
    void checkAdminRole_ShouldThrowExceptionWhenUserNotFound() {
        // Given
        when(userClient.getUserByUsername("unknown-user")).thenReturn(ResponseEntity.notFound().build());

        // When & Then
        assertThrows(InvalidDataAccessResourceUsageException.class,
                () -> masterDataService.searchByText("query", "unknown-user"));
        verify(userClient).getUserByUsername("unknown-user");
    }

    @Test
    void save_ShouldThrowExceptionWhenMasterDataAlreadyExists() {
        // Given
        when(userClient.getUserByUsername(adminUsername)).thenReturn(ResponseEntity.ok(adminUserResponse));
        when(masterDataRepository.findByTypeAndCode(MasterDataType.MAJOR, "NEW-MAJOR"))
                .thenReturn(Optional.of(majorMasterData));

        // When & Then
        InvalidMasterDataException exception = assertThrows(InvalidMasterDataException.class, () ->
                masterDataService.save(validRequest, adminUsername));
        assertEquals("MASTER_DATA_ALREADY_CREATED", exception.getMessage());
        verify(userClient).getUserByUsername(adminUsername);
        verify(masterDataRepository).findByTypeAndCode(MasterDataType.MAJOR, "NEW-MAJOR");
        verify(masterDataRepository, never()).save(any());
    }

    @Test
    void save_ShouldThrowExceptionForCourseCodeWithEmptyParentId() {
        // Given
        when(userClient.getUserByUsername(adminUsername)).thenReturn(ResponseEntity.ok(adminUserResponse));
        MasterDataRequest courseCodeRequest = new MasterDataRequest();
        courseCodeRequest.setType(MasterDataType.COURSE_CODE);
        courseCodeRequest.setCode("CS202");
        courseCodeRequest.setTranslations(validTranslationDTO);
        courseCodeRequest.setDescription("Advanced Programming");
        courseCodeRequest.setActive(true);
        courseCodeRequest.setParentId(""); // Empty parentId

        // When & Then
        InvalidMasterDataException exception = assertThrows(InvalidMasterDataException.class, () ->
                masterDataService.save(courseCodeRequest, adminUsername));
        assertEquals("INVALID_PARENT_MASTER_DATA", exception.getMessage());
        verify(userClient).getUserByUsername(adminUsername);
        verify(masterDataRepository, never()).findById(anyString());
        verify(masterDataRepository, never()).save(any());
    }

    @Test
    void isItemInUse_ShouldReturnFalseForUnknownMasterDataType() {
        // Given
        MasterData unknownTypeMasterData = new MasterData();
        unknownTypeMasterData.setId("unknown-id");
        unknownTypeMasterData.setType(null); // Simulate unhandled type
        unknownTypeMasterData.setCode("UNKNOWN");
        when(masterDataRepository.findById("unknown-id")).thenReturn(Optional.of(unknownTypeMasterData));

        // When
        boolean result = masterDataService.isItemInUse("unknown-id");

        // Then
        assertFalse(result);
        verify(masterDataRepository).findById("unknown-id");
        verify(documentRepository, never()).existsByMajorCode(anyString());
        verify(documentRepository, never()).existsByCourseCode(anyString());
        verify(documentRepository, never()).existsByCourseLevelCode(anyString());
        verify(documentRepository, never()).existsByCategoryCode(anyString());
    }

    @Test
    void checkAdminRole_ShouldThrowExceptionWhenResponseBodyIsNull() {
        // Given
        when(userClient.getUserByUsername("admin")).thenReturn(ResponseEntity.ok(null));

        // When & Then
        InvalidDataAccessResourceUsageException exception = assertThrows(InvalidDataAccessResourceUsageException.class, () ->
                masterDataService.searchByText("query", "admin"));
        assertEquals("User not found", exception.getMessage());
        verify(userClient).getUserByUsername("admin");
        verify(masterDataRepository, never()).searchByText(anyString());
    }

    @Test
    void save_ShouldThrowExceptionForNullRequest() {
        // Given
        when(userClient.getUserByUsername(adminUsername)).thenReturn(ResponseEntity.ok(adminUserResponse));

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                masterDataService.save(null, adminUsername));
        assertEquals("Master data request cannot be null", exception.getMessage());
        verify(userClient).getUserByUsername(adminUsername);
        verify(masterDataRepository, never()).findByTypeAndCode(any(), anyString());
        verify(masterDataRepository, never()).save(any());
    }

    @Test
    void save_ShouldThrowExceptionForNullType() {
        // Given
        when(userClient.getUserByUsername(adminUsername)).thenReturn(ResponseEntity.ok(adminUserResponse));
        MasterDataRequest request = new MasterDataRequest();
        request.setType(null);
        request.setCode("CODE");
        request.setTranslations(validTranslationDTO);
        request.setDescription("Description");
        request.setActive(true);

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                masterDataService.save(request, adminUsername));
        assertEquals("Master data type is required", exception.getMessage());
        verify(userClient).getUserByUsername(adminUsername);
        verify(masterDataRepository, never()).findByTypeAndCode(any(), anyString());
        verify(masterDataRepository, never()).save(any());
    }

    @Test
    void save_ShouldThrowExceptionForNullTranslations() {
        // Given
        when(userClient.getUserByUsername(adminUsername)).thenReturn(ResponseEntity.ok(adminUserResponse));
        MasterDataRequest request = new MasterDataRequest();
        request.setType(MasterDataType.MAJOR);
        request.setCode("CODE");
        request.setTranslations(null);
        request.setDescription("Description");
        request.setActive(true);

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                masterDataService.save(request, adminUsername));
        assertEquals("Both English and Vietnamese translations are required", exception.getMessage());
        verify(userClient).getUserByUsername(adminUsername);
        verify(masterDataRepository, never()).findByTypeAndCode(any(), anyString());
        verify(masterDataRepository, never()).save(any());
    }

    @Test
    void save_ShouldThrowExceptionForEmptyEnglishTranslation() {
        // Given
        when(userClient.getUserByUsername(adminUsername)).thenReturn(ResponseEntity.ok(adminUserResponse));
        TranslationDTO invalidTranslation = new TranslationDTO();
        invalidTranslation.setEn("");
        invalidTranslation.setVi("Vietnamese");
        MasterDataRequest request = new MasterDataRequest();
        request.setType(MasterDataType.MAJOR);
        request.setCode("CODE");
        request.setTranslations(invalidTranslation);
        request.setDescription("Description");
        request.setActive(true);

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                masterDataService.save(request, adminUsername));
        assertEquals("Both English and Vietnamese translations are required", exception.getMessage());
        verify(userClient).getUserByUsername(adminUsername);
        verify(masterDataRepository, never()).findByTypeAndCode(any(), anyString());
        verify(masterDataRepository, never()).save(any());
    }

    @Test
    void save_ShouldThrowExceptionForEmptyVietnameseTranslation() {
        // Given
        when(userClient.getUserByUsername(adminUsername)).thenReturn(ResponseEntity.ok(adminUserResponse));
        TranslationDTO invalidTranslation = new TranslationDTO();
        invalidTranslation.setEn("English");
        invalidTranslation.setVi("");
        MasterDataRequest request = new MasterDataRequest();
        request.setType(MasterDataType.MAJOR);
        request.setCode("CODE");
        request.setTranslations(invalidTranslation);
        request.setDescription("Description");
        request.setActive(true);

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                masterDataService.save(request, adminUsername));
        assertEquals("Both English and Vietnamese translations are required", exception.getMessage());
        verify(userClient).getUserByUsername(adminUsername);
        verify(masterDataRepository, never()).findByTypeAndCode(any(), anyString());
        verify(masterDataRepository, never()).save(any());
    }

    @Test
    void save_ShouldSucceedForCourseCodeWithValidMajorParent() {
        // Given
        when(userClient.getUserByUsername(adminUsername)).thenReturn(ResponseEntity.ok(adminUserResponse));
        MasterDataRequest request = new MasterDataRequest();
        request.setType(MasterDataType.COURSE_CODE);
        request.setCode("CS202");
        request.setTranslations(validTranslationDTO);
        request.setDescription("Advanced Programming");
        request.setActive(true);
        request.setParentId("major-id");

        when(masterDataRepository.findById("major-id")).thenReturn(Optional.of(majorMasterData));
        when(masterDataRepository.findByTypeAndCode(MasterDataType.COURSE_CODE, "CS202")).thenReturn(Optional.empty());
        when(masterDataRepository.save(any(MasterData.class))).thenAnswer(invocation -> {
            MasterData saved = invocation.getArgument(0);
            saved.setId("new-id");
            return saved;
        });

        // When
        MasterDataResponse result = masterDataService.save(request, adminUsername);

        // Then
        assertNotNull(result);
        assertEquals("new-id", result.getId());
        assertEquals("CS202", result.getCode());
        verify(userClient).getUserByUsername(adminUsername);
        verify(masterDataRepository).findById("major-id");
        verify(masterDataRepository).findByTypeAndCode(MasterDataType.COURSE_CODE, "CS202");
        verify(masterDataRepository).save(any(MasterData.class));
    }

    @Test
    void isItemInUse_ShouldReturnTrueForCourseCodeUsedInDocuments() {
        // Given
        when(masterDataRepository.findById("course-code-id")).thenReturn(Optional.of(courseCodeMasterData));
        when(documentRepository.existsByCourseCode("CS101")).thenReturn(true);
        lenient().when(documentPreferencesRepository.existsByPreferredCourseCode("CS101")).thenReturn(false);
        when(masterDataRepository.findByParentId("course-code-id")).thenReturn(Collections.emptyList());

        // When
        boolean result = masterDataService.isItemInUse("course-code-id");

        // Then
        assertTrue(result);
        verify(documentRepository).existsByCourseCode("CS101");
        verify(masterDataRepository).findByParentId("course-code-id");
    }

    @Test
    void isItemInUse_ShouldReturnFalseForUnusedCourseCode() {
        // Given
        when(masterDataRepository.findById("course-code-id")).thenReturn(Optional.of(courseCodeMasterData));
        when(documentRepository.existsByCourseCode("CS101")).thenReturn(false);
        when(documentPreferencesRepository.existsByPreferredCourseCode("CS101")).thenReturn(false);
        when(masterDataRepository.findByParentId("course-code-id")).thenReturn(Collections.emptyList());

        // When
        boolean result = masterDataService.isItemInUse("course-code-id");

        // Then
        assertFalse(result);
        verify(documentRepository).existsByCourseCode("CS101");
        verify(documentPreferencesRepository).existsByPreferredCourseCode("CS101");
        verify(masterDataRepository).findByParentId("course-code-id");
    }

    @Test
    void isItemInUse_ShouldReturnTrueForCourseLevelUsedInDocuments() {
        // Given
        when(masterDataRepository.findById("course-level-id")).thenReturn(Optional.of(courseLevelMasterData));
        when(documentRepository.existsByCourseLevelCode("BEGINNER")).thenReturn(true);
        lenient().when(documentPreferencesRepository.existsByPreferredLevel("BEGINNER")).thenReturn(false);
        when(masterDataRepository.findByParentId("course-level-id")).thenReturn(Collections.emptyList());

        // When
        boolean result = masterDataService.isItemInUse("course-level-id");

        // Then
        assertTrue(result);
        verify(documentRepository).existsByCourseLevelCode("BEGINNER");
        verify(masterDataRepository).findByParentId("course-level-id");
    }

    @Test
    void isItemInUse_ShouldReturnTrueForCategoryUsedInDocuments() {
        // Given
        when(masterDataRepository.findById("category-id")).thenReturn(Optional.of(categoryMasterData));
        when(documentRepository.existsByCategoryCode("LECTURE")).thenReturn(true);
        lenient().when(documentPreferencesRepository.existsByPreferredCategory("LECTURE")).thenReturn(false);
        when(masterDataRepository.findByParentId("category-id")).thenReturn(Collections.emptyList());

        // When
        boolean result = masterDataService.isItemInUse("category-id");

        // Then
        assertTrue(result);
        verify(documentRepository).existsByCategoryCode("LECTURE");
        verify(masterDataRepository).findByParentId("category-id");
    }

    @Test
    void isItemInUse_ShouldReturnFalseForNullTypeWithNoChildren() {
        // Given
        MasterData invalidMasterData = new MasterData();
        invalidMasterData.setId("invalid-id");
        invalidMasterData.setType(null);
        invalidMasterData.setCode("INVALID");
        when(masterDataRepository.findById("invalid-id")).thenReturn(Optional.of(invalidMasterData));

        // When
        boolean result = masterDataService.isItemInUse("invalid-id");

        // Then
        assertFalse(result);
        verify(masterDataRepository).findById("invalid-id");
        verify(documentRepository, never()).existsByMajorCode(anyString());
        verify(documentRepository, never()).existsByCourseCode(anyString());
        verify(documentRepository, never()).existsByCourseLevelCode(anyString());
        verify(documentRepository, never()).existsByCategoryCode(anyString());
    }



}