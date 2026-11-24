package com.dir.sync.batch.step;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.dir.sync.config.DirJobProperties;
import com.dir.sync.model.BuContext;
import com.dir.sync.model.PipelineState;
import com.dir.sync.service.DirProcessingService;

/**
 * REPORT phase — mirrors the reporting block + cleanUp() in PrimaryThreadForBu.
 */
@Component
public class ReportTasklet implements Tasklet {

    private static final Logger logger = LogManager.getLogger(ReportTasklet.class);

    @Autowired private BuContext           buContext;
    @Autowired private PipelineState       pipelineState;
    @Autowired private DirJobProperties    props;
    @Autowired private DirProcessingService processingService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        logger.info("============================================================");
        logger.info("{}:{} [REPORT] Generating batch report", buContext.buName,
                Thread.currentThread().getName());

        if ("True".equalsIgnoreCase(props.get("report"))) {
            try {
                String report = processingService.generateReport(buContext);
                if (!report.isBlank()) logger.info(report);
            } catch (Exception e) {
                logger.error("{}:{} [REPORT] Error generating report", buContext.buName,
                        Thread.currentThread().getName(), e);
            }
        }

        processingService.cleanUpAndClose(buContext);
        return RepeatStatus.FINISHED;
    }
}
