package com.dir.sync.repository.impl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.springframework.stereotype.Repository;

import com.dir.sync.model.BuContext;
import com.dir.sync.model.DirOperation;
import com.dir.sync.model.DirRecord;
import com.dir.sync.model.PipelineState;
import com.dir.sync.repository.ProcessingRepository;
import com.dir.sync.repository.ProcessingRowConsumer;
import com.dir.sync.util.AppConstants;

@Repository
public class ProcessingRepositoryImpl implements ProcessingRepository {

    private static final Logger logger = LogManager.getLogger(ProcessingRepositoryImpl.class);

    @Override
    public int getMaxBatchValue(String processingTable, String maxBatchSql, String buName) throws Exception {
        String sql = String.format(maxBatchSql, processingTable);
        try (Statement st = null) {
            // maxBatchSql is a SELECT MAX(...) + 1
            PreparedStatement stmt = null;
            // Use raw Connection from caller — ctx not available here, so we receive raw SQL
            throw new UnsupportedOperationException("Use overload that takes BuContext");
        }
    }

    public int getMaxBatchValue(BuContext ctx, String maxBatchSqlTemplate) throws Exception {
        String sql = String.format(maxBatchSqlTemplate, ctx.processingTable);
        try (Statement st = ctx.conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                int v = rs.getInt(1) + 1;
                logger.info("{}:{} [INIT] processingDBtableMaxbatchno={}", ctx.buName,
                        Thread.currentThread().getName(), v);
                return v;
            }
            logger.info("{}:{} [INIT] First processing batch for {}", ctx.buName,
                    Thread.currentThread().getName(), ctx.buName);
            return 1;
        } catch (Exception e) {
            logger.error("{}:{} [INIT] Database batch query failed", ctx.buName,
                    Thread.currentThread().getName(), e);
            throw e;
        }
    }

    @Override
    public String getDateOfLastRun(String processingTable, String lastRunDateSql, String buName) throws Exception {
        throw new UnsupportedOperationException("Use overload that takes BuContext");
    }

    public String getDateOfLastRun(BuContext ctx, String lastRunDateSqlTemplate) throws Exception {
        String sql = String.format(lastRunDateSqlTemplate, ctx.processingTable, ctx.buName);
        try (Statement st = ctx.conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                String d = rs.getString(1);
                logger.info("{}:{} [INIT] processingDBtableLastRun={}", ctx.buName,
                        Thread.currentThread().getName(), d);
                return d;
            }
            logger.info("{}:{} [INIT] First processing batch for {}", ctx.buName,
                    Thread.currentThread().getName(), ctx.buName);
            return "01/01/2020";
        } catch (Exception e) {
            logger.error("{}:{} [INIT] Database last-run-date query failed", ctx.buName,
                    Thread.currentThread().getName(), e);
            throw e;
        }
    }

    @Override
    public void populateProcessingTable(BuContext ctx, PipelineState state) {
        try (PreparedStatement stmt = ctx.conn.prepareStatement(ctx.processingDbInsertStmt)) {
            ctx.conn.setAutoCommit(false);

            // CREATE entries
            for (String objectId : state.buMap.keySet()) {
                String boIds = state.buMap.get(objectId).toString();
                logger.debug("{}:{} [PROCESS] CREATE objectId={} boIds={} plant={}", ctx.buName,
                        Thread.currentThread().getName(), objectId, boIds,
                        state.buMapWithPlants.opt(objectId));
                stmt.setString(1, objectId);
                stmt.setString(2, ctx.buName);
                stmt.setString(3, boIds);
                stmt.setInt(4, ctx.batchNo);
                stmt.setString(5, AppConstants.process);
                stmt.setString(6, AppConstants.create);
                stmt.setString(7, null);
                stmt.setString(8, state.messages.get(objectId));
                stmt.setString(9, state.docsWithNewVersion.get(objectId).keySet().iterator().next());
                stmt.setString(10, state.titles.get(objectId));
                stmt.setString(11, state.buMapWithPlants.optString(objectId));
                stmt.addBatch();
            }

            // UPDATE entries
            for (String objectId : state.buMapForUpdate.keySet()) {
                String boIds     = state.buMapForUpdate.get(objectId).toString();
                String dirNumber = state.docsWithDir.get(objectId);
                logger.debug("{}:{} [PROCESS] UPDATE objectId={} dirNumber={} plant={}", ctx.buName,
                        Thread.currentThread().getName(), objectId, dirNumber,
                        state.buMapWithPlants.opt(objectId));
                stmt.setString(1, objectId);
                stmt.setString(2, ctx.buName);
                stmt.setString(3, boIds);
                stmt.setInt(4, ctx.batchNo);
                stmt.setString(5, AppConstants.process);
                stmt.setString(6, AppConstants.update);
                stmt.setString(7, dirNumber);
                stmt.setString(8, state.messages.get(objectId));
                stmt.setString(9, state.docsWithNewVersion.get(objectId).keySet().iterator().next());
                stmt.setString(10, state.titles.get(objectId));
                stmt.setString(11, state.buMapWithPlants.optString(objectId));
                stmt.addBatch();
            }

            // DELETE entries
            for (Map.Entry<String, String> entry : state.buMapForDelete.entrySet()) {
                String objectId  = entry.getKey();
                String dirNumber = entry.getValue();
                logger.debug("{}:{} [PROCESS] DELETE objectId={} plant={}", ctx.buName,
                        Thread.currentThread().getName(), objectId,
                        state.buMapForDeleteWithPlants.get(objectId));
                stmt.setString(1, objectId);
                stmt.setString(2, ctx.buName);
                stmt.setString(3, null);
                stmt.setInt(4, ctx.batchNo);
                stmt.setString(5, AppConstants.process);
                stmt.setString(6, AppConstants.delete);
                stmt.setString(7, dirNumber);
                stmt.setString(8, null);
                stmt.setString(9, null);
                stmt.setString(10, null);
                stmt.setString(11, state.buMapForDeleteWithPlants.get(objectId));
                stmt.addBatch();
            }

            stmt.executeBatch();
            ctx.conn.commit();
            ctx.conn.setAutoCommit(true);
        } catch (Exception e) {
            logger.error("{}:{} [PROCESS] Error populating processing table", ctx.buName,
                    Thread.currentThread().getName(), e);
        }
    }

    @Override
    public void updateDbPostBapi(BuContext ctx, int sno, String message, String status, String dirNumber) {
        try {
            PreparedStatement stmt = ctx.conn.prepareStatement(ctx.processingDbUpdateSql);
            stmt.setString(1, status);
            stmt.setString(2, message);
            stmt.setString(3, dirNumber);
            stmt.setInt(4, sno);
            stmt.execute();
        } catch (Exception e) {
            logger.error("{}:{} [BAPI] Error updating DB post-BAPI sno={}", ctx.buName,
                    Thread.currentThread().getName(), sno, e);
        }
    }

    @Override
    public void updateDbError(BuContext ctx, String chronicleId, String message, String status,
                               String boIds, String operation, String dirNumber) {
        try {
            PreparedStatement stmt = ctx.conn.prepareStatement(ctx.processingDbInsertStmt);
            stmt.setString(1, chronicleId);
            stmt.setString(2, ctx.buName);
            stmt.setString(3, boIds);
            stmt.setInt(4, ctx.batchNo);
            stmt.setString(5, status);
            stmt.setString(6, operation);
            stmt.setString(7, dirNumber);
            stmt.setString(8, message);
            stmt.setString(9, null);
            stmt.setString(10, null);
            stmt.setString(11, null);
            stmt.execute();
        } catch (Exception e) {
            logger.error("{}:{} [BAPI] Error inserting error row objectId={}", ctx.buName,
                    Thread.currentThread().getName(), chronicleId, e);
        }
    }

    @Override
    public void updateDeletedDocRows(BuContext ctx) {
        try {
            String selectSql = String.format(ctx.megaDeleteSelectSql, ctx.processingTable,
                    AppConstants.delete, ctx.buName, AppConstants.completedStatus);
            logger.debug("{}:{} [CLEANUP] updateDeletedDocRows SQL: {}", ctx.buName,
                    Thread.currentThread().getName(), selectSql);
            PreparedStatement docStmt = ctx.conn.prepareStatement(selectSql);
            ResultSet rs = docStmt.executeQuery();
            StringBuilder ids = new StringBuilder();
            if (rs.isBeforeFirst()) {
                while (rs.next()) {
                    ids.append(",'").append(rs.getString(AppConstants.d2ChronicleId)).append("'");
                }
                if (ids.length() > 0) {
                    String chronicleIds = ids.substring(1);
                    PreparedStatement upd = ctx.conn.prepareStatement(
                            String.format(ctx.megaDeleteUpdateSql,
                                    ctx.processingTable, "DELETED", chronicleIds, ctx.buName));
                    logger.debug("{}:{} [CLEANUP] megaDeleteUpdate SQL: {}", ctx.buName,
                            Thread.currentThread().getName(), upd);
                    upd.execute();
                    logger.info("{}:{} [CLEANUP] Deleted DIR records updated", ctx.buName,
                            Thread.currentThread().getName());
                }
            }
        } catch (Exception e) {
            logger.error("{}:{} [CLEANUP] Error updating deleted doc rows", ctx.buName,
                    Thread.currentThread().getName(), e);
        }
    }

    @Override
    public void updateProcessToError(BuContext ctx) {
        try {
            PreparedStatement stmt = ctx.conn.prepareStatement(ctx.processingDbUpdateProcessErrorSql);
            stmt.setString(1, AppConstants.errorStatus);
            stmt.setString(2, "Could not fetch Auth token");
            stmt.setString(3, AppConstants.errorStatus);
            stmt.setInt(4, ctx.batchNo);
            stmt.setString(5, ctx.buName);
            stmt.execute();
        } catch (Exception e) {
            logger.error("{}:{} [BAPI] Error updating processing table to error status", ctx.buName,
                    Thread.currentThread().getName(), e);
        }
    }

    @Override
    public void fetchManualReadytoprocessEntries(BuContext ctx, PipelineState state) {
        String sql = String.format(
                "SELECT distinct on (r_object_id) r_object_id FROM %s where status='%s' and bu = '%s'",
                ctx.processingTable, AppConstants.process, ctx.buName);
        // Documents are then passed to DocumentumService.finaliseCutoverInput via the service layer
        ctx.conn.toString(); // keep reference — actual call happens via DocumentumService
        // Store the SQL for use by DocumentumServiceImpl
        state.messages.put("__manualSql__", sql);
    }

    @Override
    public void fetchDocuments(BuContext ctx, PipelineState state) {
        // Cutover mode — handled by DocumentumService.fetchDocuments via service layer
        // This stores the query SQL for use by DocumentumServiceImpl
        String sql = String.format(
                "SELECT r_object_id FROM %s where status='%s' and bu='%s'",
                ctx.processingTable118, AppConstants.completedStatus, ctx.buName);
        state.messages.put("__cutoverSql__", sql);
    }

    @Override
    public JSONObject fetchBoIds(BuContext ctx, String chronicleId) {
        String sql = String.format(
                "SELECT bo_ids FROM %s where r_object_id = '%s' and bu = '%s'",
                ctx.stagingTable, chronicleId, ctx.buName);
        try (PreparedStatement docStmt = ctx.conn.prepareStatement(sql);
             ResultSet rs = docStmt.executeQuery()) {
            JSONObject boIdsJson = new JSONObject();
            while (rs.next()) {
                boIdsJson = new JSONObject(rs.getString("bo_ids"));
            }
            for (String key : boIdsJson.keySet()) {
                Object value = boIdsJson.remove(key);
                boIdsJson.put(key + "_remove", value);
            }
            System.out.println("--------------boIds from fetchBoIds() Function-----------------: " + boIdsJson);
        } catch (Exception e) {
            logger.error("{}:{} [FETCH] Error fetching boIds for chronicleId={}", ctx.buName,
                    Thread.currentThread().getName(), chronicleId, e);
        }
        // Known preserved behaviour: always returns empty JSONObject (mirrors original FetchBoIds bug)
        return new JSONObject();
    }

    @Override
    public void processReadyEntries(BuContext ctx, PipelineState state,
                                     ProcessingRowConsumer rowConsumer) throws Exception {
        String sql = String.format(
                "SELECT * FROM %s where status='%s' and bu='%s'",
                ctx.processingTable, AppConstants.process, ctx.buName);
        try (PreparedStatement stmt = ctx.conn.prepareStatement(sql,
                ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
             ResultSet rs = stmt.executeQuery()) {
            if (!rs.isBeforeFirst()) {
                updateDbError(ctx, "NA", "No documents to process. ",
                        AppConstants.completedStatus, null, "NA", null);
                return;
            }
            while (rs.next()) {
                String modString   = rs.getString(AppConstants.dbTags);
                String operationStr = rs.getString(AppConstants.dbOperationCol);
                if (AppConstants.delete.equals(operationStr)) modString = "{}";
                JSONObject boIdsJson = new JSONObject(modString);
                DirOperation op = DirOperation.valueOf(operationStr);
                DirRecord record = new DirRecord(
                        rs.getInt(AppConstants.dbSnoCol),
                        rs.getString(AppConstants.d2ChronicleId),
                        op,
                        boIdsJson,
                        rs.getString(AppConstants.dbDirNumberCol),
                        rs.getString(AppConstants.dbPlant),
                        rs.getString(AppConstants.d2TitleField),
                        ctx.buName
                );
                logger.debug("{}:{} [BAPI] row sno={} chronicleId={} operation={} plant={} dirNumber={} title={}",
                        ctx.buName, Thread.currentThread().getName(),
                        record.getSno(), record.getChronicleId(), operationStr,
                        record.getPlanningPlant(), record.getDirNumber(), record.getTitle());
                rowConsumer.accept(record);
            }
        }
    }
}
