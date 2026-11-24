package com.dir.sync.api;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST trigger for the Spring Batch job, replacing the original CLI main().
 *
 * POST /api/jobs/trigger  — launches dirSyncJob asynchronously
 * GET  /api/jobs/status   — returns latest execution status
 */
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private static final Logger logger = LogManager.getLogger(JobController.class);

    @Autowired private JobLauncher jobLauncher;
    @Autowired private Job         dirSyncJob;
    @Autowired private JobExplorer jobExplorer;

    /**
     * Trigger the DIR sync job. Each call uses a unique timestamp parameter so
     * Spring Batch does not reject it as a duplicate.
     */
    @PostMapping("/trigger")
    public ResponseEntity<Map<String, Object>> trigger() {
        Map<String, Object> body = new HashMap<>();
        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("triggeredAt", LocalDateTime.now().toString())
                    .toJobParameters();

            JobExecution execution = jobLauncher.run(dirSyncJob, params);
            body.put("status",    execution.getStatus().toString());
            body.put("jobId",     execution.getJobId());
            body.put("startTime", execution.getStartTime() != null ? execution.getStartTime().toString() : null);
            logger.info("[API] Job triggered — jobId={} status={}", execution.getJobId(), execution.getStatus());
            return ResponseEntity.accepted().body(body);
        } catch (Exception e) {
            logger.error("[API] Failed to trigger job", e);
            body.put("error",   e.getMessage());
            body.put("status",  "FAILED");
            return ResponseEntity.internalServerError().body(body);
        }
    }

    /**
     * Return the latest execution status for dirSyncJob.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> body = new HashMap<>();
        try {
            var instances = jobExplorer.getJobInstances("dirSyncJob", 0, 1);
            if (instances.isEmpty()) {
                body.put("status", "NO_EXECUTIONS");
                return ResponseEntity.ok(body);
            }
            var executions = jobExplorer.getJobExecutions(instances.get(0));
            if (executions.isEmpty()) {
                body.put("status", "NO_EXECUTIONS");
                return ResponseEntity.ok(body);
            }
            JobExecution latest = executions.get(0);
            body.put("status",    latest.getStatus().toString());
            body.put("jobId",     latest.getJobId());
            body.put("startTime", latest.getStartTime() != null ? latest.getStartTime().toString() : null);
            body.put("endTime",   latest.getEndTime()   != null ? latest.getEndTime().toString()   : null);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            logger.error("[API] Failed to retrieve job status", e);
            body.put("error",  e.getMessage());
            body.put("status", "ERROR");
            return ResponseEntity.internalServerError().body(body);
        }
    }
}
