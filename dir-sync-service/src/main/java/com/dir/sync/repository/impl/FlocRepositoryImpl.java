package com.dir.sync.repository.impl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Repository;

import com.dir.sync.model.BuContext;
import com.dir.sync.repository.FlocRepository;
import com.dir.sync.util.AppConstants;

@Repository
public class FlocRepositoryImpl implements FlocRepository {

    private static final Logger logger = LogManager.getLogger(FlocRepositoryImpl.class);

    @Override
    public void upsertFlocAlternateLabels(BuContext ctx, List<Map<String, String>> rows) {
        try (PreparedStatement stmt = ctx.conn.prepareStatement(ctx.flocDbUpdateSql)) {
            ctx.conn.setAutoCommit(false);
            for (Map<String, String> row : rows) {
                stmt.setString(1, row.get("label"));
                stmt.setString(2, row.get("alternate_label"));
                stmt.setString(3, ctx.buName);
                stmt.addBatch();
            }
            stmt.executeBatch();
            ctx.conn.commit();
            ctx.conn.setAutoCommit(true);
        } catch (Exception e) {
            logger.error("{}:{} [FLOC] Error upserting FLOC alternate labels", ctx.buName,
                    Thread.currentThread().getName(), e);
        }
    }

    @Override
    public void getAlternateLabelsFromDb(String flocLabels, BuContext ctx,
                                          Map<String, String> flocLabelToAlternate) {
        String sql = String.format(ctx.flocSql, ctx.flocTable, flocLabels, ctx.buName);
        logger.debug("{}:{} [FLOC] getAlternateLabelsFromDb SQL: {}", ctx.buName,
                Thread.currentThread().getName(), sql);
        try (PreparedStatement stmt = ctx.conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs == null) {
                logger.warn("{}:{} [FLOC] No alternate labels in DB for {}", ctx.buName,
                        Thread.currentThread().getName(), flocLabels);
                return;
            }
            while (rs.next()) {
                flocLabelToAlternate.put(
                        rs.getString(AppConstants.dbLabel),
                        rs.getString(AppConstants.dbAlternateLabel));
            }
        } catch (Exception e) {
            logger.warn("{}:{} [FLOC] Error fetching alternate labels from DB for {}", ctx.buName,
                    Thread.currentThread().getName(), flocLabels, e);
        }
    }
}
