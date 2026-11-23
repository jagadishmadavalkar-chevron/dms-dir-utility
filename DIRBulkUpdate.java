package DIRBulkUpdate;


import java.util.*;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;

import com.documentum.fc.client.DfClient;
import com.documentum.fc.client.DfQuery;
import com.documentum.fc.client.IDfClient;
import com.documentum.fc.client.IDfCollection;
import com.documentum.fc.client.IDfQuery;
import com.documentum.fc.client.IDfSession;
import com.documentum.fc.client.IDfSessionManager;
import com.documentum.fc.common.DfException;
import com.documentum.fc.common.DfLoginInfo;
import com.documentum.fc.common.IDfLoginInfo;


public class DIRBulkUpdate{
    protected static Properties prop = new Properties();
    protected static final Logger logger = LogManager.getLogger();

    /*
     * Order of Execution:
     * 1. Fetch DCTM details from ENV, Create Documentum session, make a connection to DB and get batch numbers.       -> createSession(),connect(),getMaxBatchValue()
     * 5. Set BU properties.                                                              -> buProperty, bu.setAttrIndex()
     * 6. Pull directory containing input files for BU.									  -> checkInputDocuments()
     * 7. Loop through directory and invoke a thread for each file to process in parallel.-> docToTag,docToStaging
     * 8. Map out a consolidated <docID - FLOCID - TLSTID - IPLNID - WRDRID>.             -> setInputToDocMapping(),xRefLookUp()
     *                                                                                       compareEquipmentTagsBySql()
     * 9. Push the mapping to the processing table.                                       -> pushtoProcessing()
     * 10.Update document metadata in D2 based on data in processing table.				  -> getWSValues(),updateMetadata()
     * 11.Run Core Job on all updated documents.										  -> coreJob()
     * 12.Update status of input files and report updates made in this run.				  -> updateInputFiles(),reporting()
     * 13.Clean up connections and sessions.											  -> bu.cleanUp()
     * 14.Exit
     */
    public static void main(String[] args) {
    	Decryptor decryptor = new Decryptor();
        String documentumUserName = null;

        logger.info("[INIT] DIR Creation process started");

        try {
            documentumUserName = System.getenv("DocumentumUser");
        } catch (Exception e) {
            logger.error("[INIT] Could not extract Documentum User Name", e);
            System.exit(0);
        }
        String documentumUserPass = null;
        try {
            documentumUserPass = PrimaryThreadForBu.decryptEnvVar("DocumentumPw", decryptor);
        } catch (Exception e) {
            logger.error("[INIT] Could not extract Documentum User Password", e);
            System.exit(0);
        }
        String docBase = null;
        try {
            docBase = System.getenv("Docbase");
        } catch (Exception e) {
            logger.error("[INIT] Could not extract Documentum docBase", e);
            System.exit(0);
        }

        logger.info("[INIT] Documentum User={} Docbase={}", documentumUserName, docBase);
        logger.info("[INIT] Documentum session initialization started: docBase={}", docBase);

        IDfClient client = new DfClient();
        IDfSessionManager sessionManager = client.newSessionManager();
        IDfSession session = null;
        try {
        	session = createDocumentumSession(documentumUserName, documentumUserPass, docBase, sessionManager);
        } catch (DfException e) {
            logger.error("[INIT] Could not connect to docbase", e);
            System.exit(0);
        }
        if (session == null) {
            logger.error("[INIT] Could not connect to docbase - session is null");
            System.exit(0);
        }

        // Extracting Business Unit name.
        String buName = null;
        if(docBase.contains("mcbu") || docBase.contains("MCBU")) {
			buName = "MCBU";
		}else if(docBase.contains("cbu") || docBase.contains("CBU")) {
			buName = "CBU";
		}else if(docBase.contains("sjv") || docBase.contains("SJV")) {
			buName = "SJV";
		}else if(docBase.contains("gom") || docBase.contains("GOM")) {
			buName = "GOM";
		}else if(docBase.contains("dmcspo") || docBase.contains("DMCSPO") || docBase.contains("cpp") || docBase.contains("CPP")) { // R4A
			buName = "CREG";
		} else if(docBase.contains("sntb") || docBase.contains("SNTB")) { // R2C
			buName = "STCO";
		}
        
        if (buName == null) {
            logger.error("[INIT] Could not initialize the buName");
            System.exit(0);
        }

        logger.info("[INIT] Initialized BuName={}", buName);
        logger.info("[INIT] Loading Data Dictionary for BU={}", buName);

        try {
			prop = getD2DictionaryConfigValues(session);
			// R4A
			if(prop == null || prop.isEmpty()) {
				logger.error("[INIT] Data dictionary is empty or not present in {}", buName);
				System.exit(0);
			}
		} catch (DfException e) {
			logger.error("[INIT] Could not fetch Data Dictionary", e);
            System.exit(0);
		}

        // Apply debug mode if configured in D2 dictionary (debugMode=True enables DEBUG on our logger only — DFC stays suppressed)
        if ("True".equalsIgnoreCase(prop.getProperty("debugMode"))) {
            Configurator.setLevel("DIRBulkUpdate", Level.DEBUG);
            logger.info("[INIT] DEBUG mode enabled - verbose logging active on console and file");
        }

        logger.info("[INIT] Starting Primary Thread for BU={}", buName);
        PrimaryThreadForBu buThread = new PrimaryThreadForBu(buName, prop, session, docBase);
        buThread.executeForBu();

        logger.info("[INIT] DIR Creation completed for BU={}", buName);
        System.exit(0);
    }

