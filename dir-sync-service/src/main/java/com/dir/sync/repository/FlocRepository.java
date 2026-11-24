package com.dir.sync.repository;

import java.util.List;
import java.util.Map;

import com.dir.sync.model.BuContext;

public interface FlocRepository {

    void upsertFlocAlternateLabels(BuContext ctx, List<Map<String, String>> rows);

    void getAlternateLabelsFromDb(String flocLabels, BuContext ctx,
                                  Map<String, String> flocLabelToAlternate);
}
