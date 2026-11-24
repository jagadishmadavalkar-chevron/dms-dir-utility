package com.dir.sync.batch;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.dir.sync.batch.step.BapiDispatchTasklet;
import com.dir.sync.batch.step.DeltaTasklet;
import com.dir.sync.batch.step.FetchDocsTasklet;
import com.dir.sync.batch.step.FlocFetchTasklet;
import com.dir.sync.batch.step.InitializationTasklet;
import com.dir.sync.batch.step.ProcessingTasklet;
import com.dir.sync.batch.step.ReportTasklet;
import com.dir.sync.batch.step.StagingTasklet;

/**
 * Spring Batch job definition — mirrors the sequential phase execution in
 * PrimaryThreadForBu.executeForBu().
 *
 * Step order:
 *   1. init       — decrypt credentials, open DB connection, initialise BuContext/PipelineState
 *   2. fetchDocs  — pull documents from Documentum (D2 DQL / cutOver / manual)
 *   3. staging    — populate staging table
 *   4. delta      — compare and find deltas
 *   5. flocFetch  — retrieve planning plants / FLOC alternate labels
 *   6. processing — populate processing table
 *   7. bapiDispatch — call SAP BAPIs (ExecutorService / CountDownLatch preserved)
 *   8. report     — generate report + cleanUp
 */
@Configuration
public class DirSyncJobConfig {

    private static final Logger logger = LogManager.getLogger(DirSyncJobConfig.class);

    @Autowired private InitializationTasklet initTasklet;
    @Autowired private FetchDocsTasklet      fetchTasklet;
    @Autowired private StagingTasklet        stagingTasklet;
    @Autowired private DeltaTasklet          deltaTasklet;
    @Autowired private FlocFetchTasklet      flocTasklet;
    @Autowired private ProcessingTasklet     processingTasklet;
    @Autowired private BapiDispatchTasklet   bapiTasklet;
    @Autowired private ReportTasklet         reportTasklet;

    // ------------------------------------------------------------------ Steps

    @Bean
    public Step initStep(JobRepository repo, PlatformTransactionManager tx) {
        return new StepBuilder("initStep", repo)
                .tasklet(initTasklet, tx)
                .build();
    }

    @Bean
    public Step fetchDocsStep(JobRepository repo, PlatformTransactionManager tx) {
        return new StepBuilder("fetchDocsStep", repo)
                .tasklet(fetchTasklet, tx)
                .build();
    }

    @Bean
    public Step stagingStep(JobRepository repo, PlatformTransactionManager tx) {
        return new StepBuilder("stagingStep", repo)
                .tasklet(stagingTasklet, tx)
                .build();
    }

    @Bean
    public Step deltaStep(JobRepository repo, PlatformTransactionManager tx) {
        return new StepBuilder("deltaStep", repo)
                .tasklet(deltaTasklet, tx)
                .build();
    }

    @Bean
    public Step flocFetchStep(JobRepository repo, PlatformTransactionManager tx) {
        return new StepBuilder("flocFetchStep", repo)
                .tasklet(flocTasklet, tx)
                .build();
    }

    @Bean
    public Step processingStep(JobRepository repo, PlatformTransactionManager tx) {
        return new StepBuilder("processingStep", repo)
                .tasklet(processingTasklet, tx)
                .build();
    }

    @Bean
    public Step bapiDispatchStep(JobRepository repo, PlatformTransactionManager tx) {
        return new StepBuilder("bapiDispatchStep", repo)
                .tasklet(bapiTasklet, tx)
                .build();
    }

    @Bean
    public Step reportStep(JobRepository repo, PlatformTransactionManager tx) {
        return new StepBuilder("reportStep", repo)
                .tasklet(reportTasklet, tx)
                .build();
    }

    // ------------------------------------------------------------------ Job

    @Bean
    public Job dirSyncJob(
            JobRepository repo,
            Step initStep,
            Step fetchDocsStep,
            Step stagingStep,
            Step deltaStep,
            Step flocFetchStep,
            Step processingStep,
            Step bapiDispatchStep,
            Step reportStep) {

        return new JobBuilder("dirSyncJob", repo)
                .listener(jobExecutionListener())
                .start(initStep)
                .next(fetchDocsStep)
                .next(stagingStep)
                .next(deltaStep)
                .next(flocFetchStep)
                .next(processingStep)
                .next(bapiDispatchStep)
                .next(reportStep)
                .build();
    }

    @Bean
    public JobExecutionListener jobExecutionListener() {
        return new JobExecutionListener() {
            @Override
            public void beforeJob(JobExecution jobExecution) {
                logger.info("============================================================");
                logger.info("[JOB] dirSyncJob starting — jobId={}", jobExecution.getJobId());
                logger.info("============================================================");
            }

            @Override
            public void afterJob(JobExecution jobExecution) {
                logger.info("============================================================");
                logger.info("[JOB] dirSyncJob finished — status={} jobId={}",
                        jobExecution.getStatus(), jobExecution.getJobId());
                logger.info("============================================================");
            }
        };
    }
}
