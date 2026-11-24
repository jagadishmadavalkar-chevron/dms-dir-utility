package com.dir.sync.repository.impl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.springframework.stereotype.Repository;

import com.dir.sync.model.BuContext;
import com.dir.sync.model.PipelineState;
import com.dir.sync.repository.StagingRepository;
import com.dir.sync.util.AppConstants;

@Repository
public class StagingRepositoryImpl implements StagingRepository {

    private static final Logger logger = LogManager.getLogger(StagingRepositoryImpl.class);

    @Override
    public void populateStagingTable(BuContext ctx, PipelineState state) throws Exception {
        try (PreparedStatement stmt = ctx.conn.prepareStatement(ctx.stagingDbUpdateSql)) {
            ctx.conn.setAutoCommit(false);
            for (String key : state.buMap.keySet()) {
                state.messages.put(key, "Business Objects For DIR: " + state.buMap.get(key).toString() + " | ");
                stmt.setString(1, key);
                stmt.setString(2, state.buMap.get(key).toString());
                stmt.setString(3, ctx.buName);
                stmt.setInt(4, ctx.batchNo);
                stmt.addBatch();
            }
            stmt.executeBatch();
            ctx.conn.commit();
            ctx.conn.setAutoCommit(true);
        } catch (Exception e) {
            ctx.conn.rollback();
            logger.error("{}:{} [STAGING] Error updating staging table", ctx.buName,
                    Thread.currentThread().getName(), e);
            throw e;
        }
    }

