package com.dms.auth.service.impl;

import com.dms.auth.dto.RoleDto;
import com.dms.auth.dto.UserDto;
import com.dms.auth.dto.UserSearchResponse;
import com.dms.auth.dto.request.*;
import com.dms.auth.entity.AuthToken;
import com.dms.auth.entity.PasswordResetToken;
import com.dms.auth.entity.Role;
import com.dms.auth.entity.User;
import com.dms.auth.enums.AppRole;
import com.dms.auth.enums.TokenType;
import com.dms.auth.exception.InvalidRequestException;
import com.dms.auth.exception.ResourceNotFoundException;
import com.dms.auth.mapper.UserMapper;
import com.dms.auth.repository.PasswordResetTokenRepository;
import com.dms.auth.repository.RoleRepository;
import com.dms.auth.repository.UserRepository;
import com.dms.auth.security.jwt.JwtUtils;
import com.dms.auth.security.request.Verify2FARequest;
import com.dms.auth.security.response.TokenResponse;
import com.dms.auth.security.response.UserInfoResponse;
import com.dms.auth.security.service.CustomUserDetails;
import com.dms.auth.service.OtpService;
import com.dms.auth.service.PublishEventService;
import com.dms.auth.service.TokenService;
import com.dms.auth.service.TotpService;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceImplTest {


    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private TokenService authTokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TotpService totpService;

    @Mock
    private OtpService otpService;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PublishEventService publishEventService;

    @Mock
    private HttpServletRequest httpServletRequest;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private UserServiceImpl userService;

    private User testUser;
    private Role testRole;
    private CustomUserDetails userDetails;
    private UserDto userDto;
    private UUID testUserId;
    private GoogleAuthenticatorKey googleAuthenticatorKey;
    private PasswordResetToken passwordResetToken;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();

        testRole = new Role();
        testRole.setRoleId(UUID.randomUUID());
        testRole.setRoleName(AppRole.ROLE_USER);

        testUser = new User();
        testUser.setUserId(testUserId);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPassword("password"); // The actual password in the entity
        testUser.setRole(testRole);
        testUser.setAccountNonLocked(true);
        testUser.setEnabled(true);
        testUser.setCreatedAt(Instant.now());
        testUser.setCreatedBy("system");
        testUser.setUpdatedBy("system");

        userDetails = new CustomUserDetails(
                testUser.getUserId(),
                testUser.getUsername(),
                testUser.getEmail(),
                testUser.getPassword(),
                testUser.isTwoFactorEnabled(),
                testUser.isAccountNonLocked(),
                Collections.singletonList(new SimpleGrantedAuthority(testUser.getRole().getRoleName().name()))
        );

        userDto = new UserDto();
        userDto.setUserId(testUser.getUserId());
        userDto.setUsername(testUser.getUsername());
        userDto.setEmail(testUser.getEmail());
        userDto.setRole(new RoleDto(testRole.getRoleId(), testRole.getRoleName()));
        userDto.setEnabled(testUser.isEnabled());
        userDto.setAccountNonLocked(testUser.isAccountNonLocked());

        googleAuthenticatorKey = new GoogleAuthenticatorKey.Builder("TESTSECRET")
                .setVerificationCode(123456)
                .setScratchCodes(Arrays.asList(1111, 2222))
                .build();

        passwordResetToken = new PasswordResetToken();
        passwordResetToken.setId(UUID.randomUUID());
        passwordResetToken.setToken("reset-token");
        passwordResetToken.setUser(testUser);
        passwordResetToken.setExpiryDate(Instant.now().plusSeconds(3600));
        passwordResetToken.setUsed(false);
    }

    @Test
    void testAuthenticateUser_Success() {
        // Arrange
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setIdentifier("testuser");
        loginRequest.setPassword("password");

        when(userRepository.findByUsernameOrEmail(anyString(), anyString())).thenReturn(Optional.of(testUser));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);

        // Mock for BaseService.createToken method
        when(jwtUtils.generateTokenFromUsername(any(CustomUserDetails.class))).thenReturn("jwt-token");
        when(authTokenService.createRefreshToken(any(User.class), any(HttpServletRequest.class)))
                .thenReturn(AuthToken.builder().token("refresh-token").build());

        // Act
        TokenResponse response = userService.authenticateUser(loginRequest, httpServletRequest);

        // Assert
        assertNotNull(response);
        verify(userRepository).findByUsernameOrEmail("testuser", "testuser");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(jwtUtils).generateTokenFromUsername(any(CustomUserDetails.class));
        verify(authTokenService).createRefreshToken(any(User.class), any(HttpServletRequest.class));
    }

    @Test
    void testAuthenticateUser_AccountLocked() {
        // Arrange
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setIdentifier("testuser");
        loginRequest.setPassword("password");

        testUser.setAccountNonLocked(false);
        when(userRepository.findByUsernameOrEmail(anyString(), anyString())).thenReturn(Optional.of(testUser));

        // Act & Assert
        assertThrows(org.springframework.security.authentication.LockedException.class,
                () -> userService.authenticateUser(loginRequest, httpServletRequest));
        verify(userRepository).findByUsernameOrEmail("testuser", "testuser");
        verify(authenticationManager, never()).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void testRegisterUser_Success() {
        // Arrange
        SignupRequest request = new SignupRequest();
        request.setUsername("newuser");
        request.setEmail("new@example.com");
        request.setPassword("Password1@");

        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(roleRepository.findByRoleName(AppRole.ROLE_USER)).thenReturn(Optional.of(testRole));
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        userService.registerUser(request);

        // Assert
        verify(userRepository).existsByUsername("newuser");
        verify(userRepository).existsByEmail("new@example.com");
        verify(roleRepository).findByRoleName(AppRole.ROLE_USER);
        verify(passwordEncoder).encode("Password1@");
        verify(userRepository).save(any(User.class));
        verify(otpService).generateAndSendOtp(any(User.class));
    }

    @Test
    void testRegisterUser_UsernameExists() {
        // Arrange
        SignupRequest request = new SignupRequest();
        request.setUsername("testuser");
        request.setEmail("new@example.com");
        request.setPassword("Password1@");

        when(userRepository.existsByUsername(anyString())).thenReturn(true);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.registerUser(request));
        assertEquals("USERNAME_EXISTS", exception.getMessage());
        verify(userRepository).existsByUsername("testuser");
        verify(userRepository, never()).existsByEmail(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testRegisterUser_EmailExists() {
        // Arrange
        SignupRequest request = new SignupRequest();
        request.setUsername("newuser");
        request.setEmail("test@example.com");
        request.setPassword("Password1@");

        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.registerUser(request));
        assertEquals("EMAIL_EXISTS", exception.getMessage());
        verify(userRepository).existsByUsername("newuser");
        verify(userRepository).existsByEmail("test@example.com");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testGetUserInfo_Success() {
        // Arrange
        UserDetails userDetails = Mockito.mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("testuser");

        when(userDetails.getUsername()).thenReturn("testuser");
        when(userDetails.getAuthorities()).thenAnswer(invocation -> {
            List<GrantedAuthority> grantedAuthorities = new ArrayList<>();
            grantedAuthorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            return grantedAuthorities;
        });

        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(testUser));

        // Act
        UserInfoResponse response = userService.getUserInfo(userDetails);

        // Assert
        assertNotNull(response);
        assertEquals(testUser.getUserId(), response.getUserId());
        assertEquals(testUser.getUsername(), response.getUsername());
        assertEquals(testUser.getEmail(), response.getEmail());
        assertEquals(testUser.isAccountNonLocked(), response.isAccountNonLocked());
        assertEquals(testUser.isEnabled(), response.isEnabled());
        assertEquals(testUser.isTwoFactorEnabled(), response.isTwoFactorEnabled());
        assertEquals(1, response.getRoles().size());
        assertEquals("ROLE_USER", response.getRoles().get(0));

        verify(userRepository).findByUsername("testuser");
    }

    @Test
    void testGetUserInfo_UserNotFound() {
        // Arrange
        UserDetails userDetails = Mockito.mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("nonexistent");
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> userService.getUserInfo(userDetails));
        verify(userRepository).findByUsername("nonexistent");
    }

    @Test
    void testGetUserById_Success() {
        // Arrange
        UUID userId = testUser.getUserId();

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userMapper.convertToDto(any(User.class))).thenReturn(userDto);

        // Act
        UserDto result = userService.getUserById(userId);

        // Assert
        assertNotNull(result);
        assertEquals(testUser.getUserId(), result.getUserId());
        assertEquals(testUser.getUsername(), result.getUsername());
        verify(userRepository).findById(userId);
        verify(userMapper).convertToDto(testUser);
    }

    @Test
    void testGetUserById_UserNotFound() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> userService.getUserById(userId));
        verify(userRepository).findById(userId);
        verify(userMapper, never()).convertToDto(any(User.class));
    }

    @Test
    void testUpdatePassword_Success() throws InvalidRequestException {
        // Arrange
        UUID userId = testUser.getUserId();
        UpdatePasswordRequest request = new UpdatePasswordRequest();
        request.setCurrentPassword("currentPassword");
        request.setNewPassword("NewPassword1@");

        UserDetails userDetails = Mockito.mock(UserDetails.class);
        // Removed the unnecessary stubbing for userDetails.getUsername()

        // Store the original password for verification
        String originalPassword = testUser.getPassword();

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("currentPassword", originalPassword)).thenReturn(true);
        when(passwordEncoder.matches("NewPassword1@", originalPassword)).thenReturn(false);
        when(passwordEncoder.encode("NewPassword1@")).thenReturn("encodedNewPassword");

        // Act
        userService.updatePassword(userId, request, userDetails);

        // Assert
        verify(userRepository).findById(userId);
        verify(passwordEncoder).matches("currentPassword", originalPassword);
        verify(passwordEncoder).matches("NewPassword1@", originalPassword);
        verify(passwordEncoder).encode("NewPassword1@");
        verify(userRepository).save(testUser);

        // Verify that the password was updated in the user object
        assertEquals("encodedNewPassword", testUser.getPassword());
    }

    @Test
    void testUpdatePassword_IncorrectCurrentPassword() {
        // Arrange
        UUID userId = testUser.getUserId();
        UpdatePasswordRequest request = new UpdatePasswordRequest();
        request.setCurrentPassword("wrongPassword");
        request.setNewPassword("NewPassword1@");

        UserDetails userDetails = Mockito.mock(UserDetails.class);

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongPassword", testUser.getPassword())).thenReturn(false);

        // Act & Assert
        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> userService.updatePassword(userId, request, userDetails));
        assertEquals("INCORRECT_PASSWORD", exception.getMessage());
        verify(userRepository).findById(userId);
        verify(passwordEncoder).matches("wrongPassword", testUser.getPassword());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testUpdatePassword_SameAsOldPassword() {
        // Arrange
        UUID userId = testUser.getUserId();
        UpdatePasswordRequest request = new UpdatePasswordRequest();
        request.setCurrentPassword("currentPassword");
        request.setNewPassword("samePassword");

        UserDetails userDetails = Mockito.mock(UserDetails.class);

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("currentPassword", testUser.getPassword())).thenReturn(true);
        when(passwordEncoder.matches("samePassword", testUser.getPassword())).thenReturn(true);

        // Act & Assert
        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> userService.updatePassword(userId, request, userDetails));
        assertEquals("DIFFERENT_PASSWORD", exception.getMessage());
        verify(userRepository).findById(userId);
        verify(passwordEncoder).matches("currentPassword", testUser.getPassword());
        verify(passwordEncoder).matches("samePassword", testUser.getPassword());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testUpdateProfile_Success() {
        // Arrange
        UUID userId = testUser.getUserId();
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setUsername("updatedUsername");
        request.setEmail("updated@example.com");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.existsByUsername("updatedUsername")).thenReturn(false);
        when(userRepository.existsByEmail("updated@example.com")).thenReturn(false);

        // Act
        userService.updateProfile(userId, request);

        // Assert
        verify(userRepository).findById(userId);
        verify(userRepository).existsByUsername("updatedUsername");
        verify(userRepository).existsByEmail("updated@example.com");
        verify(userRepository).save(testUser);
        assertEquals("updatedUsername", testUser.getUsername());
        assertEquals("updated@example.com", testUser.getEmail());
    }

    @Test
    void testUpdateProfile_UsernameExists() {
        // Arrange
        UUID userId = testUser.getUserId();
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setUsername("existingUsername");
        request.setEmail("updated@example.com");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.existsByUsername("existingUsername")).thenReturn(true);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.updateProfile(userId, request));
        assertEquals("Username is already taken", exception.getMessage());
        verify(userRepository).findById(userId);
        verify(userRepository).existsByUsername("existingUsername");
        verify(userRepository, never()).existsByEmail(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testUpdateProfile_EmailExists() {
        // Arrange
        UUID userId = testUser.getUserId();
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setUsername("updatedUsername");
        request.setEmail("existing@example.com");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.existsByUsername("updatedUsername")).thenReturn(false);
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.updateProfile(userId, request));
        assertEquals("Email is already in use", exception.getMessage());
        verify(userRepository).findById(userId);
        verify(userRepository).existsByUsername("updatedUsername");
        verify(userRepository).existsByEmail("existing@example.com");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testSearchUsers_Success() {
        // Arrange
        String query = "test";
        List<User> users = new ArrayList<>();
        users.add(testUser);

        when(userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(query, query))
                .thenReturn(users);

        // Act
        List<UserSearchResponse> result = userService.searchUsers(query);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testUser.getUserId().toString(), result.get(0).userId());
        assertEquals(testUser.getUsername(), result.get(0).username());
        assertEquals(testUser.getEmail(), result.get(0).email());

        verify(userRepository).findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(query, query);
    }

    @Test
    void testGetUsersByIds_Success() {
        // Arrange
        List<UUID> userIds = new ArrayList<>();
        userIds.add(testUser.getUserId());

        List<User> foundUsers = new ArrayList<>();
        foundUsers.add(testUser);

        when(userRepository.findAllById(userIds)).thenReturn(foundUsers);

        // Act
        List<UserSearchResponse> result = userService.getUsersByIds(userIds);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testUser.getUserId().toString(), result.get(0).userId());
        assertEquals(testUser.getUsername(), result.get(0).username());
        assertEquals(testUser.getEmail(), result.get(0).email());

        verify(userRepository).findAllById(userIds);
    }

    @Test
    void testRefreshToken_Success() {
        // Arrange
        String refreshTokenString = "valid-refresh-token";
        AuthToken authToken = new AuthToken();
        authToken.setToken(refreshTokenString);
        authToken.setUser(testUser);
        authToken.setTokenType(TokenType.REFRESH);
        authToken.setRevoked(false);
        authToken.setExpiryDate(Instant.now().plusSeconds(3600));

        when(authTokenService.findByTokenAndType(refreshTokenString, TokenType.REFRESH))
                .thenReturn(Optional.of(authToken));
        when(authTokenService.verifyToken(authToken)) // Add this mock
                .thenReturn(authToken);
        when(jwtUtils.generateTokenFromUsername(any(CustomUserDetails.class)))
                .thenReturn("new-jwt-token");

        // Act
        TokenResponse response = userService.refreshToken(refreshTokenString);

        // Assert
        assertNotNull(response);
        assertEquals("new-jwt-token", response.getAccessToken());
        assertEquals(refreshTokenString, response.getRefreshToken());
        assertEquals("testuser", response.getUsername());
        verify(authTokenService).findByTokenAndType(refreshTokenString, TokenType.REFRESH);
        verify(authTokenService).verifyToken(authToken); // Verify the new mock
        verify(jwtUtils).generateTokenFromUsername(any(CustomUserDetails.class));
    }

    @Test
    void testRefreshToken_InvalidToken() {
        // Arrange
        String refreshTokenString = "invalid-refresh-token";
        when(authTokenService.findByTokenAndType(refreshTokenString, TokenType.REFRESH))
                .thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> userService.refreshToken(refreshTokenString));
        assertEquals("Refresh token not found in database", exception.getMessage());
        verify(authTokenService).findByTokenAndType(refreshTokenString, TokenType.REFRESH);
    }

    @Test
    void testLogout_Success() {
        // Arrange
        String refreshTokenString = "refresh-token";

        // Optional: Mock findByTokenAndType to simulate token not found or found
        when(authTokenService.findByTokenAndType(refreshTokenString, TokenType.REFRESH))
                .thenReturn(Optional.empty());

        // Act
        userService.logout(refreshTokenString);

        // Assert
        verify(authTokenService).findByTokenAndType(refreshTokenString, TokenType.REFRESH);
        verify(authTokenService).revokeToken(refreshTokenString, TokenType.REFRESH);
    }

    @Test
    void testGeneratePasswordResetToken_Success() {
        // Arrange
        String email = "test@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(testUser));
        when(passwordResetTokenRepository.findLatestByUserEmail(email)).thenReturn(Optional.empty());
        when(passwordResetTokenRepository.findAllByUserId(testUser.getUserId())).thenReturn(new ArrayList<>());

        // Act
        userService.generatePasswordResetToken(email);

        // Assert
        verify(userRepository).findByEmail(email);
        verify(passwordResetTokenRepository).findLatestByUserEmail(email);
        verify(passwordResetTokenRepository).findAllByUserId(testUser.getUserId());
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
        verify(publishEventService).sendPasswordResetEmail(eq(testUser), anyString(), anyInt());
    }

    @Test
    void testGeneratePasswordResetToken_RecentTokenExists() {
        // Arrange
        String email = "test@example.com";
        PasswordResetToken recentToken = new PasswordResetToken();
        recentToken.setCreatedAt(Instant.now()); // Token created just now

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(testUser));
        when(passwordResetTokenRepository.findLatestByUserEmail(email)).thenReturn(Optional.of(recentToken));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> userService.generatePasswordResetToken(email));
        assertEquals("Please wait 5 minutes before requesting another password reset email", exception.getMessage());

        verify(userRepository).findByEmail(email);
        verify(passwordResetTokenRepository).findLatestByUserEmail(email);
        verify(passwordResetTokenRepository, never()).findAllByUserId(any());
        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    void testGeneratePasswordResetToken_UserNotFound() {
        // Arrange
        String email = "nonexistent@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> userService.generatePasswordResetToken(email));
        verify(userRepository).findByEmail(email);
        verify(passwordResetTokenRepository, never()).findLatestByUserEmail(anyString());
    }

    @Test
    void testResetPassword_Success() {
        // Arrange
        String token = "valid-token";
        String newPassword = "NewPassword1@";

        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(passwordResetToken));
        when(passwordEncoder.encode(newPassword)).thenReturn("encoded-new-password");

        // Act
        userService.resetPassword(token, newPassword);

        // Assert
        verify(passwordResetTokenRepository).findByToken(token);
        verify(passwordEncoder).encode(newPassword);
        verify(userRepository).save(testUser);
        verify(passwordResetTokenRepository).save(passwordResetToken);

        assertTrue(passwordResetToken.isUsed());
        assertEquals("encoded-new-password", testUser.getPassword());
    }

    @Test
    void testResetPassword_TokenNotFound() {
        // Arrange
        String token = "invalid-token";
        String newPassword = "NewPassword1@";

        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.resetPassword(token, newPassword));
        assertEquals("INVALID_PASSWORD_RESET_TOKEN", exception.getMessage());

        verify(passwordResetTokenRepository).findByToken(token);
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void testResetPassword_TokenUsed() {
        // Arrange
        String token = "used-token";
        String newPassword = "NewPassword1@";

        passwordResetToken.setUsed(true);
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.resetPassword(token, newPassword));
        assertEquals("INVALID_PASSWORD_RESET_TOKEN", exception.getMessage());

        verify(passwordResetTokenRepository).findByToken(token);
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void testResetPassword_TokenExpired() {
        // Arrange
        String token = "expired-token";
        String newPassword = "NewPassword1@";

        passwordResetToken.setExpiryDate(Instant.now().minusSeconds(3600)); // Token expired 1 hour ago
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(passwordResetToken));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.resetPassword(token, newPassword));
        assertEquals("PASSWORD_RESET_TOKEN_EXPIRED", exception.getMessage());

        verify(passwordResetTokenRepository).findByToken(token);
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void testUpdateUserRole_Success() {
        // Arrange
        UUID userId = testUser.getUserId();
        String roleName = "ROLE_ADMIN";
        Role adminRole = new Role();
        adminRole.setRoleId(UUID.randomUUID());
        adminRole.setRoleName(AppRole.ROLE_ADMIN);

        UserDetails currentUser = mock(UserDetails.class);
        when(currentUser.getUsername()).thenReturn("admin");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(roleRepository.findByRoleName(AppRole.ROLE_ADMIN)).thenReturn(Optional.of(adminRole));

        // Act
        userService.updateUserRole(userId, roleName, currentUser);

        // Assert
        verify(userRepository).findById(userId);
        verify(roleRepository).findByRoleName(AppRole.ROLE_ADMIN);
        verify(userRepository).save(testUser);
        assertEquals(adminRole, testUser.getRole());
    }

    @Test
    void testUpdateUserRole_CannotChangeSelf() {
        // Arrange
        UUID userId = testUser.getUserId();
        String roleName = "ROLE_ADMIN";

        UserDetails currentUser = mock(UserDetails.class);
        when(currentUser.getUsername()).thenReturn(testUser.getUsername());

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.updateUserRole(userId, roleName, currentUser));
        assertEquals("CANNOT_CHANGE_ADMIN_ROLE", exception.getMessage());

        verify(userRepository).findById(userId);
        verify(roleRepository, never()).findByRoleName(any());
    }

    @Test
    void testUpdateUserRole_RoleNotFound() {
        // Arrange
        UUID userId = testUser.getUserId();
        String roleName = "ROLE_ADMIN";

        UserDetails currentUser = mock(UserDetails.class);
        when(currentUser.getUsername()).thenReturn("admin");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(roleRepository.findByRoleName(AppRole.ROLE_ADMIN)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> userService.updateUserRole(userId, roleName, currentUser));

        verify(userRepository).findById(userId);
        verify(roleRepository).findByRoleName(AppRole.ROLE_ADMIN);
        verify(userRepository, never()).save(any());
    }

    @Test
    void testGenerate2FASecret_Success() {
        // Arrange
        UUID userId = testUser.getUserId();

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(totpService.generateSecret()).thenReturn(googleAuthenticatorKey);

        // Act
        GoogleAuthenticatorKey result = userService.generate2FASecret(userId);

        // Assert
        assertNotNull(result);
        assertEquals(googleAuthenticatorKey, result);
        assertEquals("TESTSECRET", testUser.getTwoFactorSecret());

        verify(userRepository).findById(userId);
        verify(totpService).generateSecret();
        verify(userRepository).save(testUser);
    }

    @Test
    void testVerify2FACode_Success() {
        // Arrange
        UUID userId = testUser.getUserId();
        testUser.setTwoFactorSecret("TESTSECRET");

        Verify2FARequest request = new Verify2FARequest();
        request.setCode("123456");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(totpService.verifyCode(eq("TESTSECRET"), eq(123456))).thenReturn(true);

        // Act
        userService.verify2FACode(userId, request);

        // Assert
        assertTrue(testUser.isTwoFactorEnabled());

        verify(userRepository).findById(userId);
        verify(totpService).verifyCode("TESTSECRET", 123456);
        verify(userRepository).save(testUser);
    }

    @Test
    void testVerify2FACode_InvalidCode() {
        // Arrange
        UUID userId = testUser.getUserId();
        testUser.setTwoFactorSecret("TESTSECRET");

        Verify2FARequest request = new Verify2FARequest();
        request.setCode("123456");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(totpService.verifyCode(eq("TESTSECRET"), eq(123456))).thenReturn(false);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.verify2FACode(userId, request));
        assertEquals("Invalid 2FA code", exception.getMessage());

        verify(userRepository).findById(userId);
        verify(totpService).verifyCode("TESTSECRET", 123456);
        verify(userRepository, never()).save(any());
    }

    @Test
    void testVerify2FACode_NullCode() {
        // Arrange
        UUID userId = testUser.getUserId();

        Verify2FARequest request = new Verify2FARequest();
        // Code is null

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.verify2FACode(userId, request));
        assertEquals("Invalid code", exception.getMessage());

        verify(userRepository, never()).findById(any());
        verify(totpService, never()).verifyCode(anyString(), anyInt());
    }

    @Test
    void testDisable2FA_Success() {
        // Arrange
        UUID userId = testUser.getUserId();
        testUser.setTwoFactorEnabled(true);
        testUser.setTwoFactorSecret("TESTSECRET");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // Act
        userService.disable2FA(userId);

        // Assert
        assertFalse(testUser.isTwoFactorEnabled());
        assertNull(testUser.getTwoFactorSecret());

        verify(userRepository).findById(userId);
        verify(userRepository).save(testUser);
    }

    @Test
    void testGet2FAStatus_Success() {
        // Arrange
        String username = "testuser";
        testUser.setTwoFactorEnabled(true);

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(testUser));

        // Act
        boolean result = userService.get2FAStatus(username);

        // Assert
        assertTrue(result);
        verify(userRepository).findByUsername(username);
    }

    @Test
    void testGetQrCodeUrl_Success() {
        // Arrange
        String username = "testuser";
        String expectedUrl = "otpauth://totp/Document%20Management%20and%20Search%20System:testuser?secret=TESTSECRET";

        when(totpService.getQrCodeUrl(googleAuthenticatorKey, username)).thenReturn(expectedUrl);

        // Act
        String result = userService.getQrCodeUrl(googleAuthenticatorKey, username);

        // Assert
        assertEquals(expectedUrl, result);
        verify(totpService).getQrCodeUrl(googleAuthenticatorKey, username);
    }

    @Test
    void testUpdateStatus_AdminCanUpdateAll() {
        // Arrange
        UUID userId = testUser.getUserId();
        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setAccountLocked(true);
        request.setCredentialsExpired(true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // Act
        userService.updateStatus(userId, request, true); // true = isAdmin

        // Assert
        assertFalse(testUser.isAccountNonLocked()); // accountNonLocked should be the opposite of accountLocked

        // We use times(2) because the method is called twice in the implementation
        verify(userRepository, times(2)).findById(userId);
        verify(authTokenService).revokeAllUserTokens(testUser);
        verify(userRepository).save(testUser);
    }

    @Test
    void testUpdateStatus_NonAdminRestrictions() {
        // Arrange
        UUID userId = testUser.getUserId();
        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setAccountLocked(true);

        // Need to mock the repository to return the user first
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // Act & Assert
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> userService.updateStatus(userId, request, false)); // false = not admin

        verify(userRepository).findById(userId);
        verify(userRepository, never()).save(any());
    }


    @Test
    void testGetUserByUsername_Success() {
        // Arrange
        String username = "testuser";

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(testUser));

        // Act
        UserDto result = userService.getUserByUsername(username);

        // Assert
        assertNotNull(result);
        assertEquals(testUser.getUserId(), result.getUserId());
        assertEquals(username, result.getUsername());

        verify(userRepository).findByUsername(username);
    }

    @Test
    void testGetUserByUsername_NotFound() {
        // Arrange
        String username = "nonexistent";

        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> userService.getUserByUsername(username));

        verify(userRepository).findByUsername(username);
    }

    @Test
    void testAuthenticate_BadCredentials() {
        // Arrange
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setIdentifier("testuser");
        loginRequest.setPassword("wrongpassword");

        when(userRepository.findByUsernameOrEmail(anyString(), anyString()))
                .thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> userService.authenticateUser(loginRequest, httpServletRequest));

        assertEquals("USER_NOT_FOUND", exception.getMessage());
        verify(userRepository).findByUsernameOrEmail("testuser", "testuser");
        // The authentication manager is never called because the user isn't found first
        verify(authenticationManager, never()).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void testValidate2FACode_Success() {
        // Arrange
        Verify2FARequest request = new Verify2FARequest();
        request.setUsername("testuser");
        request.setCode("123456");

        // Set the two-factor secret on the test user
        testUser.setTwoFactorSecret("TESTSECRET");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        // Use doReturn().when() syntax to avoid argument matching issues
        doReturn(true).when(totpService).verifyCode(anyString(), anyInt());

        // Act
        boolean result = userService.validate2FACode(request);

        // Assert
        assertTrue(result);
        verify(userRepository).findByUsername("testuser");
        verify(totpService).verifyCode("TESTSECRET", 123456);
    }

    @Test
    void testValidate2FACode_InvalidCode() {
        // Arrange
        Verify2FARequest request = new Verify2FARequest();
        request.setUsername("testuser");
        request.setCode("123456");

        // Set the two-factor secret on the test user
        testUser.setTwoFactorSecret("TESTSECRET");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        // Use doReturn().when() syntax to avoid argument matching issues
        doReturn(false).when(totpService).verifyCode(anyString(), anyInt());

        // Act
        boolean result = userService.validate2FACode(request);

        // Assert
        assertFalse(result);
        verify(userRepository).findByUsername("testuser");
        verify(totpService).verifyCode("TESTSECRET", 123456);
    }

    @Test
    void testEnable2FA_Success() {
        // Arrange
        String username = "testuser";

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(testUser));

        // Act
        userService.enable2FA(username);

        // Assert
        assertTrue(testUser.isTwoFactorEnabled());
        verify(userRepository).findByUsername(username);
        verify(userRepository).save(testUser);
    }

    @Test
    void testAuthenticateUser_TwoFactorEnabled() {
        // Arrange
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setIdentifier("testuser");
        loginRequest.setPassword("password");

        testUser.setTwoFactorEnabled(true);
        when(userRepository.findByUsernameOrEmail(anyString(), anyString())).thenReturn(Optional.of(testUser));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(jwtUtils.generateTokenFromUsername(any(CustomUserDetails.class))).thenReturn("jwt-token");
        when(authTokenService.createRefreshToken(any(User.class), any(HttpServletRequest.class)))
                .thenReturn(AuthToken.builder().token("refresh-token").build());

        // Act
        TokenResponse response = userService.authenticateUser(loginRequest, httpServletRequest);

        // Assert
        assertNotNull(response);
        assertTrue(response.isEnabled());
        assertEquals("jwt-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        verify(userRepository).findByUsernameOrEmail("testuser", "testuser");
    }

    @Test
    void testAuthenticateUser_DisabledUser() {
        // Arrange
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setIdentifier("testuser");
        loginRequest.setPassword("password");

        testUser.setEnabled(false);
        when(userRepository.findByUsernameOrEmail(anyString(), anyString())).thenReturn(Optional.of(testUser));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);

        // Act
        TokenResponse response = userService.authenticateUser(loginRequest, httpServletRequest);

        // Assert
        assertNotNull(response);
        assertFalse(response.isEnabled());
        assertNull(response.getAccessToken());
        assertNull(response.getRefreshToken());
        verify(userRepository).findByUsernameOrEmail("testuser", "testuser");
        verify(jwtUtils, never()).generateTokenFromUsername(any());
    }

    @Test
    void testRefreshToken_VerificationFailure() {
        // Arrange
        String refreshTokenString = "expired-refresh-token";
        AuthToken authToken = new AuthToken();
        authToken.setToken(refreshTokenString);
        authToken.setUser(testUser);
        authToken.setTokenType(TokenType.REFRESH);
        authToken.setRevoked(true);

        when(authTokenService.findByTokenAndType(refreshTokenString, TokenType.REFRESH))
                .thenReturn(Optional.of(authToken));
        when(authTokenService.verifyToken(authToken))
                .thenThrow(new RuntimeException("Token is revoked"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> userService.refreshToken(refreshTokenString));
        assertEquals("Token is revoked", exception.getMessage());
        verify(authTokenService).findByTokenAndType(refreshTokenString, TokenType.REFRESH);
        verify(authTokenService).verifyToken(authToken);
        verify(jwtUtils, never()).generateTokenFromUsername(any());
    }

    @Test
    void testLogout_TokenExists_RevokesAccessTokens() {
        // Arrange
        String refreshTokenString = "refresh-token";
        AuthToken authToken = new AuthToken();
        authToken.setToken(refreshTokenString);
        authToken.setUser(testUser);
        authToken.setTokenType(TokenType.REFRESH);

        when(authTokenService.findByTokenAndType(refreshTokenString, TokenType.REFRESH))
                .thenReturn(Optional.of(authToken));

        // Act
        userService.logout(refreshTokenString);

        // Assert
        verify(authTokenService).findByTokenAndType(refreshTokenString, TokenType.REFRESH);
        verify(authTokenService).revokeToken(refreshTokenString, TokenType.REFRESH);
        verify(authTokenService).revokeAllUserTokensByType(testUser, TokenType.ACCESS);
    }

    @Test
    void testGeneratePasswordResetToken_MultipleOldTokens() {
        // Arrange
        String email = "test@example.com";
        PasswordResetToken oldToken1 = new PasswordResetToken();
        oldToken1.setUser(testUser);
        oldToken1.setUsed(false);
        PasswordResetToken oldToken2 = new PasswordResetToken();
        oldToken2.setUser(testUser);
        oldToken2.setUsed(false);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(testUser));
        when(passwordResetTokenRepository.findLatestByUserEmail(email)).thenReturn(Optional.empty());
        when(passwordResetTokenRepository.findAllByUserId(testUser.getUserId()))
                .thenReturn(Arrays.asList(oldToken1, oldToken2));

        // Act
        userService.generatePasswordResetToken(email);

        // Assert
        assertTrue(oldToken1.isUsed());
        assertTrue(oldToken2.isUsed());
        verify(passwordResetTokenRepository).saveAllAndFlush(Arrays.asList(oldToken1, oldToken2));
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
        verify(publishEventService).sendPasswordResetEmail(eq(testUser), anyString(), eq(5 * 60));
    }

    @Test
    void testResetPassword_TokenAlreadyUsed() {
        // Arrange
        String token = "used-token";
        String newPassword = "NewPassword1@";

        passwordResetToken.setUsed(true);
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(passwordResetToken));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.resetPassword(token, newPassword));
        assertEquals("PASSWORD_RESET_TOKEN_USED", exception.getMessage());

        verify(passwordResetTokenRepository).findByToken(token);
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void testVerify2FACode_AlreadyEnabled() {
        // Arrange
        UUID userId = testUser.getUserId();
        testUser.setTwoFactorSecret("TESTSECRET");
        testUser.setTwoFactorEnabled(true);

        Verify2FARequest request = new Verify2FARequest();
        request.setCode("123456");

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(totpService.verifyCode(eq("TESTSECRET"), eq(123456))).thenReturn(true);

        // Act
        userService.verify2FACode(userId, request);

        // Assert
        assertTrue(testUser.isTwoFactorEnabled());
        verify(userRepository).findById(userId);
        verify(totpService).verifyCode("TESTSECRET", 123456);
        verify(userRepository).save(testUser); // Still saves due to updatedBy
    }

    @Test
    void testValidate2FACode_NullUsername() {
        // Arrange
        Verify2FARequest request = new Verify2FARequest();
        request.setCode("123456");
        request.setUsername(null);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.validate2FACode(request));
        assertEquals("Invalid username", exception.getMessage());

        verify(userRepository, never()).findByUsername(anyString());
        verify(totpService, never()).verifyCode(anyString(), anyInt());
    }

    @Test
    void testUpdateStatus_AdminCredentialsExpired() {
        // Arrange
        UUID userId = testUser.getUserId();
        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setCredentialsExpired(true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // Act
        userService.updateStatus(userId, request, true);

        // Assert
        verify(userRepository, times(2)).findById(userId);
        verify(authTokenService).revokeAllUserTokens(testUser);
        verify(userRepository).save(testUser);
    }

    @Test
    void testUpdateProfile_NoChanges() {
        // Arrange
        UUID userId = testUser.getUserId();
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setUsername(testUser.getUsername());
        request.setEmail(testUser.getEmail());

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // Act
        userService.updateProfile(userId, request);

        // Assert
        verify(userRepository).findById(userId);
        verify(userRepository, never()).existsByUsername(anyString());
        verify(userRepository, never()).existsByEmail(anyString());
        verify(userRepository).save(testUser);
    }
}