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
import com.dir.sync.strategy.BapiStrategy;
import com.dir.sync.util.AppConstants;

@Component
public class DeleteDirStrategy implements BapiStrategy {

    private static final Logger logger = LogManager.getLogger(DeleteDirStrategy.class);

    @Autowired
    private ProcessingRepositoryImpl processingRepository;

    @Autowired
    private UpdateDirStrategy updateDirStrategy;

    @Override
    public void execute(BapiContext bapiCtx, BuContext ctx, PipelineState state, String token) throws Exception {
        // R2C: first remove object links via UPDATE (FetchBoIds), then DELETE
        JSONObject boIdsForRemove = processingRepository.fetchBoIds(ctx, bapiCtx.getChronicleId());
        BapiContext withBoIds = new BapiContext(
                bapiCtx.getChronicleId(), boIdsForRemove,
                bapiCtx.getOperation(), bapiCtx.getSno(),
                bapiCtx.getDirNumber(), bapiCtx.getPlanningPlant(), bapiCtx.getTitle());
        updateDirStrategy.callUpdateBapi(withBoIds, boIdsForRemove, ctx, state, token);
        callDeleteBapi(bapiCtx, ctx, token);
    }

    private void callDeleteBapi(BapiContext bapiCtx, BuContext ctx, String token) {
        String dirNumber   = bapiCtx.getDirNumber();
        int sno            = bapiCtx.getSno();
        String chronicleId = bapiCtx.getChronicleId();

        logger.info("{}:{} [BAPI] DELETE objectId={} plant={}", ctx.buName,
                Thread.currentThread().getName(), chronicleId, bapiCtx.getPlanningPlant());

        HttpClient client = HttpClient.newHttpClient();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ctx.endpoint))
                    .header("Content-Type", "application/json")
                    .header("APIKey", ctx.apiKey)
                    .header("Authorization", "Bearer " + token)
                    .header("CompanyCode", ctx.companyCode)
                    .header("MaintenancePlanningPlant", bapiCtx.getPlanningPlant())
                    .header("Accept", "application/json")
                    .header(AppConstants.sapDocTypeField,    ctx.docType)
                    .header(AppConstants.sapDocNumberField,  dirNumber)
                    .header(AppConstants.sapDocPartField,    ctx.docPart)
                    .header(AppConstants.sapDocVersionField, ctx.docVersion)
                    .method("DELETE", HttpRequest.BodyPublishers.ofString(""))
                    .build();

            // SECURITY: log only endpoint/plant/companyCode — never request.headers()
            logger.debug("{}:{} [BAPI] DELETE request → DELETE {} plant={} companyCode={} dirNumber={}",
                    ctx.buName, Thread.currentThread().getName(),
                    ctx.endpoint, bapiCtx.getPlanningPlant(), ctx.companyCode, dirNumber);

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            logger.debug("{}:{} [BAPI] DELETE response: {}", ctx.buName,
                    Thread.currentThread().getName(), response.body());

            JSONObject res = new JSONObject(response.body());
            String retMsg  = res.getJSONObject(AppConstants.deleteResponse)
                    .getJSONObject(AppConstants.ret).getString(AppConstants.message);

            if (retMsg.isEmpty()) {
                processingRepository.updateDbPostBapi(ctx, sno,
                        "Delete Flag Set Successfully.", AppConstants.completedStatus, dirNumber);
            } else {
                processingRepository.updateDbPostBapi(ctx, sno, retMsg,
                        AppConstants.errorStatus, dirNumber);
            }
        } catch (Exception e) {
            processingRepository.updateDbPostBapi(ctx, sno,
                    "DIR Deletion Failed. Error: " + e.getMessage(),
                    AppConstants.errorStatus, dirNumber);
            logger.error("{}:{} [BAPI] DELETE failed for dirNumber={}", ctx.buName,
                    Thread.currentThread().getName(), dirNumber, e);
        }
    }
}