    @Override
    public void findDeltas(BuContext ctx, PipelineState state) {
        List<String> keysToMove = new ArrayList<>();
        try {
            String objectIds = state.buMap.keySet().stream()
                    .map(k -> "'" + k + "'").collect(Collectors.joining(","));
            if (!objectIds.isEmpty()) {
                PreparedStatement dirStmt = ctx.conn.prepareStatement(
                        String.format(ctx.dirQuery, ctx.processingTable, objectIds, ctx.buName));
                logger.debug("{}:{} [DELTA] dirQuery: {}", ctx.buName, Thread.currentThread().getName(),
                        dirStmt.toString());
                ResultSet dirRs = dirStmt.executeQuery();
                while (dirRs.next()) {
                    String dirNum = dirRs.getString(AppConstants.dbDirNumberCol);
                    if (dirNum != null && !dirNum.isEmpty()) {
                        state.docsWithDir.put(dirRs.getString(AppConstants.d2ChronicleId), dirNum);
                        logger.debug("{}:{} [DELTA] chronicleId={} existing DIR={}", ctx.buName,
                                Thread.currentThread().getName(),
                                dirRs.getString(AppConstants.d2ChronicleId), dirNum);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("{}:{} [DELTA] Error querying DIR numbers", ctx.buName,
                    Thread.currentThread().getName(), e);
        }

        for (String objectId : state.buMap.keySet()) {
            if (state.docsWithDir.containsKey(objectId)) {
                boolean changed = false;
                try (PreparedStatement queryStmt = ctx.conn.prepareStatement(ctx.compareQuery)) {
                    for (int i = 1; i <= 6; i++) {
                        if (i % 3 == 0) queryStmt.setString(i, ctx.buName);
                        else             queryStmt.setString(i, objectId);
                    }
                    ResultSet rs = queryStmt.executeQuery();
                    rs.next();
                    changed = rs.getBoolean(1);
                } catch (Exception e) {
                    logger.error("{}:{} [DELTA] Error in compare query objectId={}", ctx.buName,
                            Thread.currentThread().getName(), objectId, e);
                }

                if (changed) {
                    String boIdsOld = null;
                    String boIdsNew = null;
                    try (PreparedStatement oldStmt = ctx.conn.prepareStatement(ctx.selectOldTagsSql);
                         PreparedStatement newStmt = ctx.conn.prepareStatement(ctx.selectNewTagsSql)) {

                        oldStmt.setString(1, objectId);
                        oldStmt.setString(2, ctx.buName);
                        ResultSet rsOld = oldStmt.executeQuery();
                        while (rsOld.next()) boIdsOld = rsOld.getString(AppConstants.dbTags);

                        newStmt.setString(1, objectId);
                        newStmt.setString(2, ctx.buName);
                        ResultSet rsNew = newStmt.executeQuery();
                        while (rsNew.next()) boIdsNew = rsNew.getString(AppConstants.dbTags);

                        if (boIdsOld == null || boIdsOld.isEmpty()) {
                            keysToMove.add(objectId);
                            continue;
                        }
                        JSONObject oldJson = new JSONObject(boIdsOld);
                        JSONObject newJson = new JSONObject(boIdsNew);
                        String[] attrs = "EQUI,FLOC,TLST,IPLN,WRDR".split(",");
                        Map<String, String> delta = new HashMap<>();
                        for (String attr : attrs) {
                            String newIds = newJson.optString(attr, "");
                            String oldIds = oldJson.optString(attr, "");
                            String addToDir = "";
                            String removeFromDir = "";
                            if (!oldIds.isEmpty() || !newIds.isEmpty()) {
                                if (oldIds.isEmpty() || newIds.isEmpty()) {
                                    addToDir    = newIds;
                                    removeFromDir = oldIds;
                                } else {
                                    List<String> newList  = new ArrayList<>(Arrays.asList(newIds.split(",")));
                                    List<String> newTemp  = new ArrayList<>(Arrays.asList(newIds.split(",")));
                                    List<String> oldList  = new ArrayList<>(Arrays.asList(oldIds.split(",")));
                                    newList.removeAll(oldList);
                                    oldList.removeAll(newTemp);
                                    addToDir      = String.join(",", newList);
                                    removeFromDir = String.join(",", oldList);
                                }
                                logger.debug("{}:{} [DELTA] objectId={} attr={} old={} new={} add={} remove={}",
                                        ctx.buName, Thread.currentThread().getName(), objectId, attr,
                                        oldIds, newIds, addToDir, removeFromDir);
                                if (!addToDir.isEmpty())      delta.put(attr + "_create", addToDir);
                                if (!removeFromDir.isEmpty()) delta.put(attr + "_remove", removeFromDir);
                            }
                            state.buMapForUpdate.put(objectId, delta);
                            keysToMove.add(objectId);
                        }
                    } catch (Exception e) {
                        logger.error("{}:{} [DELTA] Error comparing tags objectId={}", ctx.buName,
                                Thread.currentThread().getName(), objectId, e);
                        keysToMove.add(objectId);
                    }
                } else {
                    keysToMove.add(objectId);
                    state.messages.remove(objectId);
                }
            }
        }

        for (String key : keysToMove) {
            if (state.buMap.has(key)) state.buMap.remove(key);
        }
    }

    @Override
    public void cleanUpStagingTable(BuContext ctx) {
        try {
            String selectSql = String.format(ctx.cleanStagingSelectSql,
                    ctx.processingTable, ctx.buName, ctx.batchNo);
            logger.debug("{}:{} [CLEANUP] cleanUpStagingTable SQL: {}", ctx.buName,
                    Thread.currentThread().getName(), selectSql);
            PreparedStatement docStmt = ctx.conn.prepareStatement(selectSql);
            ResultSet rs = docStmt.executeQuery();
            StringBuilder chronicleIds = new StringBuilder();
            if (rs.isBeforeFirst()) {
                while (rs.next()) {
                    if (!"NA".equals(rs.getString(AppConstants.d2ChronicleId))) {
                        chronicleIds.append(",'").append(rs.getString(AppConstants.d2ChronicleId)).append("'");
                    }
                }
                if (chronicleIds.length() > 0) {
                    String ids = chronicleIds.substring(1);
                    PreparedStatement del = ctx.conn.prepareStatement(
                            String.format(ctx.cleanStagingDeleteSql, ctx.stagingTable, ctx.buName, ctx.batchNo, ids));
                    logger.debug("{}:{} [CLEANUP] cleanStagingDelete SQL: {}", ctx.buName,
                            Thread.currentThread().getName(), del);
                    del.execute();
                    logger.info("{}:{} [CLEANUP] Error staging records cleaned up", ctx.buName,
                            Thread.currentThread().getName());
                }
            }
        } catch (Exception e) {
            logger.error("{}:{} [CLEANUP] Error cleaning staging table", ctx.buName,
                    Thread.currentThread().getName(), e);
        }
    }
}
