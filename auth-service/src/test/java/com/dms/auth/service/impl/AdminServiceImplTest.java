package com.dms.auth.service.impl;

import com.dms.auth.dto.UserDto;
import com.dms.auth.entity.Role;
import com.dms.auth.entity.User;
import com.dms.auth.enums.AppRole;
import com.dms.auth.mapper.UserMapper;
import com.dms.auth.repository.RoleRepository;
import com.dms.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AdminServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private AdminServiceImpl adminService;

    private User user1;
    private User user2;
    private UserDto userDto1;
    private UserDto userDto2;
    private Role role1;
    private Role role2;
    private List<User> userList;
    private Page<User> userPage;
    private List<Role> roleList;

    @BeforeEach
    void setUp() {
        // Setup test data
        role1 = new Role(AppRole.ROLE_USER);
        role1.setRoleId(UUID.randomUUID());

        role2 = new Role(AppRole.ROLE_ADMIN);
        role2.setRoleId(UUID.randomUUID());

        user1 = new User("testuser1", "user1@example.com", "password");
        user1.setUserId(UUID.randomUUID());
        user1.setRole(role1);
        user1.setEnabled(true);

        user2 = new User("testuser2", "user2@example.com", "password");
        user2.setUserId(UUID.randomUUID());
        user2.setRole(role2);
        user2.setEnabled(false);

        userDto1 = UserDto.builder()
                .userId(user1.getUserId())
                .username(user1.getUsername())
                .email(user1.getEmail())
                .enabled(user1.isEnabled())
                .build();

        userDto2 = UserDto.builder()
                .userId(user2.getUserId())
                .username(user2.getUsername())
                .email(user2.getEmail())
                .enabled(user2.isEnabled())
                .build();

        userList = Arrays.asList(user1, user2);
        userPage = new PageImpl<>(userList);
        roleList = Arrays.asList(role1, role2);
    }

    @Test
    void getAllUsers_WithNoFilters_ShouldReturnAllUsers() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10, Sort.by("lastName")); // Using non-empty sort
        doReturn(userPage).when(userRepository).findAll(any(Specification.class), any(Pageable.class));
        when(userMapper.convertToDto(user1)).thenReturn(userDto1);
        when(userMapper.convertToDto(user2)).thenReturn(userDto2);

        // Act
        Page<UserDto> result = adminService.getAllUsers(null, null, null, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
        verify(userRepository).findAll(any(Specification.class), eq(pageable)); // Same pageable should be used
        verify(userMapper).convertToDto(user1);
        verify(userMapper).convertToDto(user2);
    }

    @Test
    void getAllUsers_WithEmptySearchParam_ShouldIgnoreSearch() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        doReturn(userPage).when(userRepository).findAll(any(Specification.class), any(Pageable.class));
        when(userMapper.convertToDto(user1)).thenReturn(userDto1);
        when(userMapper.convertToDto(user2)).thenReturn(userDto2);

        // Act - Testing with empty search string (should be treated as null)
        Page<UserDto> result = adminService.getAllUsers("   ", null, null, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
        verify(userRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void getAllUsers_WithValidSearchParam_ShouldApplyFilter() {
        // Arrange
        String searchTerm = "user1";
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> filteredUserPage = new PageImpl<>(List.of(user1));

        doReturn(filteredUserPage).when(userRepository).findAll(any(Specification.class), any(Pageable.class));
        when(userMapper.convertToDto(user1)).thenReturn(userDto1);

        // Act
        Page<UserDto> result = adminService.getAllUsers(searchTerm, null, null, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(userRepository).findAll(any(Specification.class), any(Pageable.class));
        verify(userMapper).convertToDto(user1);
        verify(userMapper, never()).convertToDto(user2);
    }

    @Test
    void getAllUsers_WithEnabledTrue_ShouldFilterEnabledUsers() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> filteredUserPage = new PageImpl<>(List.of(user1));

        doReturn(filteredUserPage).when(userRepository).findAll(any(Specification.class), any(Pageable.class));
        when(userMapper.convertToDto(user1)).thenReturn(userDto1);

        // Act
        Page<UserDto> result = adminService.getAllUsers(null, true, null, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(userRepository).findAll(any(Specification.class), any(Pageable.class));
        verify(userMapper).convertToDto(user1);
        verify(userMapper, never()).convertToDto(user2);
    }

    @Test
    void getAllUsers_WithEnabledFalse_ShouldFilterDisabledUsers() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> filteredUserPage = new PageImpl<>(List.of(user2));

        doReturn(filteredUserPage).when(userRepository).findAll(any(Specification.class), any(Pageable.class));
        when(userMapper.convertToDto(user2)).thenReturn(userDto2);

        // Act
        Page<UserDto> result = adminService.getAllUsers(null, false, null, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(userRepository).findAll(any(Specification.class), any(Pageable.class));
        verify(userMapper).convertToDto(user2);
        verify(userMapper, never()).convertToDto(user1);
    }

    @Test
    void getAllUsers_WithEmptyRoleParam_ShouldIgnoreRole() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        doReturn(userPage).when(userRepository).findAll(any(Specification.class), any(Pageable.class));
        when(userMapper.convertToDto(user1)).thenReturn(userDto1);
        when(userMapper.convertToDto(user2)).thenReturn(userDto2);

        // Act - Testing with empty role string (should be treated as null)
        Page<UserDto> result = adminService.getAllUsers(null, null, "   ", pageable);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
        verify(userRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void getAllUsers_WithValidRoleParam_ShouldFilterByRole() {
        // Arrange
        String role = "ROLE_ADMIN";
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> filteredUserPage = new PageImpl<>(List.of(user2));

        doReturn(filteredUserPage).when(userRepository).findAll(any(Specification.class), any(Pageable.class));
        when(userMapper.convertToDto(user2)).thenReturn(userDto2);

        // Act
        Page<UserDto> result = adminService.getAllUsers(null, null, role, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(userRepository).findAll(any(Specification.class), any(Pageable.class));
        verify(userMapper).convertToDto(user2);
        verify(userMapper, never()).convertToDto(user1);
    }

    @Test
    void getAllUsers_WithAllFilters_ShouldApplyAllFilters() {
        // Arrange
        String searchTerm = "user2";
        boolean enabled = false;
        String role = "ROLE_ADMIN";
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> filteredUserPage = new PageImpl<>(List.of(user2));

        doReturn(filteredUserPage).when(userRepository).findAll(any(Specification.class), any(Pageable.class));
        when(userMapper.convertToDto(user2)).thenReturn(userDto2);

        // Act
        Page<UserDto> result = adminService.getAllUsers(searchTerm, enabled, role, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(userRepository).findAll(any(Specification.class), any(Pageable.class));
        verify(userMapper).convertToDto(user2);
        verify(userMapper, never()).convertToDto(user1);
    }

    @Test
    void getAllUsers_WithEmptyResult_ShouldReturnEmptyPage() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> emptyPage = new PageImpl<>(Collections.emptyList());

        doReturn(emptyPage).when(userRepository).findAll(any(Specification.class), any(Pageable.class));

        // Act
        Page<UserDto> result = adminService.getAllUsers("nonexistent", null, null, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
        verify(userRepository).findAll(any(Specification.class), any(Pageable.class));
        // No mapper calls should be made
        verify(userMapper, never()).convertToDto(any());
    }

    @Test
    void getAllRoles_ShouldReturnAllRoles() {
        // Arrange
        when(roleRepository.findAll()).thenReturn(roleList);

        // Act
        List<Role> result = adminService.getAllRoles();

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(role1, result.get(0));
        assertEquals(role2, result.get(1));
        verify(roleRepository).findAll();
    }

    @Test
    void getAllRoles_WithEmptyRoles_ShouldReturnEmptyList() {
        // Arrange
        when(roleRepository.findAll()).thenReturn(Collections.emptyList());

        // Act
        List<Role> result = adminService.getAllRoles();

        // Assert
        assertNotNull(result);
        assertEquals(0, result.size());
        verify(roleRepository).findAll();
    }
}