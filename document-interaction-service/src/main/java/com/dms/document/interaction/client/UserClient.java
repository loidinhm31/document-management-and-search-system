package com.dms.document.interaction.client;


import com.dms.document.interaction.config.FeignConfig;
import com.dms.document.interaction.dto.UserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "auth-service", configuration = FeignConfig.class)
public interface UserClient {

    @GetMapping("/api/v1/users")
    ResponseEntity<UserDto> getUserByUsername(@RequestParam("username") String username);

    @PostMapping("/api/v1/users/details")
    ResponseEntity<List<UserDto>> getUsersByIds(@RequestBody List<UUID> userIds);
}
