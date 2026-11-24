package com.dir.sync.service.impl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.dir.sync.config.DirJobProperties;
import com.dir.sync.model.BuContext;
import com.dir.sync.model.PipelineState;
import com.dir.sync.repository.FlocRepository;
import com.dir.sync.repository.impl.ProcessingRepositoryImpl;
import com.dir.sync.service.DocumentumService;
import com.dir.sync.util.AppConstants;
import com.documentum.fc.client.DfQuery;
import com.documentum.fc.client.IDfCollection;
import com.documentum.fc.client.IDfQuery;
import com.documentum.fc.client.IDfSession;
import com.documentum.fc.client.IDfSysObject;

@Service
public class DocumentumServiceImpl implements DocumentumService {

    private static final Logger logger = LogManager.getLogger(DocumentumServiceImpl.class);

    @Autowired
    private DirJobProperties props;

    @Autowired
    private ProcessingRepositoryImpl processingRepository;

    @Autowired
    private FlocRepository flocRepository;

    @Override
    public void updateParentTags(BuContext ctx, PipelineState state) {
        IDfQuery query = new DfQuery();
        String dql = String.format(ctx.relatedTagsDql,
                props.get("type"), ctx.dateOfLastRun,
                props.get("statusForDir"), props.get("doctype"), props.get("documentTypeToSkip"));
        logger.debug("{}:{} [DCTM] relatedTagsDql: {}", ctx.buName, Thread.currentThread().getName(), dql);
        query.setDQL(dql);
        IDfCollection collection = null;
        try {
            collection = query.execute(ctx.session, IDfQuery.DF_READ_QUERY);
            if (collection == null) {
                logger.warn("{}:{} [DCTM] No documents in D2 to process", ctx.buName,
                        Thread.currentThread().getName());
            } else {
                while (collection.next()) {
                    executeRelatedTags(ctx, state, collection.getString(AppConstants.dbDocId));
                }
            }
        } catch (Exception e) {
            logger.error("{}:{} [DCTM] Error in updateParentTags", ctx.buName,
                    Thread.currentThread().getName(), e);
        } finally {
            try { if (collection != null) collection.close(); } catch (Exception ignored) {}
        }
    }

    @Override
    public void checkInputDocuments(BuContext ctx, PipelineState state) throws Exception {
        IDfQuery query = new DfQuery();
        String fields = props.get("inputAttrsr2") + "," + AppConstants.d2DirField;
        String dql = String.format(ctx.dql, fields,
                props.get("type"), ctx.dateOfLastRun,
                props.get("statusForDir"), props.get("doctype"), props.get("documentTypeToSkip"));
        logger.debug("{}:{} [DCTM] checkInputDocuments DQL: {}", ctx.buName,
                Thread.currentThread().getName(), dql);
        query.setDQL(dql);
        IDfCollection collection = null;
        try {
            collection = query.execute(ctx.session, IDfQuery.DF_READ_QUERY);
            if (collection == null) {
                processingRepository.updateDbError(ctx, "NA", "No documents in D2 to process.",
                        AppConstants.completedStatus, null, "NA", null);
                logger.warn("{}:{} [DCTM] No documents in D2 to process", ctx.buName,
                        Thread.currentThread().getName());
                return;
            }
            while (collection.next()) {
                Map<String, String> input = new HashMap<>();
                Map<String, String> dirMap = new HashMap<>();

                String[] tagFields = { props.get("FLOCField"), props.get("TLSTField"),
                        props.get("IPLNField"), props.get("WRDRField"), props.get("EQUIField") };
                for (String field : tagFields) {
                    int valueCount = collection.getValueCount(field);
                    if (valueCount != 0 && !collection.getRepeatingString(field, 0).trim().isEmpty()) {
                        String[] ids = new String[valueCount];
                        for (int i = 0; i < valueCount; i++) {
                            ids[i] = collection.getRepeatingString(field, i);
                        }
                        input.put(ctx.attrIndex.get(field), StringUtils.join(ids, ","));
                    }
                }

                String chronicleId = collection.getString(AppConstants.d2ChronicleId);
                state.buMap.put(chronicleId, input);
                state.buMapWithPlants.put(chronicleId, "");

                String dirNum = collection.getString(AppConstants.d2DirField);
                dirMap.put(collection.getString(AppConstants.dbDocId),
                        (dirNum == null || dirNum.isEmpty()) ? "" : dirNum);
                state.docsWithNewVersion.put(chronicleId, dirMap);
                state.titles.put(chronicleId, collection.getString(AppConstants.d2TitleField));

                logger.debug("{}:{} [DCTM] chronicleId={} title={} dirNum={} tags={}",
                        ctx.buName, Thread.currentThread().getName(),
                        chronicleId, collection.getString(AppConstants.d2TitleField), dirNum, input);
            }
        } catch (Exception e) {
            logger.error("{}:{} [DCTM] Error in checkInputDocuments", ctx.buName,
                    Thread.currentThread().getName(), e);
            processingRepository.updateDbError(ctx, "NA",
                    "Could not fetch documents. " + e.getMessage(),
                    AppConstants.errorStatus, null, "NA", null);
        } finally {
            try { if (collection != null) collection.close(); } catch (Exception ignored) {}
        }
    }

