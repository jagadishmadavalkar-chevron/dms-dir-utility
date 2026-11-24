package com.dir.sync.service.impl;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.dir.sync.model.BuContext;
import com.dir.sync.model.PipelineState;
import com.dir.sync.repository.ProcessingRepository;
import com.dir.sync.repository.impl.ProcessingRepositoryImpl;
import com.dir.sync.service.AuthTokenService;
import com.dir.sync.util.AppConstants;

@Service
public class AuthTokenServiceImpl implements AuthTokenService {

    private static final Logger logger = LogManager.getLogger(AuthTokenServiceImpl.class);

    @Autowired
    private ProcessingRepositoryImpl processingRepository;

    @Override
    public synchronized String fetchAuthToken(BuContext ctx, PipelineState state) {
        try {
            String encodedPassword = URLEncoder.encode(ctx.apimPassword, StandardCharsets.UTF_8.toString());
            String body = String.format(AppConstants.authTokenCallPayload,
                    ctx.clientId, ctx.clientSecret, ctx.resource, ctx.apimUsername, encodedPassword);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ctx.authEndpoint))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .method("GET", HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = ctx.clientAuth.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject res = new JSONObject(response.body());
            state.authToken = res;
            logger.info("{}:{} [BAPI] Auth token acquired", ctx.buName, Thread.currentThread().getName());
            return res.getString("access_token");
        } catch (Exception e) {
            if (state.authTokenFlag.compareAndSet(false, true)) {
                processingRepository.updateDbError(ctx, "NA", "Could not fetch Auth Token",
                        AppConstants.errorStatus, null, "NA", null);
                processingRepository.updateProcessToError(ctx);
            }
            JSONObject authError = new JSONObject();
            authError.put("expires_in", "1");
            state.authToken = authError;
            logger.error("{}:{} [BAPI] Could not fetch auth token", ctx.buName,
                    Thread.currentThread().getName(), e);
            return "";
        }
    }

    @Override
    public String checkAuthToken(PipelineState state) {
        try {
            JSONObject authToken = state.authToken;
            if (authToken == null || !authToken.has("access_token") || !authToken.has("expires_on")) {
                return "";
            }
            long current   = Instant.now().getEpochSecond();
            long expiry    = Long.parseLong(authToken.getString("expires_on"));
            long remaining = expiry - current;
            logger.debug("[BAPI] Token remaining validity: {}s", remaining);
            if (remaining > 300) {
                return authToken.getString("access_token");
            }
        } catch (Exception e) {
            logger.error("[BAPI] Error checking auth token validity", e);
        }
        return "";
    }
}
