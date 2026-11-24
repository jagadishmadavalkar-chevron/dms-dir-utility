package com.dir.sync.repository;

import org.json.JSONObject;

import com.dir.sync.model.BuContext;
import com.dir.sync.model.PipelineState;

public interface ProcessingRepository {

    int    getMaxBatchValue(String processingTable, String maxBatchSql, String buName) throws Exception;

    String getDateOfLastRun(String processingTable, String lastRunDateSql, String buName) throws Exception;

    void   populateProcessingTable(BuContext ctx, PipelineState state);

    void   updateDbPostBapi(BuContext ctx, int sno, String message, String status, String dirNumber);

    void   updateDbError(BuContext ctx, String chronicleId, String message, String status,
                         String boIds, String operation, String dirNumber);

    void   updateDeletedDocRows(BuContext ctx);

    void   updateProcessToError(BuContext ctx);

    void   fetchManualReadytoprocessEntries(BuContext ctx, PipelineState state);

    void   fetchDocuments(BuContext ctx, PipelineState state);

    JSONObject fetchBoIds(BuContext ctx, String chronicleId);

    void   processReadyEntries(BuContext ctx, PipelineState state,
                               ProcessingRowConsumer rowConsumer) throws Exception;
}
