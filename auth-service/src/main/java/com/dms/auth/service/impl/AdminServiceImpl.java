package com.dms.auth.service.impl;

import com.dms.auth.dto.UserDto;
import com.dms.auth.entity.Role;
import com.dms.auth.entity.User;
import com.dms.auth.enums.AppRole;
import com.dms.auth.mapper.UserMapper;
import com.dms.auth.repository.RoleRepository;
import com.dms.auth.repository.UserRepository;
import com.dms.auth.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {
    private final UserMapper userMapper;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Override
    public Page<UserDto> getAllUsers(String search, Boolean enabled, String role, Pageable pageable) {
        Specification<User> spec = Specification.where(null);

        if (search != null && !search.trim().isEmpty()) {
            spec = spec.and((root, query, cb) ->
                    cb.or(
                            cb.like(cb.lower(root.get("username")), "%" + search.toLowerCase() + "%"),
                            cb.like(cb.lower(root.get("email")), "%" + search.toLowerCase() + "%")
                    )
            );
        }

        if (enabled != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("enabled"), enabled));
        }

        if (role != null && !role.trim().isEmpty()) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.join("role").get("roleName"), AppRole.valueOf(role))
            );
        }

        // Add default sort by username if no sort is specified
        if (pageable.getSort().isEmpty()) {
            pageable = PageRequest.of(
                    pageable.getPageNumber(),
                    pageable.getPageSize(),
                    Sort.by(Sort.Direction.ASC, "username")
            );
        }

        return userRepository.findAll(spec, pageable).map(userMapper::convertToDto);
    }

    @Override
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

}