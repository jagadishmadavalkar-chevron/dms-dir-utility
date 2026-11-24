package com.dir.sync.service;

import com.dir.sync.model.BuContext;
import com.dir.sync.model.PipelineState;

public interface SapApiService {

    /** Dispatches all readytoprocess entries via ExecutorService + CountDownLatch — mirrors processDirs(). */
    void processDirs(BuContext ctx, PipelineState state) throws Exception;
}
