package com.dir.sync.config;

import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a shared HttpClient bean for SAP BAPI calls.
 * Each SAPOperations/strategy thread creates its own instance for connection isolation,
 * but the auth-token requests use this shared bean.
 */
@Configuration
public class SapHttpClientConfig {

    @Bean
    public HttpClient sapHttpClient() {
        return HttpClient.newHttpClient();
    }
}
