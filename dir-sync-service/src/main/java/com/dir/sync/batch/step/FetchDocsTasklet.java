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
import com.dir.sync.repository.impl.ProcessingRepositoryImpl;
import com.dir.sync.service.DocumentumService;

/**
 * FETCH phase — mirrors the pullDirectory block in PrimaryThreadForBu.
 */
@Component
public class FetchDocsTasklet implements Tasklet {

    private static final Logger logger = LogManager.getLogger(FetchDocsTasklet.class);

    @Autowired private BuContext           buContext;
    @Autowired private PipelineState       pipelineState;
    @Autowired private DirJobProperties    props;
    @Autowired private DocumentumService   documentumService;
    @Autowired private ProcessingRepositoryImpl processingRepository;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        if (!"True".equalsIgnoreCase(props.get("pullDirectory"))) {
            logger.info("{}:{} [FETCH] Skipped by flag", buContext.buName, Thread.currentThread().getName());
            return RepeatStatus.FINISHED;
        }

        logger.info("============================================================");
        logger.info("{}:{} [FETCH] Pulling directory", buContext.buName, Thread.currentThread().getName());

        logger.info("{}:{} [DCTM] Updating failed documents from previous executions",
                buContext.buName, Thread.currentThread().getName());
        documentumService.addDirNumberWhichFailed(buContext, pipelineState);

        logger.info("{}:{} [CLEANUP] Updating deleted documents from previous executions",
                buContext.buName, Thread.currentThread().getName());
        processingRepository.updateDeletedDocRows(buContext);

        logger.info("{}:{} [FLOC] Fetching FLOC alternate labels from D2",
                buContext.buName, Thread.currentThread().getName());
        documentumService.getAlternateLabelsFromD2(buContext, pipelineState);
        logger.debug("{}:{} [FLOC] flocLabelToAlternate after D2 fetch: {}",
                buContext.buName, Thread.currentThread().getName(), pipelineState.flocLabelToAlternate);

        logger.info("{}:{} [FETCH] Fetching documents from D2",
                buContext.buName, Thread.currentThread().getName());
        logger.info("============================================================");

        if ("True".equalsIgnoreCase(props.get("manualProcessFlag"))) {
            logger.info("{}:{} [MANUAL PROCESSING] Processing Manually Updated records",
                    buContext.buName, Thread.currentThread().getName());
            processingRepository.fetchManualReadytoprocessEntries(buContext, pipelineState);
            // Documents resolved by DocumentumService
            String sql = pipelineState.messages.remove("__manualSql__");
            if (sql != null) {
                try (java.sql.PreparedStatement stmt = buContext.conn.prepareStatement(sql);
                     java.sql.ResultSet rs = stmt.executeQuery()) {
                    StringBuilder docs = new StringBuilder();
                    while (rs.next()) docs.append(",'").append(rs.getString("r_object_id")).append("'");
                    if (docs.length() > 0) {
                        documentumService.finaliseCutoverInput(docs.substring(1), buContext, pipelineState);
                    }
                }
            }
        } else if ("True".equalsIgnoreCase(props.get("cutOverFlag"))) {
            logger.info("============================================================");
            logger.info("{}:{} [CUTOVERFLAG] {}", buContext.buName, Thread.currentThread().getName(), true);
            processingRepository.fetchDocuments(buContext, pipelineState);
            String sql = pipelineState.messages.remove("__cutoverSql__");
            if (sql != null) {
                try (java.sql.PreparedStatement stmt = buContext.conn.prepareStatement(sql);
                     java.sql.ResultSet rs = stmt.executeQuery()) {
                    StringBuilder docs = new StringBuilder();
                    while (rs.next()) {
                        documentumService.executeRelatedTags(buContext, pipelineState, rs.getString("r_object_id"));
                        docs.append(",'").append(rs.getString("r_object_id")).append("'");
                    }
                    if (docs.length() > 0) {
                        documentumService.finaliseCutoverInput(docs.substring(1), buContext, pipelineState);
                    }
                }
            }
        } else {
            logger.info("============================================================");
            logger.info("{}:{} [CUTOVERFLAG] false", buContext.buName, Thread.currentThread().getName());
            documentumService.updateParentTags(buContext, pipelineState);
            documentumService.checkInputDocuments(buContext, pipelineState);
            documentumService.checkDeleteDocuments(buContext, pipelineState);
        }

        logger.info("{}:{} [FETCH] Documents fetched", buContext.buName, Thread.currentThread().getName());
        logger.info("============================================================");
        return RepeatStatus.FINISHED;
    }
}
