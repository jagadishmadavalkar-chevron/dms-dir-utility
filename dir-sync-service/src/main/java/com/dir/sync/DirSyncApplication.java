package com.dir.sync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// @EnableBatchProcessing is intentionally omitted — in Spring Boot 3.x it disables
// Boot's auto-configuration of JobRepository and schema initialisation.
// Spring Boot 3 auto-configures Spring Batch without this annotation.
@SpringBootApplication
public class DirSyncApplication {

    public static void main(String[] args) {
        SpringApplication.run(DirSyncApplication.class, args);
    }
}
