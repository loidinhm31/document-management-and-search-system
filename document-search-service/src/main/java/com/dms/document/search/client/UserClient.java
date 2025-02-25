package com.dms.document.search.client;


import com.dms.document.search.config.FeignConfig;
import com.dms.document.search.dto.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "auth-service", configuration = FeignConfig.class)
public interface UserClient {

    @GetMapping("/api/v1/users")
    ResponseEntity<UserResponse> getUserByUsername(@RequestParam("username") String username);
}
