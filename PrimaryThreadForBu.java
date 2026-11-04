package DIRBulkUpdate;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import com.documentum.fc.client.IDfSession;
import com.documentum.fc.common.DfException;

/*
 * This class is used to implement BU level multi-threading. It enables parallel
 * connection to multiple BUs. It takes in the BU name, the properties, and the countdown latch.
 * It establishes a connection to the DB and extracts the staging and processing batch numbers.
 * The BU name is used to collect the directory containing all the input files, set the
 * BU specific data class, and then processes the input files by calling the docToTag/docToStaging thread
 * on all the files. It waits for all the individual file threads to finish processing
 * before pushing the resultant mapping to the processing table.
 */
public class PrimaryThreadForBu {
	String buName;
	Properties prop;
	Connection conn;
	IDfSession session;
	Integer batchNo;
	String docBase;
	String dateOfLastRun;

	PrimaryThreadForBu(String buName, Properties prop, IDfSession session, String docBase) {
		this.prop = prop;
		this.buName = buName;
		this.session = session;
		this.docBase = docBase;
	}

	public void executeForBu() {
		Decryptor decryptor = new Decryptor();

		DIRBulkUpdate.logger.info("{}:{} [INIT] Extracting and decrypting credentials", buName, Thread.currentThread().getName());

		String jdbcUrl = prop.getProperty("jdbcURL");
		String dBUsername = prop.getProperty("dBUsername");
		if (jdbcUrl == null) {
			DIRBulkUpdate.logger.error("{}:{} [INIT] Could not extract JDBC URL", buName, Thread.currentThread().getName());
			return;
		}
		if (dBUsername == null) {
			DIRBulkUpdate.logger.error("{}:{} [INIT] Could not extract DB User", buName, Thread.currentThread().getName());
			return;
		}

		String dBPassword;
		try {
			dBPassword = decryptor.decrypt(prop.getProperty("dbPassword"));
		} catch (Exception e) {
			DIRBulkUpdate.logger.error("{}:{} [INIT] Could not decrypt DB Password", buName, Thread.currentThread().getName(), e);
			return;
		}

		String apimPassword;
		try {
			apimPassword = decryptor.decrypt(prop.getProperty("APIMPassword"));
		} catch (Exception e) {
			DIRBulkUpdate.logger.error("{}:{} [INIT] Could not decrypt APIM Password", buName, Thread.currentThread().getName(), e);
			return;
		}

		String clientSecret;
		try {
			clientSecret = decryptor.decrypt(prop.getProperty("clientSecret"));
		} catch (Exception e) {
			DIRBulkUpdate.logger.error("{}:{} [INIT] Could not decrypt Client Secret", buName, Thread.currentThread().getName(), e);
			return;
		}

		String apiKey;
		try {
			apiKey = decryptor.decrypt(prop.getProperty("APIKey"));
		} catch (Exception e) {
			DIRBulkUpdate.logger.error("{}:{} [INIT] Could not decrypt API Key", buName, Thread.currentThread().getName(), e);
			return;
		}

		DIRBulkUpdate.logger.info("{}:{} [INIT] Credentials decrypted successfully", buName, Thread.currentThread().getName());
		DIRBulkUpdate.logger.info("{}:{} [INIT] Database connection starting", buName, Thread.currentThread().getName());

		try {
			conn = DatabaseFunctions.connectToDatabase(jdbcUrl, dBUsername, dBPassword);
		} catch (SQLException e) {
			DIRBulkUpdate.logger.error("{}:{} [INIT] Database connection failed", buName, Thread.currentThread().getName(), e);
			if (session != null) {
				try {
					session.disconnect();
				} catch (DfException ex) {
					DIRBulkUpdate.logger.error("{}:{} [INIT] Documentum disconnection error", buName, Thread.currentThread().getName(), ex);
				}
			}
			return;
		}

		if (conn == null) {
			DIRBulkUpdate.logger.error("{}:{} [INIT] Database connection is null", buName, Thread.currentThread().getName());
			if (session != null) {
				try {
					session.disconnect();
				} catch (DfException ex) {
					DIRBulkUpdate.logger.error("{}:{} [INIT] Documentum disconnection error", buName, Thread.currentThread().getName(), ex);
				}
			}
			return;
		}

		try {
			batchNo = DatabaseFunctions.getMaxBatchValue(prop, conn, buName);
			dateOfLastRun = DatabaseFunctions.getDateOfLastRun(prop, conn, buName);
			DIRBulkUpdate.logger.info("{}:{} [INIT] Batch No={}", buName, Thread.currentThread().getName(), batchNo);
			DIRBulkUpdate.logger.info("{}:{} [INIT] Last Run Date={}", buName, Thread.currentThread().getName(), dateOfLastRun);
		} catch (SQLException e) {
			DIRBulkUpdate.logger.error("{}:{} [INIT] Could not fetch batch data from Processing Table", buName, Thread.currentThread().getName(), e);
			if (session != null) {
				try {
					session.disconnect();
				} catch (DfException ex) {
					DIRBulkUpdate.logger.error("{}:{} [INIT] Documentum disconnection error", buName, Thread.currentThread().getName(), ex);
				}
			}
			if (conn != null) {
				try {
					conn.close();
				} catch (SQLException ex) {
					DIRBulkUpdate.logger.error("{}:{} [INIT] Database disconnection error", buName, Thread.currentThread().getName(), ex);
				}
			}
			return;
		}

		BUProperties bu = new BUProperties(session, conn, buName, batchNo, dateOfLastRun, docBase, apiKey, apimPassword, clientSecret);
		bu.setAttrIndex();

		if (prop.getProperty("pullDirectory").equalsIgnoreCase("True")) {
			DIRBulkUpdate.logger.info("{}:{} [FETCH] Pulling directory", buName, Thread.currentThread().getName());

			try {
				DIRBulkUpdate.logger.info("{}:{} [DCTM] Updating failed documents from previous executions", buName, Thread.currentThread().getName());
				DocumentumFunctions.addDirNumberWhichFailed(bu);
				DIRBulkUpdate.logger.info("{}:{} [DCTM] Updated failed documents from previous executions", buName, Thread.currentThread().getName());

				DIRBulkUpdate.logger.info("{}:{} [CLEANUP] Updating deleted documents from previous executions", buName, Thread.currentThread().getName());
				DatabaseFunctions.updateDeletedDocRows(bu);
				DIRBulkUpdate.logger.info("{}:{} [CLEANUP] Updated deleted documents from previous executions", buName, Thread.currentThread().getName());

				DIRBulkUpdate.logger.info("{}:{} [FLOC] Fetching FLOC alternate labels from D2", buName, Thread.currentThread().getName());
				DocumentumFunctions.getAlternateLabelsFromD2(bu);
				DIRBulkUpdate.logger.info("{}:{} [FLOC] Fetched FLOC alternate labels from D2", buName, Thread.currentThread().getName());
				DIRBulkUpdate.logger.debug("{}:{} [FLOC] flocLabelToAlternate after D2 fetch: {}", buName, Thread.currentThread().getName(), bu.flocLabelToAlternate);

				DIRBulkUpdate.logger.info("{}:{} [FETCH] Fetching documents from D2", buName, Thread.currentThread().getName());
				if (!prop.getProperty("cutOverFlag").equalsIgnoreCase("True")) {
					DocumentumFunctions.updateParentTags(bu);
					DocumentumFunctions.checkInputDocuments(bu);
					DocumentumFunctions.checkDeleteDocuments(bu);
				} else {
					DatabaseFunctions.fetchDocuments(bu);
				}
				DIRBulkUpdate.logger.info("{}:{} [FETCH] Documents fetched", buName, Thread.currentThread().getName());

			} catch (Exception e) {
				DIRBulkUpdate.logger.error("{}:{} [FETCH] Error during fetch phase", bu.buName, Thread.currentThread().getName(), e);
				bu.cleanUp();
				return;
			}
		}

		if (prop.getProperty("populateStaging").equalsIgnoreCase("True")) {
			DIRBulkUpdate.logger.info("{}:{} [STAGING] Populating staging table", buName, Thread.currentThread().getName());
			try {
				DatabaseFunctions.populateStagingTable(bu);
				DIRBulkUpdate.logger.info("{}:{} [STAGING] Staging table populated", buName, Thread.currentThread().getName());
			} catch (Exception e) {
				DIRBulkUpdate.logger.error("{}:{} [STAGING] Error populating staging table", bu.buName, Thread.currentThread().getName(), e);
				bu.cleanUp();
				return;
			}
		}

		if (prop.getProperty("compareAndFindDeltas").equalsIgnoreCase("True")) {
			DIRBulkUpdate.logger.info("{}:{} [DELTA] Finding deltas", buName, Thread.currentThread().getName());
			try {
				DatabaseFunctions.findDeltas(bu);
				DIRBulkUpdate.logger.info("{}:{} [DELTA] Deltas found", buName, Thread.currentThread().getName());
			} catch (Exception e) {
				DIRBulkUpdate.logger.error("{}:{} [DELTA] Error finding deltas", bu.buName, Thread.currentThread().getName(), e);
				bu.cleanUp();
				return;
			}
		}

		DIRBulkUpdate.logger.info("{}:{} [DELTA] DIRs to create={}", buName, Thread.currentThread().getName(), bu.buMap.length());
		DIRBulkUpdate.logger.info("{}:{} [DELTA] DIRs to update={}", buName, Thread.currentThread().getName(), bu.buMapForUpdate.length());
		DIRBulkUpdate.logger.info("{}:{} [DELTA] DIRs to delete={}", buName, Thread.currentThread().getName(), bu.buMapForDelete.size());
		DIRBulkUpdate.logger.debug("{}:{} [DELTA] buMap contents: {}", buName, Thread.currentThread().getName(), bu.buMap);
		DIRBulkUpdate.logger.debug("{}:{} [DELTA] buMapForUpdate contents: {}", buName, Thread.currentThread().getName(), bu.buMapForUpdate);
		DIRBulkUpdate.logger.debug("{}:{} [DELTA] buMapForDelete contents: {}", buName, Thread.currentThread().getName(), bu.buMapForDelete);

		// R4A
		DIRBulkUpdate.logger.info("{}:{} [PLANTS] Retrieving planning plants for CREATE", buName, Thread.currentThread().getName());
		if (!bu.buMap.isEmpty()) {
			DocumentumFunctions.FetchPlanningPlants(bu, "CREATE");
		} else {
			DIRBulkUpdate.logger.info("{}:{} [PLANTS] No documents for CREATE", buName, Thread.currentThread().getName());
		}

		DIRBulkUpdate.logger.info("{}:{} [PLANTS] Retrieving planning plants for UPDATE", buName, Thread.currentThread().getName());
		if (!bu.buMapForUpdate.isEmpty()) {
			DocumentumFunctions.FetchPlanningPlants(bu, "UPDATE");
		} else {
			DIRBulkUpdate.logger.info("{}:{} [PLANTS] No documents for UPDATE", buName, Thread.currentThread().getName());
		}

		DIRBulkUpdate.logger.info("{}:{} [PLANTS] Retrieving planning plants for DELETE", buName, Thread.currentThread().getName());
		if (bu.buMapForDelete.size() > 0) {
			for (String docId : bu.buMapForDelete.keySet()) {
				bu.buMapForDeleteWithPlants.put(docId, "");
			}
			DocumentumFunctions.FetchPlanningPlantsDeleted(bu); // R4A
		} else {
			DIRBulkUpdate.logger.info("{}:{} [PLANTS] No documents for DELETE", buName, Thread.currentThread().getName());
		}

		if (prop.getProperty("populateProcessing").equalsIgnoreCase("True")) {
			DIRBulkUpdate.logger.info("{}:{} [PROCESS] Populating processing table", buName, Thread.currentThread().getName());
			try {
				DatabaseFunctions.populateProcessingTable(bu);
				DIRBulkUpdate.logger.info("{}:{} [PROCESS] Processing table populated", buName, Thread.currentThread().getName());
			} catch (Exception e) {
				DIRBulkUpdate.logger.error("{}:{} [PROCESS] Error populating processing table", bu.buName, Thread.currentThread().getName(), e);
				bu.cleanUp();
				return;
			}
		}

		if (prop.getProperty("callBapis").equalsIgnoreCase("True")) {
			DIRBulkUpdate.logger.info("{}:{} [BAPI] Calling SAP API endpoints", buName, Thread.currentThread().getName());
			try {
				DIRBulkUpdate.logger.info("{}:{} [FLOC] Fetching FLOC alternate labels for BAPI", buName, Thread.currentThread().getName());
				DocumentumFunctions.getFlocAlternateLabels(bu);
				DIRBulkUpdate.logger.info("{}:{} [FLOC] Fetched FLOC alternate labels for BAPI", buName, Thread.currentThread().getName());
				DIRBulkUpdate.logger.debug("{}:{} [FLOC] flocLabelToAlternate before BAPI: {}", buName, Thread.currentThread().getName(), bu.flocLabelToAlternate);
				DatabaseFunctions.processDirs(bu);
				DIRBulkUpdate.logger.info("{}:{} [BAPI] DIRs processed successfully", buName, Thread.currentThread().getName());
			} catch (Exception e) {
				DIRBulkUpdate.logger.error("{}:{} [BAPI] Error processing DIRs", bu.buName, Thread.currentThread().getName(), e);
				bu.cleanUp();
				return;
			}
		}

		DIRBulkUpdate.logger.info("{}:{} [REPORT] Generating batch report", buName, Thread.currentThread().getName());
		if (prop.getProperty("report").equalsIgnoreCase("True")) {
			try {
				reporting(bu);
			} catch (Exception e) {
				DIRBulkUpdate.logger.error("{}:{} [REPORT] Error generating report", buName, Thread.currentThread().getName(), e);
			}
		}
		bu.cleanUp();
	}

