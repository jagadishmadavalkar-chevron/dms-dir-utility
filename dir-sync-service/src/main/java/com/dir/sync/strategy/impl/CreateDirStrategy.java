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
public class CreateDirStrategy implements BapiStrategy {

    private static final Logger logger = LogManager.getLogger(CreateDirStrategy.class);

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
                    "No object links, DIR not necessary.", AppConstants.completedStatus, null);
            return;
        }

        logger.info("{}:{} [BAPI] CREATE objectId={} plant={}", ctx.buName,
                Thread.currentThread().getName(), chronicleId, bapiCtx.getPlanningPlant());

        HttpClient client = HttpClient.newHttpClient();
        try {
            String payload = PayloadBuilder.buildPayload(boIdsJson, chronicleId, ctx,
                    bapiCtx.getTitle(), state.flocLabelToAlternate);
            logger.debug("{}:{} [BAPI] CREATE payload: {}", ctx.buName,
                    Thread.currentThread().getName(), payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ctx.endpoint))
                    .header("Content-Type", "application/json")
                    .header("APIKey", ctx.apiKey)
                    .header("Authorization", "Bearer " + token)
                    .header("CompanyCode", ctx.companyCode)
                    .header("MaintenancePlanningPlant", bapiCtx.getPlanningPlant())
                    .header("Accept", "application/json")
                    .method("POST", HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            // SECURITY: log only endpoint/plant/companyCode — never request.headers()
            logger.debug("{}:{} [BAPI] CREATE request → POST {} plant={} companyCode={}",
                    ctx.buName, Thread.currentThread().getName(),
                    ctx.endpoint, bapiCtx.getPlanningPlant(), ctx.companyCode);

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            logger.debug("{}:{} [BAPI] CREATE response: {}", ctx.buName,
                    Thread.currentThread().getName(), response.body());

            JSONObject res = new JSONObject(response.body());

            if (res.has("error")) {
                JSONObject error = res.getJSONObject("error");
                String message = error.getJSONObject("message").optString("value", "");
                String innerMsg = "";
                if (error.has("innererror")) {
                    JSONObject inner = error.getJSONObject("innererror");
                    if (inner.has("errordetails")) {
                        innerMsg = inner.getJSONArray("errordetails").optJSONObject(0).optString("message", "");
                    }
                }
                processingRepository.updateDbPostBapi(ctx, sno,
                        state.messages.getOrDefault(chronicleId, "") + "Error: " + message + " | " + innerMsg,
                        AppConstants.errorStatus, null);
            } else {
                JSONObject createResp = res.getJSONObject(AppConstants.createResponse);
                if (createResp.getJSONObject(AppConstants.ret).get(AppConstants.message).toString().isEmpty()) {
                    String dirNumber = createResp.getString(AppConstants.dirNumberResponse);
                    processingRepository.updateDbPostBapi(ctx, sno, "DIR Successfully Created",
                            AppConstants.partCompletedStatus, dirNumber);
                    String versionId = state.docsWithNewVersion.get(chronicleId).keySet().iterator().next();
                    documentumService.addDirNumber(dirNumber, versionId, ctx, sno,
                            state.messages.getOrDefault(chronicleId, "") + "DIR successfully created and Document updated.");
                } else {
                    processingRepository.updateDbPostBapi(ctx, sno,
                            state.messages.getOrDefault(chronicleId, "") + "Error: " +
                                    createResp.getJSONObject(AppConstants.ret).get(AppConstants.message),
                            AppConstants.errorStatus, null);
                }
            }
        } catch (Exception e) {
            processingRepository.updateDbPostBapi(ctx, sno,
                    state.messages.getOrDefault(chronicleId, "") + "DIR Creation Failed. Error: " + e.getMessage(),
                    AppConstants.errorStatus, null);
            logger.error("{}:{} [BAPI] CREATE failed for objectId={}", ctx.buName,
                    Thread.currentThread().getName(), chronicleId, e);
        }
    }
}
