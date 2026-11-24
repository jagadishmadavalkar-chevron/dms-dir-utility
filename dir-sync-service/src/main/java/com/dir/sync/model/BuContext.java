package com.dir.sync.model;

import java.net.http.HttpClient;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.dir.sync.config.DirJobProperties;

/**
 * Per-run execution context — replaces BUProperties.
 * Holds all configuration, pre-formatted SQL/DQL strings, and the active DB connection.
 * Call initialize() at the start of each job run before any pipeline step executes.
 */
@Component
public class BuContext {

    // === Identity ===
    public String buName;
    public String docBase;

    // === Per-run values set during initialization ===
    public int    batchNo;
    public String dateOfLastRun;

    // === DB credentials (decrypted at init) ===
    public String jdbcUrl;
    public String dBUsername;
    public String dBPassword;

    // === Active DB connection (opened at job start, closed at job end) ===
    public Connection conn;

    // === SAP / APIM credentials (decrypted at init) ===
    public String apiKey;
    public String apimPassword;
    public String clientSecret;
    public String clientId;
    public String resource;
    public String apimUsername;
    public String endpoint;
    public String authEndpoint;
    public String companyCode;

    // === Document type config ===
    public String classType;
    public String className;
    public String charName;
    public String docType;
    public String docFile;
    public String docPart;
    public String docVersion;
    public String wsApplication;
    public String deleteObjectLink;

    // === Table names ===
    public String stagingTable;
    public String flocTable;
    public String processingTable;
    public String processingTable118;

    // === Pre-formatted SQL / DQL templates (mirror BUProperties field initializers) ===
    public String documentFetchDql;
    public String stagingDbUpdateSql;
    public String flocDbUpdateSql;
    public String compareQuery;
    public String dirQuery;
    public String selectOldTagsSql;
    public String selectNewTagsSql;
    public String processingDbInsertStmt;
    public String processingDbCreateUpdateSql;
    public String processingDbUpdateSql;
    public String updateDql;
    public String deleteTrailDql;
    public String deleteDql;
    public String deleteSql;
    public String dql;
    public String cutoverDql;
    public String flocDql;
    public String flocSql;
    public String megaDeleteSelectSql;
    public String megaDeleteUpdateSql;
    public String cleanStagingSelectSql;
    public String cleanStagingDeleteSql;
    public String planningPlantDql;
    public String fetchDeleteIdsPlanningPlantsSql;
    public String relatedTagsDql;
    public String processingDbUpdateProcessErrorSql;
    public String batchSummarySql;

    // === Attribute index — D2 field name → BO type (FLOC, IPLN, …) ===
    public Map<String, String> attrIndex = new HashMap<>();

    // === Shared HTTP client for auth token requests ===
    public HttpClient clientAuth = HttpClient.newHttpClient();

