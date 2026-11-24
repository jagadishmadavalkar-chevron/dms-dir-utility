package com.dir.sync.strategy.impl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.dir.sync.model.BapiContext;
import com.dir.sync.model.BuContext;
import com.dir.sync.model.PipelineState;
import com.dir.sync.repository.impl.ProcessingRepositoryImpl;
import com.dir.sync.service.DocumentumService;
import com.dir.sync.strategy.BapiStrategy;
import com.dir.sync.util.AppConstants;
import com.dir.sync.util.PayloadBuilder;

@Component
public class UpdateDirStrategy implements BapiStrategy {

    private static final Logger logger = LogManager.getLogger(UpdateDirStrategy.class);

    @Autowired
    private ProcessingRepositoryImpl processingRepository;

    @Autowired
    private DocumentumService documentumService;

    @Override
    public void execute(BapiContext bapiCtx, BuContext ctx, PipelineState state, String token) throws Exception {
        String chronicleId = bapiCtx.getChronicleId();
        JSONObject boIdsJson = bapiCtx.getBoIdsJson();
        int sno = bapiCtx.getSno();

        if (boIdsJson.toString().equals("{}")) {
            processingRepository.updateDbPostBapi(ctx, sno,
                    "No object links, DIR update not necessary.", AppConstants.completedStatus,
                    bapiCtx.getDirNumber());
            return;
        }

        callUpdateBapi(bapiCtx, boIdsJson, ctx, state, token);
    }

    public void callUpdateBapi(BapiContext bapiCtx, JSONObject boIdsJson,
                                BuContext ctx, PipelineState state, String token) {
        String chronicleId = bapiCtx.getChronicleId();
        String dirNumber   = bapiCtx.getDirNumber();
        int sno            = bapiCtx.getSno();

        logger.info("{}:{} [BAPI] UPDATE objectId={} plant={}", ctx.buName,
                Thread.currentThread().getName(), chronicleId, bapiCtx.getPlanningPlant());

        HttpClient client = HttpClient.newHttpClient();
        try {
            String payload = PayloadBuilder.buildUpdatePayload(boIdsJson, chronicleId, ctx,
                    dirNumber, state.flocLabelToAlternate);
            logger.debug("{}:{} [BAPI] UPDATE payload: {}", ctx.buName,
                    Thread.currentThread().getName(), payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ctx.endpoint))
                    .header("Content-Type", "application/json")
                    .header("APIKey", ctx.apiKey)
                    .header("Authorization", "Bearer " + token)
                    .header("CompanyCode", ctx.companyCode)
                    .header("MaintenancePlanningPlant", bapiCtx.getPlanningPlant())
                    .header("Accept", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            // SECURITY: log only endpoint/plant/companyCode — never request.headers()
            logger.debug("{}:{} [BAPI] UPDATE request → PATCH {} plant={} companyCode={}",
                    ctx.buName, Thread.currentThread().getName(),
                    ctx.endpoint, bapiCtx.getPlanningPlant(), ctx.companyCode);

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            logger.debug("{}:{} [BAPI] UPDATE response: {}", ctx.buName,
                    Thread.currentThread().getName(), response.body());

            JSONObject res = new JSONObject(response.body());

            if (res.getJSONObject(AppConstants.updateResponse)
                    .getJSONObject(AppConstants.ret).get(AppConstants.message).toString().isEmpty()) {
                String versionId = state.docsWithNewVersion.get(chronicleId).keySet().iterator().next();
                if (state.docsWithDir.get(chronicleId)
                        .equals(state.docsWithNewVersion.get(chronicleId).get(versionId))) {
                    processingRepository.updateDbPostBapi(ctx, sno,
                            state.messages.getOrDefault(chronicleId, "") + "DIR Updated Successfully.",
                            AppConstants.completedStatus, dirNumber);
                } else {
                    processingRepository.updateDbPostBapi(ctx, sno,
                            "DIR Successfully Updated, but Document DIR number not updated.",
                            AppConstants.partCompletedStatus, dirNumber);
                    documentumService.addDirNumber(dirNumber, versionId, ctx, sno,
                            state.messages.getOrDefault(chronicleId, "") +
                                    "DIR successfully updated and Document updated.");
                }
            } else {
                processingRepository.updateDbPostBapi(ctx, sno,
                        state.messages.getOrDefault(chronicleId, "") + "Error: " +
                                res.getJSONObject(AppConstants.updateResponse)
                                        .getJSONObject(AppConstants.ret).getString(AppConstants.message),
                        AppConstants.errorStatus, dirNumber);
            }
        } catch (Exception e) {
            processingRepository.updateDbPostBapi(ctx, sno,
                    state.messages.getOrDefault(chronicleId, "") + "DIR Update Failed. Error: " + e.getMessage(),
                    AppConstants.errorStatus, dirNumber);
            logger.error("{}:{} [BAPI] UPDATE failed for objectId={}", ctx.buName,
                    Thread.currentThread().getName(), chronicleId, e);
        }
    }
}
