package com.dms.auth.service;

import com.dms.auth.dto.UserDto;
import com.dms.auth.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface AdminService {
    Page<UserDto> getAllUsers(String search, Boolean enabled, String role, Pageable pageable);

    List<Role> getAllRoles();

}