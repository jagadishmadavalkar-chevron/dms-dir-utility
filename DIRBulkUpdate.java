package DIRBulkUpdate;


import java.util.*;
import org.apache.logging.log4j.Logger;

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


import org.apache.logging.log4j.LogManager;

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
    	Decryptor decryptor = new Decryptor();		// Decryptor initialised to decrypt DCTM password, DB Password, APIM Password, Client Secret, and API Key
        String documentumUserName = null;
        
        
        System.out.println("DIR Creation process started");
        DIRBulkUpdate.logger.info("DIR Creation process started");
        
        
        
        try {
            documentumUserName = System.getenv("DocumentumUser"); // Fetching Documentum Username
        } catch (Exception e) {
        	e.printStackTrace();
            System.out.println("Could not extract Documentum User Name.");
            DIRBulkUpdate.logger.error("Could not extract Documentum User Name.");
            System.exit(0);
        }
        String documentumUserPass = null;
        try {
            documentumUserPass = PrimaryThreadForBu.decryptEnvVar("DocumentumPw", decryptor); // Fetching and decrypting Documentum password
        } catch (Exception e) {
        	e.printStackTrace();
            System.out.println("Could not extract Documentum User Password.");
            DIRBulkUpdate.logger.error("Could not extract Documentum User Password.");
            System.exit(0);
        }
        String docBase = null;
        try {
            docBase = System.getenv("Docbase"); // Fetching Docbase
        } catch (Exception e) {
        	e.printStackTrace();
            System.out.println("Could not extract Documentum docBase.");
            DIRBulkUpdate.logger.error("Could not extract Documentum docbase.");
            System.exit(0);
        }
        
        System.out.println("Documentum User: " + documentumUserName);
        System.out.println("Documentum Docbase: " + docBase);
        
        DIRBulkUpdate.logger.info("Documentum User={}, Docbase={}", documentumUserName, docBase);
        
        System.out.println("Documentum session initialization started: docBase: " + docBase);
        DIRBulkUpdate.logger.info("Documentum session initialization started: docBase: {}", docBase);
        
        IDfClient client = new DfClient();
        IDfSessionManager sessionManager = client.newSessionManager();
        IDfSession session = null;
        try {
        	// Initializing Documentum session.
            session = createDocumentumSession(documentumUserName, documentumUserPass, docBase, sessionManager); 
        } catch (DfException e) {
        	e.printStackTrace();
            System.out.println("Could not connect to docbase.");
            DIRBulkUpdate.logger.error("Could not connect to docbase.");
            System.exit(0);
        }
        if (session == null) {
            System.out.println("Could not connect to docbase.");
            DIRBulkUpdate.logger.error("Could not connect to docbase.");
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
		}else if(docBase.contains("dmcspo") || docBase.contains("DMCSPO")) { // R4A
			buName = "CREG";
		} else if(docBase.contains("sntb") || docBase.contains("SNTB")) { // R2C
			buName = "STCO";
		}
        
        System.out.println("Initialized BuName: " + buName);
        DIRBulkUpdate.logger.info("Initialized BuName: {}", buName);
        
        System.out.println("Loading Data Dictionary: " + buName);
        DIRBulkUpdate.logger.info("Loading Data Dictionary: " + buName);
        
        try {
        	// fetching Data Dictionary from docbase
			prop = getD2DictionaryConfigValues(session);
			// R4A
			if(prop == null || prop.isEmpty()) {
				System.out.println("Data dictionary is empty or not present in " + buName);
				logger.info("Data dictionary is empty or not present in " + buName);
				System.exit(0);
			}
		} catch (DfException e) {
			e.printStackTrace();
			System.out.println("Could not fetch DD.");
            DIRBulkUpdate.logger.info("Could not fetch DD.");
            System.exit(0);
		}
        
        System.out.println("Starting Primary Thread for BU: " + buName);
        DIRBulkUpdate.logger.info("Starting Primary Thread for BU: " + buName);
        // Initializing primary method 
        PrimaryThreadForBu buThread = new PrimaryThreadForBu(buName, prop, session, docBase);
        buThread.executeForBu();
        
        System.out.println("DIR Creation completed for BU: " + buName);
        DIRBulkUpdate.logger.info("DIR Creation completed for BU: " + buName);
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
			 e.printStackTrace();
		 } finally {
			 if (collectionObj != null) {
				 collectionObj.close();
			 }
		 }
		 if (!configValues.isEmpty()) {
			 d2DDConfig.putAll(configValues);	// populating Properties object with key value pairs from DD.
		 }
//		 System.out.println("Data Dictionary values: " + d2DDConfig);
		 return d2DDConfig;
	 }
    /*
     * This function takes the documentum username, password, session manager and docbase as arguments
     * and creates a Documentum Session.
     */
   protected static IDfSession createDocumentumSession(String user, String password, String docbase, IDfSessionManager sessionManager) throws DfException {
       IDfSession Session = null;
       System.out.println("Creating Documentum session for Docbase: " + docbase);
       logger.info("Creating Documentum session for Docbase: " + docbase);
       IDfLoginInfo loginInfo = new DfLoginInfo();
       loginInfo.setUser(user);
       loginInfo.setPassword(password);
       try {
           sessionManager.setIdentity(docbase, loginInfo);
           Session = sessionManager.getSession(docbase);
           System.out.println(docbase+" Documentum session created!");
           logger.info(docbase+" Documentum session created!");
       } catch (Exception e) {
           System.err.println("Failed to create Documentum session: " + e.getMessage());
           logger.error("Failed to create Documentum session: " + e.getMessage());
       }
       return Session;
   }
}