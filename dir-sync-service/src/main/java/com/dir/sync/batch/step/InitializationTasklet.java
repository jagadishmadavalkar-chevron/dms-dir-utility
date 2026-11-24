package com.dir.sync.batch.step;

import java.sql.DriverManager;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.dir.sync.config.DirJobProperties;
import com.dir.sync.config.DocumentumConfig;
import com.dir.sync.model.BuContext;
import com.dir.sync.model.PipelineState;
import com.dir.sync.repository.impl.ProcessingRepositoryImpl;
import com.dir.sync.util.Decryptor;

/**
 * First step — mirrors PrimaryThreadForBu.executeForBu() initialization block.
 * Decrypts credentials, opens the DB connection, resolves batchNo/dateOfLastRun,
 * and initialises BuContext and PipelineState for the run.
 */
@Component
public class InitializationTasklet implements Tasklet {

    private static final Logger logger = LogManager.getLogger(InitializationTasklet.class);

    @Autowired private BuContext              buContext;
    @Autowired private PipelineState          pipelineState;
    @Autowired private DirJobProperties       props;
    @Autowired private DocumentumConfig       documentumConfig;
    @Autowired private Decryptor              decryptor;
    @Autowired private ProcessingRepositoryImpl processingRepository;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        String buName  = documentumConfig.getBuName();
        String docBase = documentumConfig.getDocBase();

        logger.info("{}:{} [INIT] Extracting and decrypting credentials", buName,
                Thread.currentThread().getName());

        String dBPassword  = decryptor.decrypt(props.get("dbPassword"));
        String apimPassword = decryptor.decrypt(props.get("APIMPassword"));
        String clientSecret = decryptor.decrypt(props.get("clientSecret"));
        String apiKey       = decryptor.decrypt(props.get("APIKey"));

        logger.info("{}:{} [INIT] Credentials decrypted successfully", buName,
                Thread.currentThread().getName());
        logger.info("{}:{} [INIT] Database connection starting", buName,
                Thread.currentThread().getName());

        // Open DB connection — stored in BuContext for the duration of this run
        buContext.conn = DriverManager.getConnection(props.get("jdbcURL"), props.get("dBUsername"), dBPassword);

        logger.info("{}:{} [INIT] Database connection established", buName,
                Thread.currentThread().getName());

        // Resolve batchNo and dateOfLastRun
        int    batchNo       = processingRepository.getMaxBatchValue(buContext, props.get("maxBatchNumber"));
        String dateOfLastRun = processingRepository.getDateOfLastRun(buContext, props.get("lastRunDate"));

        // Initialise BuContext from DirJobProperties + decrypted credentials.
        // conn was already opened above; initialize() does not touch it.
        buContext.initialize(props, buName, docBase, batchNo, dateOfLastRun,
                dBPassword, apimPassword, clientSecret, apiKey);

        pipelineState.initialize();

        // Apply debugMode (mirrors DIRBulkUpdate.main() logic)
        if ("True".equalsIgnoreCase(props.get("debugMode"))) {
            Configurator.setLevel("com.dir.sync", Level.DEBUG);
            logger.info("[INIT] DEBUG mode enabled");
        }

        // IDfSession is held by DocumentumConfig — services access it via documentumConfig.getSession()

        logger.info("{}:{} [INIT] BuContext initialised — batchNo={} dateOfLastRun={}",
                buName, Thread.currentThread().getName(), batchNo, dateOfLastRun);

        return RepeatStatus.FINISHED;
    }
}
