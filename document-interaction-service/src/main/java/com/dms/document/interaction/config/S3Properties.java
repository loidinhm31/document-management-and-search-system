package com.dms.document.interaction.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "aws.s3")
public class S3Properties {
    private String bucketName;
    private String region;
    private String accessKey;
    private String secretKey;
}