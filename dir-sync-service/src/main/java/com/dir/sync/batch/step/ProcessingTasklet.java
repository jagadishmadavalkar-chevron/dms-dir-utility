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
 * PROCESSING phase — mirrors the populateProcessing block in PrimaryThreadForBu.
 */
@Component
public class ProcessingTasklet implements Tasklet {

    private static final Logger logger = LogManager.getLogger(ProcessingTasklet.class);

    @Autowired private BuContext           buContext;
    @Autowired private PipelineState       pipelineState;
    @Autowired private DirJobProperties    props;
    @Autowired private DirProcessingService processingService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        if (!"True".equalsIgnoreCase(props.get("populateProcessing")) ||
                "True".equalsIgnoreCase(props.get("manualProcessFlag"))) {
            logger.info("{}:{} [PROCESS] Skipped by flag", buContext.buName, Thread.currentThread().getName());
            return RepeatStatus.FINISHED;
        }

        logger.info("============================================================");
        logger.info("{}:{} [PROCESS] Populating processing table", buContext.buName,
                Thread.currentThread().getName());
        processingService.populateProcessingTable(buContext, pipelineState);
        logger.info("{}:{} [PROCESS] Processing table populated", buContext.buName,
                Thread.currentThread().getName());
        logger.info("============================================================");
        return RepeatStatus.FINISHED;
    }
}
