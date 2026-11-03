package DIRBulkUpdate;

import java.net.http.HttpClient;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONObject;

import com.documentum.fc.client.IDfSession;


/*
 * This class is used to store the BU specific data, like input file, buName,
 * DB connection, input file source mapping, missing tags mapping, filenames mapping,
 * staging and processing batch numbers, and the doc-to-(BO-to-tags) mapping.
 */
public class BUProperties {

    List<String> wsValues = new ArrayList<String>(); //List of all Workspaces in D2
    Map<String, String> messages = new HashMap<>(); // Map of input file details from D2
    Connection conn; // Connection to Database
    String buName; // Name of BU for this thread
    Map<String, String> titles = new HashMap<>();
    JSONObject buMap = new JSONObject(); // doc-to-(BO-to-IDs) mapping extracted from all input files
    JSONObject buMapWithPlants = new JSONObject(); // R4A
    JSONObject buMapForUpdate = new JSONObject();
    Map<String, String> buMapForDelete = new HashMap<>();
    Map<String, String> buMapForDeleteWithPlants = new HashMap<>();
    Integer batchNo; // Batch numbers for processing and staging table
    String dateOfLastRun;
    IDfSession session; // Documentum Session
    Map<String,Map<String,String>> docsWithNewVersion = new HashMap<>();
    Map<String,String> docsWithDir = new HashMap<>();
    Map<String, String> attrIndex = new HashMap<>(); // Mapping of attributes from configuration file to BO type
    String docBase;
    Map<String, String> flocLabelToAlternate = new HashMap<>();
    String apiKey;
    String apimPassword;
    String clientSecret;
    JSONObject authToken = new JSONObject();
    HttpClient clientAuth = HttpClient.newHttpClient();

    
    

    public BUProperties(IDfSession session, Connection conn, String buName, Integer bno,String dateOfLastRun, String docBase, String apiKey, String apimPassword, String clientSecret) {
        this.conn = conn;
        this.buName = buName;
        this.dateOfLastRun = dateOfLastRun;
        this.docBase = docBase;
        this.batchNo = bno;
        this.session = session;
        this.apiKey = apiKey;
        this.apimPassword = apimPassword;
        this.clientSecret = clientSecret;
        
    }
    

    public void setAttrIndex() {
        this.attrIndex.put(DIRBulkUpdate.prop.getProperty("FLOCField"), Constants.floc);
        this.attrIndex.put(DIRBulkUpdate.prop.getProperty("IPLNField"), Constants.ipln);
        this.attrIndex.put(DIRBulkUpdate.prop.getProperty("TLSTField"), Constants.tlst);
        this.attrIndex.put(DIRBulkUpdate.prop.getProperty("WRDRField"), Constants.wrdr);
        this.attrIndex.put(DIRBulkUpdate.prop.getProperty("EQUIField"), Constants.equi);
    }

    String relatedTagsDql = "select r_object_id from %s where r_modify_date >= '%s' and a_status in (%s) and %s not in (%s) and any r_version_label='CURRENT';";
    String documentFetchDql = DIRBulkUpdate.prop.getProperty("documentfetchdql");
    String stagingTable = DIRBulkUpdate.prop.getProperty("stagingDBtable"); // Staging table name
    String flocTable = DIRBulkUpdate.prop.getProperty("flocDBtable"); // Staging table name
    String stagingDbUpdateSql = String.format(DIRBulkUpdate.prop.getProperty("stagingDbInsertSql"), stagingTable); // SQL statement to push data to staging table.
    String flocDbUpdateSql = String.format(DIRBulkUpdate.prop.getProperty("flocDbInsertSql"), flocTable); // SQL statement to push FLOC data.

    String comparePart = DIRBulkUpdate.prop.getProperty("compareQuery-part1") + DIRBulkUpdate.prop.getProperty("compareQuery-part2");
    String compareQuery = String.format(comparePart, stagingTable, stagingTable, stagingTable, stagingTable); // SQL statement to compare batches in staging table for deltas

    String dirQuery = DIRBulkUpdate.prop.getProperty("dirQuery"); // SQL statement to query existing DIR numbers
    String selectOldTagsSql = String.format(DIRBulkUpdate.prop.getProperty("selectOldTagsSql"), stagingTable);
    String selectNewTagsSql = String.format(DIRBulkUpdate.prop.getProperty("selectNewTagsSql"), stagingTable);

