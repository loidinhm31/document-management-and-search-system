package com.dms.auth.cronjob;

import com.dms.auth.services.impl.RefreshTokenService;
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
    private final RefreshTokenService refreshTokenService;

    @Scheduled(cron = "0 0 * * * *")
//    @Scheduled(cron = "0 0 */6 * * *")
    public void clearExpiredToken() {
        log.info("Clear Expired Token Job Start");
        refreshTokenService.removeExpiredTokens();
        log.info("Clear Expired Token Job End");
    }
}
