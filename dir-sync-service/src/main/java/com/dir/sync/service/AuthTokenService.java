package com.dir.sync.service;

import com.dir.sync.model.BuContext;
import com.dir.sync.model.PipelineState;

public interface AuthTokenService {

    /** Fetches a fresh auth token from APIM and updates state.authToken. Returns the access_token string. */
    String fetchAuthToken(BuContext ctx, PipelineState state);

    /** Validates the current token; returns the access_token if still valid (>300 s remaining), else empty string. */
    String checkAuthToken(PipelineState state);
}
