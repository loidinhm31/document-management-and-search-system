package com.dms.processor.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.message.BasicHeader;
import org.apache.http.ssl.SSLContexts;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import java.util.Base64;

@Slf4j
@Configuration
public class OpenSearchConfig {

    @Value("${opensearch.host}")
    private String openSearchHost;

    @Value("${opensearch.port:443}")
    private int openSearchPort;

    @Value("${opensearch.username}")
    private String openSearchUsername;

    @Value("${opensearch.password}")
    private String openSearchPassword;

    @Value("${opensearch.scheme:https}")
    private String scheme;

    @Value("${opensearch.aws:false}")
    private boolean isAws;

    @Bean(destroyMethod = "close")
    public RestHighLevelClient openSearchClient() throws Exception {
        log.info("Initializing OpenSearch client with host: {}, port: {}, scheme: {}",
                openSearchHost, openSearchPort, scheme);

        RestClientBuilder builder = RestClient.builder(
                new HttpHost(openSearchHost, openSearchPort, scheme));

        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials(openSearchUsername, openSearchPassword));

        builder.setHttpClientConfigCallback(httpClientBuilder -> {
            httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
                    .setMaxConnTotal(100)
                    .setMaxConnPerRoute(30);

            if (isAws) {
                try {
                    // Configure SSL for AWS OpenSearch
                    SSLContext sslContext = SSLContexts.custom()
                            .loadTrustMaterial(null, (chain, authType) -> true)
                            .build();

                    return httpClientBuilder
                            .setSSLContext(sslContext);
                } catch (Exception e) {
                    log.error("Error configuring SSL for AWS OpenSearch", e);
                    throw new RuntimeException("Failed to configure AWS OpenSearch client", e);
                }
            }
            return httpClientBuilder;
        });

        builder.setDefaultHeaders(new Header[]{
                new BasicHeader("Authorization", "Basic " +
                        Base64.getEncoder().encodeToString(
                                (openSearchUsername + ":" + openSearchPassword).getBytes()
                        ))
        });

        builder.setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                .setConnectTimeout(5000)
                .setSocketTimeout(60000)
                .setConnectionRequestTimeout(0));

        return new RestHighLevelClient(builder);
    }
}