package com.dir.sync.repository;

import com.dir.sync.model.BuContext;
import com.dir.sync.model.PipelineState;

public interface StagingRepository {

    void populateStagingTable(BuContext ctx, PipelineState state) throws Exception;

    void findDeltas(BuContext ctx, PipelineState state);

    void cleanUpStagingTable(BuContext ctx);
}