    /*
     * This function takes the documentum session as argument and queries the docbase
     * for the data dictionary configured in the BU documentum for the DIR Creation bulk utility.
     * This data dictionary is returned as a Properties object.
     */
    private static Properties getD2DictionaryConfigValues(IDfSession dctmSession) throws DfException {
		 Properties d2DDConfig = new Properties();
		 HashMap<String, String> configValues = new HashMap<String, String>();
		 IDfQuery queryObj = new DfQuery();
		 IDfCollection collectionObj = null;
		 try {
//			 xecm-sap-dir-service-bulk-config-mc3
//			 xecm-sap-dir-service-bulk-config
			 queryObj.setDQL("select r_object_id,object_name,locale_value from d2_dictionary_value where dictionary_name='xecm-sap-dir-service-bulk-config'");
			 collectionObj = queryObj.execute(dctmSession, IDfQuery.DF_EXECREAD_QUERY);
			 while (collectionObj.next()) {
				 configValues.put(collectionObj.getString("object_name"), collectionObj.getString("locale_value"));
			 }
		 } catch (Exception e) {
			 logger.error("[INIT] Error fetching Data Dictionary", e);
		 } finally {
			 if (collectionObj != null) {
				 collectionObj.close();
			 }
		 }
		 if (!configValues.isEmpty()) {
			 d2DDConfig.putAll(configValues);
		 }
		 return d2DDConfig;
	 }

    /*
     * This function takes the documentum username, password, session manager and docbase as arguments
     * and creates a Documentum Session.
     */
   protected static IDfSession createDocumentumSession(String user, String password, String docbase, IDfSessionManager sessionManager) throws DfException {
       IDfSession Session = null;
       logger.info("[INIT] Creating Documentum session for Docbase={}", docbase);
       IDfLoginInfo loginInfo = new DfLoginInfo();
       loginInfo.setUser(user);
       loginInfo.setPassword(password);
       try {
           sessionManager.setIdentity(docbase, loginInfo);
           Session = sessionManager.getSession(docbase);
           logger.info("[INIT] Documentum session created for Docbase={}", docbase);
       } catch (Exception e) {
           logger.error("[INIT] Failed to create Documentum session for Docbase={}", docbase, e);
       }
       return Session;
   }
}