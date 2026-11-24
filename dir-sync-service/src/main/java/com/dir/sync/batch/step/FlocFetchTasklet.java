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

/**
 * PLANTS phase — mirrors the plant retrieval and FLOC fetch in PrimaryThreadForBu
 * (lines 265-301).
 */
@Component
public class FlocFetchTasklet implements Tasklet {

    private static final Logger logger = LogManager.getLogger(FlocFetchTasklet.class);

    @Autowired private BuContext        buContext;
    @Autowired private PipelineState    pipelineState;
    @Autowired private DirJobProperties props;
    @Autowired private DocumentumService documentumService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        logger.info("============================================================");

        boolean isManual = "True".equalsIgnoreCase(props.get("manualProcessFlag"));

        if (!isManual) {
            logger.info("{}:{} [PLANTS] Retrieving planning plants for CREATE",
                    buContext.buName, Thread.currentThread().getName());
            if (!pipelineState.buMap.isEmpty()) {
                documentumService.fetchPlanningPlants(buContext, pipelineState, "CREATE");
            } else {
                logger.info("{}:{} [PLANTS] No documents for CREATE",
                        buContext.buName, Thread.currentThread().getName());
            }

            logger.info("{}:{} [PLANTS] Retrieving planning plants for UPDATE",
                    buContext.buName, Thread.currentThread().getName());
            if (!pipelineState.buMapForUpdate.isEmpty()) {
                documentumService.fetchPlanningPlants(buContext, pipelineState, "UPDATE");
            } else {
                logger.info("{}:{} [PLANTS] No documents for UPDATE",
                        buContext.buName, Thread.currentThread().getName());
            }

            logger.info("{}:{} [PLANTS] Retrieving planning plants for DELETE",
                    buContext.buName, Thread.currentThread().getName());
            if (!pipelineState.buMapForDelete.isEmpty()) {
                for (String docId : pipelineState.buMapForDelete.keySet()) {
                    pipelineState.buMapForDeleteWithPlants.put(docId, "");
                }
                documentumService.fetchPlanningPlantsDeleted(buContext, pipelineState);
            } else {
                logger.info("{}:{} [PLANTS] No documents for DELETE",
                        buContext.buName, Thread.currentThread().getName());
            }
        } else {
            logger.info("{}:{} [MANUAL PROCESSING] Skipping plant fetch due to manual re-processing",
                    buContext.buName, Thread.currentThread().getName());
        }

        logger.info("============================================================");
        return RepeatStatus.FINISHED;
    }
}
