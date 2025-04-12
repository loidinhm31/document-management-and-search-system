package com.dms.auth.service.impl;

import com.dms.auth.dto.UserDto;
import com.dms.auth.dto.request.UserSearchRequest;
import com.dms.auth.entity.Role;
import com.dms.auth.entity.User;
import com.dms.auth.enums.AppRole;
import com.dms.auth.mapper.UserMapper;
import com.dms.auth.repository.RoleRepository;
import com.dms.auth.repository.UserRepository;
import com.dms.auth.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {
    private final UserMapper userMapper;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    // Pattern to validate search input and prevent SQL injection
    private static final Pattern SEARCH_VALIDATION_PATTERN = Pattern.compile("^[a-zA-Z0-9.@_\\-\\s]*$");

    @Override
    public Page<UserDto> getAllUsers(UserSearchRequest userSearchRequest) {
        Specification<User> spec = Specification.where(null);

        if (StringUtils.isNotEmpty(userSearchRequest.search()) && !userSearchRequest.search().trim().isEmpty()) {
            String searchTerm = userSearchRequest.search().trim();

            // Validate search term to ensure it doesn't contain unsafe characters
            if (!SEARCH_VALIDATION_PATTERN.matcher(searchTerm).matches()) {
                // If invalid characters, return empty result set
                return new PageImpl<>(Collections.emptyList(), PageRequest.of(userSearchRequest.page(), userSearchRequest.size()), 0);
            }

            // Escape special SQL LIKE characters if needed
            String escapedSearchTerm = searchTerm
                    .replace("%", "\\%")
                    .replace("_", "\\_");

            spec = spec.and((root, query, cb) ->
                    cb.or(
                            cb.like(cb.lower(root.get("username")), "%" + escapedSearchTerm.toLowerCase() + "%", '\\'),
                            cb.like(cb.lower(root.get("email")), "%" + escapedSearchTerm.toLowerCase() + "%", '\\')
                    )
            );
        }

        if (BooleanUtils.isTrue(userSearchRequest.enabled())) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("enabled"), userSearchRequest.enabled()));
        }

        if (StringUtils.isNotEmpty(userSearchRequest.role()) && !userSearchRequest.role().trim().isEmpty()) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.join("role").get("roleName"), AppRole.valueOf(userSearchRequest.role()))
            );
        }

        Pageable pageable = PageRequest.ofSize(10);
        // Add default sort by username if no sort is specified
        if (StringUtils.isEmpty(userSearchRequest.sortField()) || StringUtils.isEmpty(userSearchRequest.sortDirection())) {
            pageable = PageRequest.of(
                    userSearchRequest.page(),
                    userSearchRequest.size(),
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