    @Override
    public void checkDeleteDocuments(BuContext ctx, PipelineState state) throws Exception {
        IDfQuery auditQuery = new DfQuery();
        auditQuery.setDQL(ctx.deleteTrailDql);
        IDfCollection auditCollection = null;
        try {
            auditCollection = auditQuery.execute(ctx.session, IDfQuery.DF_READ_QUERY);
            if (auditCollection == null) return;
            while (auditCollection.next()) {
                String chronicleId = auditCollection.getString(AppConstants.d2ChronicleId);
                IDfQuery dontDeleteQuery = new DfQuery();
                dontDeleteQuery.setDQL(String.format(ctx.deleteDql, chronicleId));
                IDfCollection dontDeleteCollection = dontDeleteQuery.execute(ctx.session, IDfQuery.DF_READ_QUERY);
                if (dontDeleteCollection == null || !dontDeleteCollection.next()) {
                    PreparedStatement stmt = ctx.conn.prepareStatement(
                            String.format(ctx.deleteSql, ctx.processingTable, ctx.buName));
                    stmt.setString(1, chronicleId);
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        state.buMapForDelete.put(chronicleId, rs.getString(AppConstants.dbDirNumberCol));
                    }
                }
                try { if (dontDeleteCollection != null) dontDeleteCollection.close(); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            logger.error("{}:{} [DCTM] Error in checkDeleteDocuments", ctx.buName,
                    Thread.currentThread().getName(), e);
        } finally {
            try { if (auditCollection != null) auditCollection.close(); } catch (Exception ignored) {}
        }
    }

    @Override
    public void fetchPlanningPlants(BuContext ctx, PipelineState state, String operationType) {
        JSONObject sourceMap = "CREATE".equals(operationType) ? state.buMap : state.buMapForUpdate;
        for (String objectId : sourceMap.keySet()) {
            try {
                Object tagObj = sourceMap.get(objectId);
                if (!(tagObj instanceof JSONObject)) continue;
                JSONObject tags = (JSONObject) tagObj;
                // find first tag key (strip _create/_remove suffix)
                Iterator<String> keys = tags.keys();
                if (!keys.hasNext()) continue;
                String firstKey = keys.next().replace("_create", "").replace("_remove", "");
                String firstVal = tags.getString(tags.keys().next());
                String firstId  = firstVal.split(",")[0];

                IDfQuery query = new DfQuery();
                query.setDQL(String.format(ctx.planningPlantDql, firstKey, firstId));
                IDfCollection collection = query.execute(ctx.session, IDfQuery.DF_READ_QUERY);
                if (collection != null && collection.next()) {
                    String plant = collection.getString(AppConstants.d2PlanningPlant);
                    state.buMapWithPlants.put(objectId, plant);
                    logger.debug("{}:{} [PLANTS] objectId={} operation={} plant={}", ctx.buName,
                            Thread.currentThread().getName(), objectId, operationType, plant);
                }
                try { if (collection != null) collection.close(); } catch (Exception ignored) {}
            } catch (Exception e) {
                logger.error("{}:{} [PLANTS] Error fetching plant for objectId={}", ctx.buName,
                        Thread.currentThread().getName(), objectId, e);
            }
        }
    }

    @Override
    public void fetchPlanningPlantsDeleted(BuContext ctx, PipelineState state) {
        for (String objectId : state.buMapForDelete.keySet()) {
            // Try DB first
            try {
                PreparedStatement stmt = ctx.conn.prepareStatement(
                        String.format(ctx.fetchDeleteIdsPlanningPlantsSql, ctx.processingTable, objectId, ctx.buName));
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    String plant = rs.getString(AppConstants.dbPlant);
                    state.buMapForDeleteWithPlants.put(objectId, plant);
                    continue;
                }
            } catch (Exception e) {
                logger.debug("{}:{} [PLANTS] DB plant lookup failed for DELETE objectId={}, falling back to D2",
                        ctx.buName, Thread.currentThread().getName(), objectId);
            }
            // Fall back to D2
            try {
                IDfQuery query = new DfQuery();
                query.setDQL(String.format(ctx.planningPlantDql, "FLOC", objectId));
                IDfCollection col = query.execute(ctx.session, IDfQuery.DF_READ_QUERY);
                if (col != null && col.next()) {
                    state.buMapForDeleteWithPlants.put(objectId,
                            col.getString(AppConstants.d2PlanningPlant));
                }
                try { if (col != null) col.close(); } catch (Exception ignored) {}
            } catch (Exception e) {
                logger.error("{}:{} [PLANTS] Error fetching DELETE plant for objectId={}", ctx.buName,
                        Thread.currentThread().getName(), objectId, e);
            }
        }
    }

