package com.dms.auth.mapper;

import com.dms.auth.dto.RoleDto;
import com.dms.auth.dto.UserDto;
import com.dms.auth.entity.User;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class UserMapper {
    public UserDto convertToDto(User user) {
        return new UserDto(
                user.getUserId(),
                user.getUsername(),
                user.getEmail(),
                user.isAccountNonLocked(),
                user.isEnabled(),
                user.getTwoFactorSecret(),
                user.isTwoFactorEnabled(),
                user.getSignUpMethod(),
                new RoleDto(user.getRole().getRoleId(), user.getRole().getRoleName()),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

}
