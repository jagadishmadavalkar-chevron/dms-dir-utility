package com.dir.sync.service;

import com.dir.sync.model.BuContext;
import com.dir.sync.model.PipelineState;

public interface DocumentumService {

    void updateParentTags(BuContext ctx, PipelineState state);

    void checkInputDocuments(BuContext ctx, PipelineState state) throws Exception;

    void checkDeleteDocuments(BuContext ctx, PipelineState state) throws Exception;

    void fetchPlanningPlants(BuContext ctx, PipelineState state, String operationType);

    void fetchPlanningPlantsDeleted(BuContext ctx, PipelineState state);

    void addDirNumber(String dirNumber, String versionId, BuContext ctx, int sno, String message);

    void addDirNumberWhichFailed(BuContext ctx, PipelineState state);

    void finaliseCutoverInput(String documents, BuContext ctx, PipelineState state) throws Exception;

    void getFlocAlternateLabels(BuContext ctx, PipelineState state);

    void getAlternateLabelsFromD2(BuContext ctx, PipelineState state);

    void executeRelatedTags(BuContext ctx, PipelineState state, String rObjectId);
}