    /**
     * Initialises the context from DirJobProperties plus the decrypted credentials and
     * per-run values obtained from the DB (batchNo, dateOfLastRun).
     * This is the Spring Boot equivalent of the BUProperties constructor + field initialisers.
     */
    public void initialize(DirJobProperties props,
                           String buName, String docBase,
                           int batchNo, String dateOfLastRun,
                           String dBPassword,
                           String apimPassword, String clientSecret, String apiKey) {

        this.buName         = buName;
        this.docBase        = docBase;
        this.batchNo        = batchNo;
        this.dateOfLastRun  = dateOfLastRun;

        // DB
        this.jdbcUrl        = props.get("jdbcURL");
        this.dBUsername     = props.get("dBUsername");
        this.dBPassword     = dBPassword;

        // SAP
        this.apiKey         = apiKey;
        this.apimPassword   = apimPassword;
        this.clientSecret   = clientSecret;
        this.clientId       = props.get("clientId");
        this.resource       = props.get("resource");
        this.apimUsername   = props.get("APIMUsername");
        this.endpoint       = props.get("endpoint");
        this.authEndpoint   = props.get("authEndpoint");
        this.companyCode    = props.get("companyCode");

        // Document type
        this.classType      = props.get("classType");
        this.className      = props.get("className");
        this.charName       = props.get("charName");
        this.docType        = props.get("documentType");
        this.docFile        = props.get("docFile");
        this.docPart        = props.get("documentPart");
        this.docVersion     = props.get("documentVersion");
        this.wsApplication  = props.get("wsApplication");
        this.deleteObjectLink = props.get("deleteObjectLink");

        // Table names
        this.stagingTable      = props.get("stagingDBtable");
        this.flocTable         = props.get("flocDBtable");
        this.processingTable   = props.get("processingDBtable");
        this.processingTable118 = props.get("processingDBtable118");

        // Pre-formatted SQL — mirrors BUProperties field initialisers exactly
        this.documentFetchDql         = props.get("documentfetchdql");
        this.stagingDbUpdateSql       = String.format(props.get("stagingDbInsertSql"), stagingTable);
        this.flocDbUpdateSql          = String.format(props.get("flocDbInsertSql"), flocTable);

        String comparePart = props.get("compareQuery-part1") + props.get("compareQuery-part2");
        this.compareQuery  = String.format(comparePart, stagingTable, stagingTable, stagingTable, stagingTable);

        this.dirQuery              = props.get("dirQuery");
        this.selectOldTagsSql      = String.format(props.get("selectOldTagsSql"), stagingTable);
        this.selectNewTagsSql      = String.format(props.get("selectNewTagsSql"), stagingTable);
        this.processingDbInsertStmt       = String.format(props.get("processingDbInsertSql"), processingTable);
        this.processingDbCreateUpdateSql  = String.format(props.get("processingdbcreateupdatesql"), processingTable);
        this.processingDbUpdateSql        = String.format(props.get("processingdbupdatesql"), processingTable);
        this.updateDql             = props.get("DocumentUpdateDql");
        this.deleteTrailDql        = props.get("deleteTrailDql");
        this.deleteDql             = props.get("dontDeleteDql");
        this.deleteSql             = props.get("deleteSql");
        this.dql                   = props.get("selectUpdatedDocumentsDql");
        this.cutoverDql            = "select title, i_chronicle_id, sap_dir_num, r_object_id, " +
                "sap_xecm_floc_tags,sap_xecm_insp_tags,sap_xecm_tsklst_tags,sap_xecm_wo_tags,sap_xecm_equip_tags " +
                "from %s where r_object_id in (%s)";
        this.flocDql               = props.get("flocAlternateLabelDql");
        this.flocSql               = props.get("flocAlternateLabelSql");
        this.megaDeleteSelectSql   = props.get("deleteMegaSelectSql");
        this.megaDeleteUpdateSql   = props.get("deleteMegaUpdateSql");
        this.cleanStagingSelectSql = props.get("cleanStagingSelectSql");
        this.cleanStagingDeleteSql = props.get("cleanStagingDeleteSql");
        this.planningPlantDql               = props.get("planningPlantDql");
        this.fetchDeleteIdsPlanningPlantsSql = props.get("fetchDeleteIdsPlanningPlantsSql");
        this.relatedTagsDql = "select r_object_id from %s where r_modify_date >= '%s' " +
                "and a_status in (%s) and %s not in (%s) and any r_version_label='CURRENT';";
        this.processingDbUpdateProcessErrorSql =
                String.format("UPDATE %s SET status = ?, message = ? WHERE status = ? AND batch_no = ? AND bu = ?",
                        processingTable);
        this.batchSummarySql = props.get("batchSummaryQuery");

        // Attribute index — D2 field name → BO type key
        attrIndex.clear();
        attrIndex.put(props.get("FLOCField"), com.dir.sync.util.AppConstants.floc);
        attrIndex.put(props.get("IPLNField"), com.dir.sync.util.AppConstants.ipln);
        attrIndex.put(props.get("TLSTField"), com.dir.sync.util.AppConstants.tlst);
        attrIndex.put(props.get("WRDRField"), com.dir.sync.util.AppConstants.wrdr);
        attrIndex.put(props.get("EQUIField"), com.dir.sync.util.AppConstants.equi);

        // Fresh HTTP client for this run
        this.clientAuth = HttpClient.newHttpClient();
    }
}
