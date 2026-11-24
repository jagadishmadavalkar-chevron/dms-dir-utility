package com.dir.sync.service;

import com.dir.sync.model.BuContext;
import com.dir.sync.model.PipelineState;

public interface DirProcessingService {

    void populateStagingTable(BuContext ctx, PipelineState state) throws Exception;

    void findDeltas(BuContext ctx, PipelineState state);

    void populateProcessingTable(BuContext ctx, PipelineState state);

    void updateDeletedDocRows(BuContext ctx);

    void cleanUpAndClose(BuContext ctx);

    String generateReport(BuContext ctx) throws Exception;
}
