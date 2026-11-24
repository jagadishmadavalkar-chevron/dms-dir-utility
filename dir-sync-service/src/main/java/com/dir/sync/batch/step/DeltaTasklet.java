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
 * DELTA phase — mirrors the compareAndFindDeltas block in PrimaryThreadForBu.
 */
@Component
public class DeltaTasklet implements Tasklet {

    private static final Logger logger = LogManager.getLogger(DeltaTasklet.class);

    @Autowired private BuContext           buContext;
    @Autowired private PipelineState       pipelineState;
    @Autowired private DirJobProperties    props;
    @Autowired private DirProcessingService processingService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        if (!"True".equalsIgnoreCase(props.get("compareAndFindDeltas"))) {
            logger.info("{}:{} [DELTA] Skipped by flag", buContext.buName, Thread.currentThread().getName());
            return RepeatStatus.FINISHED;
        }

        logger.info("============================================================");
        logger.info("{}:{} [DELTA] Finding deltas", buContext.buName, Thread.currentThread().getName());
        processingService.findDeltas(buContext, pipelineState);
        logger.info("{}:{} [DELTA] Deltas found", buContext.buName, Thread.currentThread().getName());
        logger.info("============================================================");

        // Count summary (mirrors PrimaryThreadForBu lines 251-263)
        logger.info("============================================================");
        logger.info("{}:{} [DELTA] DIRs to create={}", buContext.buName,
                Thread.currentThread().getName(), pipelineState.buMap.length());
        logger.info("{}:{} [DELTA] DIRs to update={}", buContext.buName,
                Thread.currentThread().getName(), pipelineState.buMapForUpdate.length());
        logger.info("{}:{} [DELTA] DIRs to delete={}", buContext.buName,
                Thread.currentThread().getName(), pipelineState.buMapForDelete.size());
        logger.debug("{}:{} [DELTA] buMap contents: {}", buContext.buName,
                Thread.currentThread().getName(), pipelineState.buMap);
        logger.debug("{}:{} [DELTA] buMapForUpdate contents: {}", buContext.buName,
                Thread.currentThread().getName(), pipelineState.buMapForUpdate);
        logger.debug("{}:{} [DELTA] buMapForDelete contents: {}", buContext.buName,
                Thread.currentThread().getName(), pipelineState.buMapForDelete);

        return RepeatStatus.FINISHED;
    }
}