    @Override
    public void addDirNumber(String dirNumber, String versionId, BuContext ctx, int sno, String message) {
        try {
            IDfSysObject doc = (IDfSysObject) ctx.session.getObject(
                    ctx.session.getIdByQualification("dm_document where r_object_id='" + versionId + "'"));
            if (doc != null) {
                doc.lock();
                doc.setString(AppConstants.d2DirField, dirNumber);
                doc.save();
            }
        } catch (Exception e) {
            logger.error("{}:{} [DCTM] Error setting DIR number in D2 for versionId={}", ctx.buName,
                    Thread.currentThread().getName(), versionId, e);
        }
        // Also update DB
        try {
            PreparedStatement stmt = ctx.conn.prepareStatement(ctx.processingDbCreateUpdateSql);
            stmt.setString(1, AppConstants.completedStatus);
            stmt.setString(2, message);
            stmt.setString(3, dirNumber);
            stmt.setInt(4, sno);
            stmt.execute();
        } catch (Exception e) {
            logger.error("{}:{} [BAPI] Error updating DB post-addDirNumber sno={}", ctx.buName,
                    Thread.currentThread().getName(), sno, e);
        }
    }

    @Override
    public void addDirNumberWhichFailed(BuContext ctx, PipelineState state) {
        try {
            PreparedStatement stmt = ctx.conn.prepareStatement(
                    String.format("SELECT sno, r_object_id, dir_number FROM %s WHERE status='%s' AND bu='%s'",
                            ctx.processingTable, AppConstants.partCompletedStatus, ctx.buName));
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String dirNumber = rs.getString(AppConstants.dbDirNumberCol);
                String versionId = rs.getString(AppConstants.dbDocId);
                int sno = rs.getInt(AppConstants.dbSnoCol);
                addDirNumber(dirNumber, versionId, ctx, sno,
                        "DIR number updated from previous partial run.");
            }
        } catch (Exception e) {
            logger.error("{}:{} [DCTM] Error in addDirNumberWhichFailed", ctx.buName,
                    Thread.currentThread().getName(), e);
        }
    }

    @Override
    public void finaliseCutoverInput(String documents, BuContext ctx, PipelineState state) throws Exception {
        IDfQuery query = new DfQuery();
        String dql = String.format(ctx.cutoverDql,
                props.get("type"),
                documents);
        query.setDQL(dql);
        IDfCollection collection = null;
        try {
            collection = query.execute(ctx.session, IDfQuery.DF_READ_QUERY);
            if (collection == null) return;
            while (collection.next()) {
                String chronicleId = collection.getString(AppConstants.d2ChronicleId);
                Map<String, String> input = new HashMap<>();
                String[] tagFields = {
                        "sap_xecm_floc_tags", "sap_xecm_insp_tags",
                        "sap_xecm_tsklst_tags", "sap_xecm_wo_tags", "sap_xecm_equip_tags"
                };
                String[] boTypes = { AppConstants.floc, AppConstants.ipln,
                        AppConstants.tlst, AppConstants.wrdr, AppConstants.equi };
                for (int i = 0; i < tagFields.length; i++) {
                    int count = collection.getValueCount(tagFields[i]);
                    if (count > 0 && !collection.getRepeatingString(tagFields[i], 0).trim().isEmpty()) {
                        String[] ids = new String[count];
                        for (int j = 0; j < count; j++) {
                            ids[j] = collection.getRepeatingString(tagFields[i], j);
                        }
                        input.put(boTypes[i], StringUtils.join(ids, ","));
                    }
                }
                state.buMap.put(chronicleId, input);
                String existingDir = collection.getString(AppConstants.d2DirField);
                if (existingDir != null && !existingDir.isEmpty()) {
                    state.docsWithDir.put(chronicleId, existingDir);
                }
                Map<String, String> dirMap = new HashMap<>();
                dirMap.put(collection.getString(AppConstants.dbDocId),
                        existingDir == null ? "" : existingDir);
                state.docsWithNewVersion.put(chronicleId, dirMap);
                state.titles.put(chronicleId, collection.getString(AppConstants.d2TitleField));
                state.buMapWithPlants.put(chronicleId, "");
            }
        } finally {
            try { if (collection != null) collection.close(); } catch (Exception ignored) {}
        }
    }

    @Override
    public void getFlocAlternateLabels(BuContext ctx, PipelineState state) {
        List<String> labels = new ArrayList<>();
        JSONObject[] sources = { state.buMap, state.buMapForUpdate };
        for (JSONObject src : sources) {
            for (String id : src.keySet()) {
                Object val = src.opt(id);
                if (!(val instanceof JSONObject)) continue;
                JSONObject tags = (JSONObject) val;
                for (String key : tags.keySet()) {
                    if (key.startsWith("FLOC")) {
                        for (String floc : tags.getString(key).split(",")) {
                            if (!state.flocLabelToAlternate.containsKey(floc)) {
                                labels.add("'" + floc + "'");
                            }
                        }
                    }
                }
            }
        }
        if (!labels.isEmpty()) {
            flocRepository.getAlternateLabelsFromDb(
                    String.join(",", labels), ctx, state.flocLabelToAlternate);
        }
    }

    @Override
    public void getAlternateLabelsFromD2(BuContext ctx, PipelineState state) {
        IDfQuery query = new DfQuery();
        query.setDQL(ctx.flocDql);
        IDfCollection collection = null;
        List<Map<String, String>> rows = new ArrayList<>();
        try {
            collection = query.execute(ctx.session, IDfQuery.DF_READ_QUERY);
            if (collection == null) return;
            while (collection.next()) {
                String label     = collection.getString("label");
                String altLabel  = collection.getString("alternate_label");
                state.flocLabelToAlternate.put(label, altLabel);
                Map<String, String> row = new HashMap<>();
                row.put("label", label);
                row.put("alternate_label", altLabel);
                rows.add(row);
            }
            if (!rows.isEmpty()) {
                flocRepository.upsertFlocAlternateLabels(ctx, rows);
            }
        } catch (Exception e) {
            logger.error("{}:{} [FLOC] Error in getAlternateLabelsFromD2", ctx.buName,
                    Thread.currentThread().getName(), e);
        } finally {
            try { if (collection != null) collection.close(); } catch (Exception ignored) {}
        }
    }

    @Override
    public void executeRelatedTags(BuContext ctx, PipelineState state, String rObjectId) {
        try {
            XECMDoc2TagServiceMethodRunner runner = new XECMDoc2TagServiceMethodRunner(
                    ctx.session, rObjectId, ctx.docBase, ctx.buName, props);
            runner.execute();
        } catch (Exception e) {
            logger.error("{}:{} [DCTM] Error in executeRelatedTags rObjectId={}", ctx.buName,
                    Thread.currentThread().getName(), rObjectId, e);
        }
    }
}
