package com.dir.sync.strategy;

import com.dir.sync.model.BapiContext;
import com.dir.sync.model.BuContext;
import com.dir.sync.model.PipelineState;

public interface BapiStrategy {

    void execute(BapiContext bapiCtx, BuContext ctx, PipelineState state, String token) throws Exception;
}
