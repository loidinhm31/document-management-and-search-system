package com.sdms.auth.mappers;

import com.sdms.auth.dtos.UserDto;
import com.sdms.auth.entities.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    public UserDto convertToDto(User user) {
        return new UserDto(
                user.getUserId(),
                user.getUsername(),
                user.getEmail(),
                user.isAccountNonLocked(),
                user.isAccountNonExpired(),
                user.isCredentialsNonExpired(),
                user.isEnabled(),
                user.getCredentialsExpiryDate(),
                user.getAccountExpiryDate(),
                user.getTwoFactorSecret(),
                user.isTwoFactorEnabled(),
                user.getSignUpMethod(),
                user.getRole(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

}