    String processingTable = DIRBulkUpdate.prop.getProperty("processingDBtable"); // processing table name
    String processingTable118 = DIRBulkUpdate.prop.getProperty("processingDBtable118"); // processing table name
    String dql = DIRBulkUpdate.prop.getProperty("selectUpdatedDocumentsDql"); // DQL statement to pull input files from D2
    String cutoverDql = "select title, i_chronicle_id, sap_dir_num, r_object_id, sap_xecm_floc_tags,sap_xecm_insp_tags,sap_xecm_tsklst_tags,sap_xecm_wo_tags,sap_xecm_equip_tags from %s where r_object_id in (%s)";

    String processingDbInsertStmt = String.format(DIRBulkUpdate.prop.getProperty("processingDbInsertSql"), processingTable);
    String processingDbCreateUpdateSql = String.format(DIRBulkUpdate.prop.getProperty("processingdbcreateupdatesql"), processingTable);
    String processingDbUpdateSql = String.format(DIRBulkUpdate.prop.getProperty("processingdbupdatesql"), processingTable);
    String updateDql = DIRBulkUpdate.prop.getProperty("DocumentUpdateDql");
	String deleteTrailDql = DIRBulkUpdate.prop.getProperty("deleteTrailDql");
	String deleteDql = DIRBulkUpdate.prop.getProperty("dontDeleteDql");
	String deleteSql = DIRBulkUpdate.prop.getProperty("deleteSql");
	String classType = DIRBulkUpdate.prop.getProperty("classType");
	String className = DIRBulkUpdate.prop.getProperty("className");
	String charName = DIRBulkUpdate.prop.getProperty("charName");
	String docType = DIRBulkUpdate.prop.getProperty("documentType");
	String docFile = DIRBulkUpdate.prop.getProperty("docFile");
	String docPart = DIRBulkUpdate.prop.getProperty("documentPart");
	String docVersion = DIRBulkUpdate.prop.getProperty("documentVersion");
	String wsApplication = DIRBulkUpdate.prop.getProperty("wsApplication");
	String deleteObjectLink = DIRBulkUpdate.prop.getProperty("deleteObjectLink");
	String clientId = DIRBulkUpdate.prop.getProperty("clientId");

	String resource = DIRBulkUpdate.prop.getProperty("resource");
	String apimUsername = DIRBulkUpdate.prop.getProperty("APIMUsername");

	String endpoint = DIRBulkUpdate.prop.getProperty("endpoint");
	String authEndpoint = DIRBulkUpdate.prop.getProperty("authEndpoint");

	String companyCode = DIRBulkUpdate.prop.getProperty("companyCode");
	String flocDql = DIRBulkUpdate.prop.getProperty("flocAlternateLabelDql");
	String flocSql = DIRBulkUpdate.prop.getProperty("flocAlternateLabelSql");
	String megaDeleteSelectSql = DIRBulkUpdate.prop.getProperty("deleteMegaSelectSql");
	String megaDeleteUpdateSql = DIRBulkUpdate.prop.getProperty("deleteMegaUpdateSql");
	String cleanStagingSelectSql = DIRBulkUpdate.prop.getProperty("cleanStagingSelectSql");
	String cleanStagingDeleteSql = DIRBulkUpdate.prop.getProperty("cleanStagingDeleteSql");
	

	String planningPlantDql = DIRBulkUpdate.prop.getProperty("planningPlantDql"); // R4A
	String fetchDeleteIdsPlanningPlantsSql = DIRBulkUpdate.prop.getProperty("fetchDeleteIdsPlanningPlantsSql"); // R4A
	public final AtomicBoolean authTokenFlag = new AtomicBoolean(false);
	String processingDbUpdateProcessErrorSql = String.format("UPDATE %s SET status = ?, message = ? WHERE status = ? AND batch_no = ? AND bu = ?", processingTable);

    /*
	 * This function disconnects the current session and closes the database
	 * connection, and then terminates the program.
	 */
    public void cleanUp() {
        try {
            if (session != null) {
                session.disconnect();
            }
            if (conn != null) {
            	DatabaseFunctions.cleanUpStagingTable(conn, cleanStagingSelectSql, processingTable, buName, batchNo, cleanStagingDeleteSql, stagingTable);
                conn.close();
            }
            DIRBulkUpdate.logger.info("{}:{} [CLEANUP] Documentum and Database connections closed", buName, Thread.currentThread().getName());
        } catch (Exception e) {
            DIRBulkUpdate.logger.error("{}:{} [CLEANUP] Error closing connections", buName, Thread.currentThread().getName(), e);
        }
    }
}
