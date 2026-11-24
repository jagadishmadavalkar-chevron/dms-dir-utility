package com.dir.sync.service.impl;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.dir.sync.model.BapiContext;
import com.dir.sync.model.BuContext;
import com.dir.sync.model.DirOperation;
import com.dir.sync.model.DirRecord;
import com.dir.sync.model.PipelineState;
import com.dir.sync.repository.FlocRepository;
import com.dir.sync.repository.impl.ProcessingRepositoryImpl;
import com.dir.sync.service.AuthTokenService;
import com.dir.sync.service.SapApiService;
import com.dir.sync.strategy.BapiStrategyFactory;
import com.dir.sync.util.AppConstants;

@Service
public class SapApiServiceImpl implements SapApiService {

    private static final Logger logger = LogManager.getLogger(SapApiServiceImpl.class);

    @Autowired
    private AuthTokenService authTokenService;

    @Autowired
    private BapiStrategyFactory strategyFactory;

    @Autowired
    private ProcessingRepositoryImpl processingRepository;

    @Autowired
    private FlocRepository flocRepository;

    @Override
    public void processDirs(BuContext ctx, PipelineState state) throws Exception {
        ExecutorService esSap = Executors.newFixedThreadPool(5);
        logger.info("{}:{} [BAPI] Calling BAPIs", ctx.buName, Thread.currentThread().getName());
        logger.info("{}:{} [BAPI] Fetching auth token", ctx.buName, Thread.currentThread().getName());
        authTokenService.fetchAuthToken(ctx, state);

        final CountDownLatch[] latchHolder = { null };

        processingRepository.processReadyEntries(ctx, state, record -> {
            // Per-row FLOC alternate label enrichment (mirrors DatabaseFunctions.processDirs)
            JSONObject boIdsJson = record.getBoIdsJson();
            StringBuilder flocLabels = new StringBuilder();
            for (String suffix : new String[]{"FLOC_create", "FLOC_remove"}) {
                if (boIdsJson.has(suffix)) {
                    for (String floc : boIdsJson.getString(suffix).split(",")) {
                        if (!state.flocLabelToAlternate.containsKey(floc)) {
                            flocLabels.append(",'").append(floc).append("'");
                        }
                    }
                }
            }
            if (flocLabels.length() > 0) {
                String labels = flocLabels.substring(1);
                if (!labels.isEmpty()) {
                    flocRepository.getAlternateLabelsFromDb(labels, ctx, state.flocLabelToAlternate);
                }
            }
        });

        // Second pass: build latch and dispatch
        final int[] rowCount = {0};
        processingRepository.processReadyEntries(ctx, state, record -> rowCount[0]++);

        if (rowCount[0] == 0) {
            processingRepository.updateDbError(ctx, "NA", "No documents to process.",
                    AppConstants.completedStatus, null, "NA", null);
            esSap.shutdown();
            return;
        }

        CountDownLatch sapLatch = new CountDownLatch(rowCount[0]);

        processingRepository.processReadyEntries(ctx, state, record -> {
            BapiContext bapiCtx = new BapiContext(
                    record.getChronicleId(),
                    record.getBoIdsJson(),
                    record.getOperation(),
                    record.getSno(),
                    record.getDirNumber(),
                    record.getPlanningPlant(),
                    record.getTitle()
            );
            esSap.execute(() -> {
                try {
                    String token = authTokenService.checkAuthToken(state);
                    if (token.isEmpty()) {
                        logger.info("{}:{} [BAPI] Fetching new auth token", ctx.buName,
                                Thread.currentThread().getName());
                        token = authTokenService.fetchAuthToken(ctx, state);
                    }
                    if (token != null && !token.trim().isEmpty()) {
                        strategyFactory.getStrategy(bapiCtx.getOperation())
                                .execute(bapiCtx, ctx, state, token);
                    }
                } catch (Exception e) {
                    logger.error("{}:{} [BAPI] Unexpected error sno={}", ctx.buName,
                            Thread.currentThread().getName(), bapiCtx.getSno(), e);
                    processingRepository.updateDbPostBapi(ctx, bapiCtx.getSno(),
                            "Failed to call BAPI", AppConstants.errorStatus, null);
                } finally {
                    sapLatch.countDown();
                }
            });
        });

        sapLatch.await();
        logger.info("{}:{} [BAPI] No records left to process", ctx.buName,
                Thread.currentThread().getName());
        esSap.shutdown();
    }
}
