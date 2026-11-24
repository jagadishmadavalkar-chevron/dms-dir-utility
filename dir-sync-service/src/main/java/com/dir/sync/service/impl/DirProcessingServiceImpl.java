package com.dir.sync.service.impl;

import java.sql.ResultSet;
import java.sql.Statement;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.dir.sync.model.BuContext;
import com.dir.sync.model.PipelineState;
import com.dir.sync.repository.StagingRepository;
import com.dir.sync.repository.impl.ProcessingRepositoryImpl;
import com.dir.sync.service.DirProcessingService;

@Service
public class DirProcessingServiceImpl implements DirProcessingService {

    private static final Logger logger = LogManager.getLogger(DirProcessingServiceImpl.class);

    @Autowired
    private StagingRepository stagingRepository;

    @Autowired
    private ProcessingRepositoryImpl processingRepository;

    @Override
    public void populateStagingTable(BuContext ctx, PipelineState state) throws Exception {
        stagingRepository.populateStagingTable(ctx, state);
    }

    @Override
    public void findDeltas(BuContext ctx, PipelineState state) {
        stagingRepository.findDeltas(ctx, state);
    }

    @Override
    public void populateProcessingTable(BuContext ctx, PipelineState state) {
        processingRepository.populateProcessingTable(ctx, state);
    }

    @Override
    public void updateDeletedDocRows(BuContext ctx) {
        processingRepository.updateDeletedDocRows(ctx);
    }

    @Override
    public void cleanUpAndClose(BuContext ctx) {
        try {
            stagingRepository.cleanUpStagingTable(ctx);
            if (ctx.conn != null && !ctx.conn.isClosed()) {
                ctx.conn.close();
                logger.info("{}:{} [CLEANUP] Database connection closed", ctx.buName,
                        Thread.currentThread().getName());
            }
        } catch (Exception e) {
            logger.error("{}:{} [CLEANUP] Error during cleanUpAndClose", ctx.buName,
                    Thread.currentThread().getName(), e);
        }
    }

    @Override
    public String generateReport(BuContext ctx) throws Exception {
        String sql = String.format(ctx.batchSummarySql,
                ctx.processingTable, ctx.batchNo, ctx.buName,
                ctx.processingTable, ctx.batchNo, ctx.buName,
                ctx.processingTable, ctx.batchNo, ctx.buName);
        try (Statement st = ctx.conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                return String.format(
                        "%n============================================================%n"
                        + "BU           : %s%n"
                        + "Thread       : %s%n"
                        + "Batch No     : %d%n"
                        + "------------------------------------------------------------%n"
                        + "Total Records      : %s%n"
                        + "Processed Records  : %s%n"
                        + "Failed Records     : %s%n"
                        + "============================================================",
                        ctx.buName, Thread.currentThread().getName(), ctx.batchNo,
                        rs.getString(1), rs.getString(2), rs.getString(3));
            }
        }
        return "";
    }
}
