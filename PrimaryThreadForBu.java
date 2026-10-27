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
	String buName; // Name of BU
	Properties prop; // DD values
	Connection conn; // Database connection
	IDfSession session; // Documentum session
	Integer batchNo; // Current Batch Number
	String docBase; // Docbase name
	String dateOfLastRun; // Date of last run - to fetch docs modified since then.

	// contructor to pass sesion data from main function.
	PrimaryThreadForBu(String buName, Properties prop, IDfSession session, String docBase) {

		this.prop = prop;
		this.buName = buName;
		this.session = session;
		this.docBase = docBase;
	}

	// primary execution function for DIR Creation Service
	public void executeForBu() {
		Decryptor decryptor = new Decryptor();

		System.out.println("Extracting JDBCURL, dbUsername, dbPassword, apiKey, apimPassword, clientId, clientSecret");
		DIRBulkUpdate.logger
				.info("Extracting JDBCURL, dbUsername, dbPassword, apiKey, apimPassword, clientId, clientSecret");

		String jdbcUrl = prop.getProperty("jdbcURL");
		String dBUsername = prop.getProperty("dBUsername");
		if (jdbcUrl == null) {
			System.out.println(buName + ": " + Thread.currentThread().getName() + " could not extract JDBC URL.");
			DIRBulkUpdate.logger
					.info(buName + ": " + Thread.currentThread().getName() + " could not extract JDBC URL.");

			return;
		}
		if (dBUsername == null) {
			System.out.println(buName + ": " + Thread.currentThread().getName() + " could not extract DB User.");
			DIRBulkUpdate.logger.info(buName + ": " + Thread.currentThread().getName() + " could not extract DB User.");

			return;
		}

		System.out.println(buName + ": " + Thread.currentThread().getName() + "Decrypting creds");
		DIRBulkUpdate.logger.info(buName + ": " + Thread.currentThread().getName() + "Decrypting creds");

		String dBPassword;
		try {
			dBPassword = decryptor.decrypt(prop.getProperty("dbPassword"));
		} catch (Exception e) {
			System.out
					.println(buName + ": " + Thread.currentThread().getName() + " could not extract DB User Password.");
			DIRBulkUpdate.logger
					.info(buName + ": " + Thread.currentThread().getName() + " could not extract DB User Password.");

			return;
		}

		String apimPassword;
		try {
			apimPassword = decryptor.decrypt(prop.getProperty("APIMPassword"));
			// apimPassword = DIRBulkUpdate.prop.getProperty("APIPass");
		} catch (Exception e) {
			System.out.println(
					buName + ": " + Thread.currentThread().getName() + " could not extract APIM User Password.");
			DIRBulkUpdate.logger
					.info(buName + ": " + Thread.currentThread().getName() + " could not extract APIM User Password.");

			return;
		}

		String clientSecret;
		try {
			clientSecret = decryptor.decrypt(prop.getProperty("clientSecret"));
		} catch (Exception e) {
			System.out.println(buName + ": " + Thread.currentThread().getName() + " could not extract Client Secret.");
			DIRBulkUpdate.logger
					.info(buName + ": " + Thread.currentThread().getName() + " could not extract Client Secret.");

			return;
		}

		String apiKey;
		try {
			apiKey = decryptor.decrypt(prop.getProperty("APIKey"));
		} catch (Exception e) {
			System.out.println(buName + ": " + Thread.currentThread().getName() + " could not extract API Key.");
			DIRBulkUpdate.logger.info(buName + ": " + Thread.currentThread().getName() + " could not extract API Key.");

			return;
		}
		System.out.println(buName + ": " + Thread.currentThread().getName() + "Creds decrypted");
		DIRBulkUpdate.logger.info(buName + ": " + Thread.currentThread().getName() + "Creds decrypted");

		System.out.println("Database connection started");
		DIRBulkUpdate.logger.info("Database connection started");

		try {
			conn = DatabaseFunctions.connectToDatabase(jdbcUrl, dBUsername, dBPassword);
		} catch (SQLException e) {
			e.printStackTrace();
			DIRBulkUpdate.logger.info("Database connection error: " + e.getMessage());
			System.out.println(buName + ":" + Thread.currentThread().getName() + ": Database connection failed");
			DIRBulkUpdate.logger.info(buName + ":" + Thread.currentThread().getName() + ": Database connection failed");
			if (session != null) {
				try {
					session.disconnect();
				} catch (DfException ex) {
					ex.printStackTrace();
					DIRBulkUpdate.logger.info("Documentum disconnection error: " + ex.getMessage());
				}
			}

			return;
		}

		if (conn == null) {
			System.err.println(buName + ":" + Thread.currentThread().getName() + ": Database connection failed");
			DIRBulkUpdate.logger.info(buName + ":" + Thread.currentThread().getName() + ": Database connection failed");
			if (session != null) {
				try {
					session.disconnect();
				} catch (DfException ex) {
					ex.printStackTrace();
					DIRBulkUpdate.logger.info("Documentum disconnection error: " + ex.getMessage());
				}
			}

			return;
		}

		try {
			batchNo = DatabaseFunctions.getMaxBatchValue(prop, conn, buName);
			dateOfLastRun = DatabaseFunctions.getDateOfLastRun(prop, conn, buName);

			System.out.println(buName + ":" + Thread.currentThread().getName() + ": Batch No: " + batchNo);
			DIRBulkUpdate.logger.info(buName + ":" + Thread.currentThread().getName() + ": Batch No: " + batchNo);
			System.out.println(buName + ":" + Thread.currentThread().getName() + ": Last Run Date: " + dateOfLastRun);
			DIRBulkUpdate.logger
					.info(buName + ":" + Thread.currentThread().getName() + ": Last Run Date: " + dateOfLastRun);
		} catch (SQLException e) {
			System.out.println(
					buName + ":" + Thread.currentThread().getName() + ": could not fetch data from Processing Table.");
			DIRBulkUpdate.logger.info(
					buName + ":" + Thread.currentThread().getName() + ": could not fetch date from Processing Table.");
			if (session != null) {
				try {
					session.disconnect();
				} catch (DfException ex) {
					ex.printStackTrace();
					DIRBulkUpdate.logger.info("Documentum disconnection error: " + ex.getMessage());
				}
			}
			if (conn != null) {
				try {
					conn.close();
				} catch (SQLException ex) {
					ex.printStackTrace();
					DIRBulkUpdate.logger.info("Database disconnection error: " + ex.getMessage());
				}
			}

			return;
		}

		// Initialize BUProperties object with execution specific data.
		BUProperties bu = new BUProperties(session, conn, buName, batchNo, dateOfLastRun, docBase, apiKey, apimPassword,
				clientSecret);
		bu.setAttrIndex();

		if (prop.getProperty("pullDirectory").equalsIgnoreCase("True")) {
			System.out.println(buName + ":" + Thread.currentThread().getName() + ": Pulling directory");
			DIRBulkUpdate.logger.info(buName + ":" + Thread.currentThread().getName() + ": Pulling directory");

			try {
				System.out.println(buName + ":" + Thread.currentThread().getName()
						+ ": Updating failed documents from previous executions");
				DIRBulkUpdate.logger.info(buName + ":" + Thread.currentThread().getName()
						+ ": Updating failed documents from previous executions");
				DocumentumFunctions.addDirNumberWhichFailed(bu);
				System.out.println(buName + ":" + Thread.currentThread().getName()
						+ ": Updated failed documents from previous executions");
				DIRBulkUpdate.logger.info(buName + ":" + Thread.currentThread().getName()
						+ ": Updated failed documents from previous executions");

				System.out.println(buName + ":" + Thread.currentThread().getName()
						+ ": Updating deleted documents from previous executions");
				DIRBulkUpdate.logger.info(buName + ":" + Thread.currentThread().getName()
						+ ": Updating deleted documents from previous executions");
				DatabaseFunctions.updateDeletedDocRows(bu);
				System.out.println(buName + ":" + Thread.currentThread().getName()
						+ ": Updated failed deleted from previous executions");
				DIRBulkUpdate.logger.info(buName + ":" + Thread.currentThread().getName()
						+ ": Updated deleted documents from previous executions");

				System.out.println(buName + ":" + Thread.currentThread().getName() + ": Fetching FLOCs");
				DIRBulkUpdate.logger.info(buName + ":" + Thread.currentThread().getName() + ": Fetching FLOCs");
				DocumentumFunctions.getAlternateLabelsFromD2(bu);
				System.out.println(buName + ":" + Thread.currentThread().getName() + ": Fetched FLOCs");
				DIRBulkUpdate.logger.info(buName + ":" + Thread.currentThread().getName() + ": Fetched FLOCs");

				System.out.println(buName + ":" + Thread.currentThread().getName() + ": Fetching documents");
				DIRBulkUpdate.logger.info(buName + ":" + Thread.currentThread().getName() + ": Fetching Documents");
				if (!prop.getProperty("cutOverFlag").equalsIgnoreCase("True")) {
					DocumentumFunctions.updateParentTags(bu);
					DocumentumFunctions.checkInputDocuments(bu);
					DocumentumFunctions.checkDeleteDocuments(bu);
				} else {
					DatabaseFunctions.fetchDocuments(bu); // Check for plant value in the DB
				}

				System.err.println(buName + ":" + Thread.currentThread().getName() + ": Documents fetched");
				DIRBulkUpdate.logger.info(buName + ":" + Thread.currentThread().getName() + ": Documents fetched");

			} catch (Exception e) {
				System.out.println(bu.buName + ":" + Thread.currentThread().getName() + ": Error :" + e.getMessage());
				DIRBulkUpdate.logger
						.info(bu.buName + ":" + Thread.currentThread().getName() + ": Error :" + e.getMessage());
				bu.cleanUp();

				return;
			}
		}


		if (prop.getProperty("populateStaging").equalsIgnoreCase("True")) {
			System.out.println(buName + ":" + Thread.currentThread().getName() + ": Populating Staging Table");
			DIRBulkUpdate.logger.info(buName + ":" + Thread.currentThread().getName() + "Populating Staging Table");
			try {
				DatabaseFunctions.populateStagingTable(bu);
				System.out.println(buName + ":" + Thread.currentThread().getName() + ": Populated Staging Table");
				DIRBulkUpdate.logger.info(buName + ":" + Thread.currentThread().getName() + "Populated Staging Table");

			} catch (Exception e) {
				System.out.println(bu.buName + ":" + Thread.currentThread().getName() + ": Error :" + e.getMessage());
				DIRBulkUpdate.logger
						.info(bu.buName + ":" + Thread.currentThread().getName() + ": Error :" + e.getMessage());
				bu.cleanUp();

				return;
			}
		}

		if (prop.getProperty("compareAndFindDeltas").equalsIgnoreCase("True")) {
			System.out.println(buName + ":" + Thread.currentThread().getName() + ": Finding Deltas");
			DIRBulkUpdate.logger.info(buName + ":" + Thread.currentThread().getName() + "Finding Deltas");

			try {
				DatabaseFunctions.findDeltas(bu);
				System.out.println(buName + ":" + Thread.currentThread().getName() + ": Found deltas");
				DIRBulkUpdate.logger.info(buName + ":" + Thread.currentThread().getName() + "Found deltas");

			} catch (Exception e) {
				System.out.println(bu.buName + ":" + Thread.currentThread().getName() + ": Error :" + e.getMessage());
				DIRBulkUpdate.logger
						.info(bu.buName + ":" + Thread.currentThread().getName() + ": Error :" + e.getMessage());
				bu.cleanUp();

				return;
			}
		}

		System.out.println(buName + ":" + Thread.currentThread().getName() + ": DIRs to create: " + bu.buMap);
		DIRBulkUpdate.logger.info(buName + ":" + Thread.currentThread().getName() + ": DIRs to create: " + bu.buMap);

		System.out.println(buName + ":" + Thread.currentThread().getName() + ": DIRs to update: " + bu.buMapForUpdate);
		DIRBulkUpdate.logger
				.info(buName + ":" + Thread.currentThread().getName() + ": DIRs to update: " + bu.buMapForUpdate);

		System.out.println(buName + ":" + Thread.currentThread().getName() + ": DIRs to delete: " + bu.buMapForDelete);
		DIRBulkUpdate.logger
				.info(buName + ":" + Thread.currentThread().getName() + ": DIRs to delete: " + bu.buMapForDelete);

		
		// R4A
		System.out.println(buName + ":" + Thread.currentThread().getName() + ": Retrieving plants: Operation: CREATE ");
		DIRBulkUpdate.logger.info(buName + ":" + Thread.currentThread().getName() + ": Retrieving plants: Operation: CREATE ");

		if (!bu.buMap.isEmpty()) {
			DocumentumFunctions.FetchPlanningPlants(bu, "CREATE");
		}else {
			System.out.println(buName + ":" + Thread.currentThread().getName() + ": No documents: Operation: CREATE ");
			DIRBulkUpdate.logger
					.info(buName + ":" + Thread.currentThread().getName() + ": No documents: Operation: CREATE ");
		}

		System.out.println(buName + ":" + Thread.currentThread().getName() + ": Retrieving plants: Operation: UPDATE ");
		DIRBulkUpdate.logger.info(buName + ":" + Thread.currentThread().getName() + ": Retrieving plants: Operation: UPDATE ");
		if (!bu.buMapForUpdate.isEmpty()) {
			DocumentumFunctions.FetchPlanningPlants(bu, "UPDATE");
		} else {
			System.out.println(buName + ":" + Thread.currentThread().getName() + ": No documents: Operation: UPDATE ");
			DIRBulkUpdate.logger
					.info(buName + ":" + Thread.currentThread().getName() + ": No documents: Operation: UPDATE ");
		}

		System.out.println(buName + ":" + Thread.currentThread().getName() + ": Retrieving plants: Operation: DELETE ");
		DIRBulkUpdate.logger.info(buName + ":" + Thread.currentThread().getName() + ": Retrieving plants: Operation: DELETE ");
		if (bu.buMapForDelete.size() > 0) {
			for (String docId : bu.buMapForDelete.keySet()) {
				bu.buMapForDeleteWithPlants.put(docId, "");
			}
			DocumentumFunctions.FetchPlanningPlantsDeleted(bu); // R4A
		} else {
			System.out.println(buName + ":" + Thread.currentThread().getName() + ": No documents: Operation: DELETE ");
			DIRBulkUpdate.logger
					.info(buName + ":" + Thread.currentThread().getName() + ": No documents: Operation: DELETE ");
		}

		
		if (prop.getProperty("populateProcessing").equalsIgnoreCase("True")) {
			System.out.println(buName + ":" + Thread.currentThread().getName() + ": Populating Processing Table");
			DIRBulkUpdate.logger.info(buName + ":" + Thread.currentThread().getName() + "Populating Processing Table");
			try {
				DatabaseFunctions.populateProcessingTable(bu);
				System.out.println(buName + ":" + Thread.currentThread().getName() + ": Populated Processing Table");
				DIRBulkUpdate.logger
						.info(buName + ":" + Thread.currentThread().getName() + "Populated Processing Table");

			} catch (Exception e) {
				System.out.println(bu.buName + ":" + Thread.currentThread().getName() + ": Error :" + e.getMessage());
				DIRBulkUpdate.logger
						.info(bu.buName + ":" + Thread.currentThread().getName() + ": Error :" + e.getMessage());
				bu.cleanUp();

				return;
			}
		}


		if (prop.getProperty("callBapis").equalsIgnoreCase("True")) {
			System.out.println(buName + ":" + Thread.currentThread().getName() + ": Calling API Endpoints");
			DIRBulkUpdate.logger.info(buName + ":" + Thread.currentThread().getName() + "Calling API Endpoints");
			try {
				System.out
						.println(buName + ":" + Thread.currentThread().getName() + ": Fetching FLOC Alternate Labels");
				DIRBulkUpdate.logger
						.info(buName + ":" + Thread.currentThread().getName() + "Fetching FLOC Alternate Labels");
				DocumentumFunctions.getFlocAlternateLabels(bu);
				System.out.println(buName + ":" + Thread.currentThread().getName() + ": Fetched FLOC Alternate Labels");
				DIRBulkUpdate.logger
						.info(buName + ":" + Thread.currentThread().getName() + "Fetched FLOC Alternate Labels");
				DatabaseFunctions.processDirs(bu);
				System.out.println(buName + ":" + Thread.currentThread().getName() + ": DIRs Processed Successfully");
				DIRBulkUpdate.logger
						.info(buName + ":" + Thread.currentThread().getName() + "DIRs Processsed Successfully");

			} catch (Exception e) {
				System.out.println(bu.buName + ":" + Thread.currentThread().getName() + ": Error :" + e.getMessage());
				DIRBulkUpdate.logger
						.info(bu.buName + ":" + Thread.currentThread().getName() + ": Error :" + e.getMessage());
				bu.cleanUp();

				return;
			}
		}

		System.out.println("Reporting");
		DIRBulkUpdate.logger.info("Reporting");
		if (prop.getProperty("report").equalsIgnoreCase("True")) {
			try {
				reporting(bu);
			} catch (Exception e) {
				e.printStackTrace();
			}

		}
		bu.cleanUp();

	}

	/*
	 * This function is used to decrypt the environment variables. It takes in the
	 * decryptor and env variable name, extracts the env variable, decrypts it, and
	 * return the value.
	 * 
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

				System.out.println(report);
				DIRBulkUpdate.logger.info(report);
			}
		} catch (SQLException ex) {
			ex.printStackTrace();
			System.out.println(bu.buName + ":" + Thread.currentThread().getName() + ": Error while fetching records");
			DIRBulkUpdate.logger
					.info(bu.buName + ":" + Thread.currentThread().getName() + ": Error while fetching records");
			DIRBulkUpdate.logger.info(bu.buName + ":" + Thread.currentThread().getName() + "error: " + ex.getMessage());
		}
	}
}