	/*
	 * This function is used to decrypt the environment variables. It takes in the
	 * decryptor and env variable name, extracts the env variable, decrypts it, and
	 * return the value.
	 */
	static String decryptEnvVar(String envVarName, Decryptor decryptor) throws Exception {
		String envVarValue = System.getenv(envVarName);
		if (envVarValue == null || envVarValue.length() == 0) {
			throw new Exception(envVarName + " environment variable not set");
		}
		return decryptor.decrypt(envVarValue);
	}

	/*
	 * This function reports the total number of documents processed and
	 * successful/failed count.
	 */
	private static void reporting(BUProperties bu) {
		String batchSummarySql = String.format(DIRBulkUpdate.prop.getProperty("batchSummaryQuery"), bu.processingTable,
				bu.batchNo, bu.buName, bu.processingTable, bu.batchNo, bu.buName, bu.processingTable, bu.batchNo,
				bu.buName);
		try (Statement st = bu.conn.createStatement(); ResultSet rs = st.executeQuery(batchSummarySql)) {
			if (rs.next()) {
				String report = String.format("%n============================================================%n"
						+ "BU           : %s%n" + "Thread       : %s%n" + "Batch No     : %d%n"
						+ "------------------------------------------------------------%n" + "Total Records      : %s%n"
						+ "Processed Records  : %s%n" + "Failed Records     : %s%n"
						+ "============================================================", bu.buName,
						Thread.currentThread().getName(), bu.batchNo, rs.getString(1), rs.getString(2),
						rs.getString(3));
				DIRBulkUpdate.logger.info(report);
			}
		} catch (SQLException ex) {
			DIRBulkUpdate.logger.error("{}:{} [REPORT] Error fetching batch summary", bu.buName, Thread.currentThread().getName(), ex);
		}
	}
}
