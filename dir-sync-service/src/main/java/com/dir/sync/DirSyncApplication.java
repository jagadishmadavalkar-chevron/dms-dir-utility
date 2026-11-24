package com.dir.sync;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableBatchProcessing
public class DirSyncApplication {

    public static void main(String[] args) {
        SpringApplication.run(DirSyncApplication.class, args);
    }
}
