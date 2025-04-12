package com.dms.auth.cronjob;

import com.dms.auth.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@RequiredArgsConstructor
@Slf4j
@EnableScheduling
@Configuration
public class ClearExpiredTokenJob {
    private final TokenService tokenService;

//    @Scheduled(cron = "0 0 * * * *")
    @Scheduled(cron = "0 0 */6 * * *")
    public void clearExpiredToken() {
        log.info("Clear Expired Token Job Start");
        tokenService.removeExpiredTokens();
        log.info("Clear Expired Token Job End");
    }
}
