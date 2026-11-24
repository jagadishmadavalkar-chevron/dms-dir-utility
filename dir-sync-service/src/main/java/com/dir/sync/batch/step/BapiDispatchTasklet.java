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
import com.dir.sync.service.DocumentumService;
import com.dir.sync.service.SapApiService;

/**
 * BAPI phase — mirrors the callBapis block in PrimaryThreadForBu.
 */
@Component
public class BapiDispatchTasklet implements Tasklet {

    private static final Logger logger = LogManager.getLogger(BapiDispatchTasklet.class);

    @Autowired private BuContext        buContext;
    @Autowired private PipelineState    pipelineState;
    @Autowired private DirJobProperties props;
    @Autowired private SapApiService    sapApiService;
    @Autowired private DocumentumService documentumService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        if (!"True".equalsIgnoreCase(props.get("callBapis"))) {
            logger.info("{}:{} [BAPI] Skipped by flag", buContext.buName, Thread.currentThread().getName());
            return RepeatStatus.FINISHED;
        }

        logger.info("============================================================");
        logger.info("{}:{} [BAPI] Calling SAP API endpoints", buContext.buName,
                Thread.currentThread().getName());

        logger.info("{}:{} [FLOC] Fetching FLOC alternate labels for BAPI",
                buContext.buName, Thread.currentThread().getName());
        documentumService.getFlocAlternateLabels(buContext, pipelineState);
        logger.info("{}:{} [FLOC] Fetched FLOC alternate labels for BAPI",
                buContext.buName, Thread.currentThread().getName());
        logger.debug("{}:{} [FLOC] flocLabelToAlternate before BAPI: {}",
                buContext.buName, Thread.currentThread().getName(), pipelineState.flocLabelToAlternate);

        sapApiService.processDirs(buContext, pipelineState);

        logger.info("{}:{} [BAPI] DIRs processed successfully", buContext.buName,
                Thread.currentThread().getName());
        logger.info("============================================================");
        return RepeatStatus.FINISHED;
    }
}
