package DIRBulkUpdate;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.Properties;
import org.json.JSONObject;





public class DatabaseFunctions {

	/*
     * This function is used to make a connection to the database. It takes in
     * the connection details and returns the connection.
     */
    protected static Connection connectToDatabase(String jdbcURL, String dBUsername, String dBPassword) throws SQLException {
        Connection connection = null;
        try {
            connection = DriverManager.getConnection(jdbcURL, dBUsername, dBPassword);
            DIRBulkUpdate.logger.info("Database connection successful");
        } catch (SQLException e) {
            DIRBulkUpdate.logger.error("Database connection failed", e);
        }
        return connection;
    }

	/*
     * This function is used to query the DB for staging and processing batch numbers.
     * It takes the properties, the DB connection, and the BU name. It returns an
     * array containing the staging batch number and the processing batch number.
     */
    protected static Integer getMaxBatchValue(Properties prop, Connection conn, String buName) throws SQLException {
        String processingDBtableSql = String.format(prop.getProperty("maxBatchNumber"), prop.getProperty("processingDBtable"));
        Integer batchno;
        try (Statement statementp = conn.createStatement();
             ResultSet processingDBtablers = statementp.executeQuery(processingDBtableSql)) {
            if (processingDBtablers.next()) {
                batchno = processingDBtablers.getInt(1) + 1;
                DIRBulkUpdate.logger.info("{}:{} [INIT] processingDBtableMaxbatchno={}", buName, Thread.currentThread().getName(), batchno);
            } else {
                DIRBulkUpdate.logger.info("{}:{} [INIT] First processing batch for {}", buName, Thread.currentThread().getName(), buName);
                batchno = 1;
            }
        } catch (SQLException e) {
            DIRBulkUpdate.logger.error("{}:{} [INIT] Database batch query failed", buName, Thread.currentThread().getName(), e);
            throw e;
        }
        return batchno;
    }

    protected static String getDateOfLastRun(Properties prop, Connection conn, String buName) throws SQLException {
        String processingDBtableSql = String.format(prop.getProperty("lastRunDate"), prop.getProperty("processingDBtable"), buName);
        String dateOfLastRun;
        try (Statement statementp = conn.createStatement();
             ResultSet processingDBtablers = statementp.executeQuery(processingDBtableSql)) {
            if (processingDBtablers.next()) {
                dateOfLastRun = processingDBtablers.getString(1);
                DIRBulkUpdate.logger.info("{}:{} [INIT] processingDBtableLastRun={}", buName, Thread.currentThread().getName(), dateOfLastRun);
            } else {
                DIRBulkUpdate.logger.info("{}:{} [INIT] First processing batch for {}", buName, Thread.currentThread().getName(), buName);
                dateOfLastRun = "01/01/2020";
            }
        } catch (SQLException e) {
            DIRBulkUpdate.logger.error("{}:{} [INIT] Database last-run-date query failed", buName, Thread.currentThread().getName(), e);
            throw e;
        }
        return dateOfLastRun;
    }
    
    

	public static void populateStagingTable(BUProperties bu) throws SQLException {
		try (PreparedStatement stmtStaging = bu.conn.prepareStatement(bu.stagingDbUpdateSql)) {
			bu.conn.setAutoCommit(false);
			for (String key : bu.buMap.keySet()) {
                String objectId = key;
                String boIds = "";
                bu.messages.put(objectId, "Business Objects For DIR: " + bu.buMap.get(objectId).toString() + " | ");
                boIds = bu.buMap.get(key).toString();
                stmtStaging.setString(2, boIds);
                stmtStaging.setString(1, objectId);
                stmtStaging.setString(3, bu.buName);
                stmtStaging.setInt(4, bu.batchNo);
                stmtStaging.addBatch();
            }

            stmtStaging.executeBatch();
            bu.conn.commit();
            bu.conn.setAutoCommit(true);
		} catch (SQLException e) {
			bu.conn.rollback();
			DIRBulkUpdate.logger.error("{}:{} [STAGING] Error updating staging table", bu.buName, Thread.currentThread().getName(), e);
		}
		
	}

	public static void populateProcessingTable(BUProperties bu) {
	        
        try (PreparedStatement stmt = bu.conn.prepareStatement(bu.processingDbInsertStmt)) {
        	bu.conn.setAutoCommit(false);
            for (String entry : bu.buMap.keySet()) {
                String objectId = entry;
                String boIds = bu.buMap.get(entry).toString();
                DIRBulkUpdate.logger.debug("{}:{} [PROCESS] objectId={} boIds={}", bu.buName, Thread.currentThread().getName(), objectId, boIds);
                DIRBulkUpdate.logger.debug("{}:{} [PROCESS] plant={}", bu.buName, Thread.currentThread().getName(), bu.buMapWithPlants.get(objectId));

                stmt.setString(1, objectId);
                stmt.setString(2, bu.buName);
                stmt.setString(3, boIds);
                stmt.setInt(4, bu.batchNo);
                stmt.setString(5, Constants.process);
                stmt.setString(6, Constants.create);
                stmt.setString(7, null);
                stmt.setString(8, bu.messages.get(objectId));
                stmt.setString(9, bu.docsWithNewVersion.get(objectId).keySet().iterator().next());
                stmt.setString(10, bu.titles.get(objectId));
	            stmt.setString(11, bu.buMapWithPlants.get(objectId).toString()); // R4A
                stmt.addBatch();
            }
	            

            for (String entry : bu.buMapForUpdate.keySet()) {
            	String objectId = entry;
                String boIds = bu.buMapForUpdate.get(entry).toString();
                String dirNumber = bu.docsWithDir.get(entry);
                DIRBulkUpdate.logger.debug("{}:{} [PROCESS] UPDATE objectId={} boIds={} dirNumber={}", bu.buName, Thread.currentThread().getName(), objectId, boIds, dirNumber);
                DIRBulkUpdate.logger.debug("{}:{} [PROCESS] UPDATE plant={}", bu.buName, Thread.currentThread().getName(), bu.buMapWithPlants.get(objectId));

                stmt.setString(1, objectId);
                stmt.setString(2, bu.buName);
                stmt.setString(3, boIds);
                stmt.setInt(4, bu.batchNo);
                stmt.setString(5, Constants.process);
                stmt.setString(6, Constants.update);
                stmt.setString(7, dirNumber);
                stmt.setString(8, bu.messages.get(objectId));
                stmt.setString(9, bu.docsWithNewVersion.get(objectId).keySet().iterator().next());
                stmt.setString(10, bu.titles.get(objectId));
	            stmt.setString(11, bu.buMapWithPlants.get(objectId).toString()); // R4A
                stmt.addBatch();
            }

            for (Map.Entry<String, String> entry : bu.buMapForDelete.entrySet()) {
                String objectId = entry.getKey();
                String dirNumber = entry.getValue();
                DIRBulkUpdate.logger.debug("{}:{} [PROCESS] DELETE objectId={} plant={}", bu.buName, Thread.currentThread().getName(), objectId, bu.buMapForDeleteWithPlants.get(objectId));

                stmt.setString(1, objectId);
                stmt.setString(2, bu.buName);
                stmt.setString(3, null);
                stmt.setInt(4, bu.batchNo);
                stmt.setString(5, Constants.process);
                stmt.setString(6, Constants.delete);
                stmt.setString(7, dirNumber);
                stmt.setString(8, null);
                stmt.setString(9, null);
                stmt.setString(10, null);
                stmt.setString(11, bu.buMapForDeleteWithPlants.get(objectId).toString());
                stmt.addBatch();
            }
            stmt.executeBatch();
            bu.conn.commit();
            bu.conn.setAutoCommit(true);
        } catch (Exception e) {
        	DIRBulkUpdate.logger.error("{}:{} [PROCESS] Error populating processing table", bu.buName, Thread.currentThread().getName(), e);
        }
	}

	public static void findDeltas(BUProperties bu) {
		List<String> keys = new ArrayList<String>();
		try {
			String objectIds = bu.buMap.keySet().stream().map(key -> "'" + key + "'").collect(Collectors.joining(","));
			if (!objectIds.equals("")) {
				PreparedStatement dirStmt = bu.conn.prepareStatement(String.format(bu.dirQuery, bu.processingTable, objectIds, bu.buName));
				DIRBulkUpdate.logger.debug("{}:{} [DELTA] dirQuery: {}", bu.buName, Thread.currentThread().getName(), dirStmt.toString());
				ResultSet dirRs = dirStmt.executeQuery();
				while (dirRs.next()) {
					if (!(dirRs.getString(Constants.dbDirNumberCol) == null || dirRs.getString(Constants.dbDirNumberCol).equals(""))) {
						bu.docsWithDir.put(dirRs.getString(Constants.d2ChronicleId), dirRs.getString(Constants.dbDirNumberCol));
					} else {
						DIRBulkUpdate.logger.debug("{}:{} [DELTA] {}: No existing DIR", bu.buName, Thread.currentThread().getName(), dirRs.getString(Constants.d2ChronicleId));
					}
				}
			}
		} catch (Exception e) {
			DIRBulkUpdate.logger.error("{}:{} [DELTA] Error querying DIR numbers", bu.buName, Thread.currentThread().getName(), e);
		}

		for (String entry : bu.buMap.keySet()) {
			String objectId = entry;
			if (bu.docsWithDir.containsKey(objectId)) {
				Boolean result = false;
				try (PreparedStatement queryStmt = bu.conn.prepareStatement(bu.compareQuery)) {
                    for (int i = 1; i <= 6; i++) {
                        if (i % 3 == 0) {
                            queryStmt.setString(i, bu.buName);
                        } else {
                            queryStmt.setString(i, objectId);
                        }
                    }
                    ResultSet queryRs = queryStmt.executeQuery();
                    queryRs.next();
                    result = queryRs.getBoolean(1);
				} catch (Exception e) {
					DIRBulkUpdate.logger.error("{}:{} [DELTA] Error in compare query for objectId={}", bu.buName, Thread.currentThread().getName(), objectId, e);
				}
				if (result) {
					String boIdsOld = null;
					String boIdsNew = null;
					try (PreparedStatement oldDocStmt = bu.conn.prepareStatement(bu.selectOldTagsSql);
							PreparedStatement newDocStmt = bu.conn.prepareStatement(bu.selectNewTagsSql)) {
			            oldDocStmt.setString(1, objectId);
			            oldDocStmt.setString(2, bu.buName);
			            ResultSet rsOld = oldDocStmt.executeQuery();
			            while (rsOld.next()) {
			            	boIdsOld = rsOld.getString(Constants.dbTags);
			            }
			            newDocStmt.setString(1, objectId);
			            newDocStmt.setString(2, bu.buName);
			            ResultSet rsNew = newDocStmt.executeQuery();
			            while (rsNew.next()) {
			            	boIdsNew = rsNew.getString(Constants.dbTags);
			            }
			            if (boIdsOld == null || boIdsOld.equals("")) {
			            	continue;
			            }
			            JSONObject boIdsOldJson = new JSONObject(boIdsOld);
			            JSONObject boIdsJson = new JSONObject(boIdsNew);
			            String[] inputAttrs = ("EQUI,FLOC,TLST,IPLN,WRDR").split(",");
			            Map<String, String> boIdMap = new HashMap<>();
			            for (int i = 0; i < inputAttrs.length; i++) {
			            	String newIds = "";
			            	String oldIds = "";
			            	String addToDir = "";
			            	String removeFromDir = "";
			            	if (boIdsJson.has(inputAttrs[i])) {
			            		newIds = boIdsJson.get(inputAttrs[i]).toString();
			            	}
			            	if (boIdsOldJson.has(inputAttrs[i])) {
			            		oldIds = boIdsOldJson.get(inputAttrs[i]).toString();
			            	}
			            	if (!(oldIds.equals("") && newIds.equals(""))) {
			            		if (oldIds.equals("") || newIds.equals("")) {
			            			addToDir = newIds;
			            			removeFromDir = oldIds;
			            		} else {
			            			List<String> newIdList = new ArrayList<String>(Arrays.asList(newIds.split(",")));
			            			List<String> newIdListTemp = new ArrayList<String>(Arrays.asList(newIds.split(",")));
			            			List<String> oldIdList = new ArrayList<String>(Arrays.asList(oldIds.split(",")));
			            			newIdList.removeAll(oldIdList);
			            			oldIdList.removeAll(newIdListTemp);
			            			addToDir = String.join(",", newIdList);
			            			removeFromDir = String.join(",", oldIdList);
			            		}
			            		if (!addToDir.equals("")) {
			            			boIdMap.put(inputAttrs[i] + "_create", addToDir);
			            		}
			            		if (!removeFromDir.equals("")) {
			            			boIdMap.put(inputAttrs[i] + "_remove", removeFromDir);
			            		}
			            	}
			            	bu.buMapForUpdate.put(objectId, boIdMap);
			            	keys.add(objectId);
			            }
					} catch (Exception e) {
						DIRBulkUpdate.logger.error("{}:{} [DELTA] Error comparing tags for objectId={}", bu.buName, Thread.currentThread().getName(), objectId, e);
						keys.add(objectId);
					}
				} else {
					keys.add(objectId);
					bu.messages.remove(objectId);
				}
			}
		}

		for (String entry : keys) {
			if (bu.buMap.has(entry)) {
				bu.buMap.remove(entry);
			}
		}
	}

	public static void fetchDocuments(BUProperties bu) {
		String sql = String.format(DIRBulkUpdate.prop.getProperty("selectUpdatedDocumentsSql"), bu.processingTable118, Constants.completedStatus, bu.buName);
		try (PreparedStatement docStmt = bu.conn.prepareStatement(sql);
			ResultSet rs = docStmt.executeQuery()) {
			String documents = "";
			while (rs.next()) {
				String rObjId = rs.getString(Constants.dbDocId);
                DocumentumFunctions.executeRelatedTags(bu, rObjId);
				documents = documents + ",'" + rs.getString(Constants.dbDocId) + "'";
			}
			if (!documents.equals("")) {
				documents = documents.substring(1);
				DocumentumFunctions.finaliseCutoverInput(documents, bu);
			} else {
				DIRBulkUpdate.logger.warn("{}:{} [FETCH] No documents to process", bu.buName, Thread.currentThread().getName());
			}
		} catch (SQLException e) {
			DIRBulkUpdate.logger.error("{}:{} [FETCH] Error fetching documents", bu.buName, Thread.currentThread().getName(), e);
		}
	}

	public static void processDirs(BUProperties bu) {
		ExecutorService esSap = Executors.newFixedThreadPool(5);
		DIRBulkUpdate.logger.info("{}:{} [BAPI] Calling BAPIs", bu.buName, Thread.currentThread().getName());
		DIRBulkUpdate.logger.info("{}:{} [BAPI] Fetching auth token", bu.buName, Thread.currentThread().getName());
        String sql = getAuthToken(bu);
        sql = String.format(DIRBulkUpdate.prop.getProperty("selectDocumentsForBapiSql"), bu.processingTable, "readytoprocess", bu.buName);
		try (PreparedStatement docStmt = bu.conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			 ResultSet rs = docStmt.executeQuery()) {
			if (!rs.isBeforeFirst()) {
				DatabaseFunctions.updateDbError(bu, "NA", "No documents to process. ", Constants.completedStatus, null, "NA", null);
			} else {
				rs.last();
		        CountDownLatch sapLatch = new CountDownLatch(rs.getRow());
		        rs.beforeFirst();
				while (rs.next()) {
				    String modString = rs.getString(Constants.dbTags);
				    String operation = rs.getString(Constants.dbOperationCol);
				    if (operation.equals(Constants.delete)) {
				    	modString = "{}";
				    }
				    JSONObject boIdsJson = new JSONObject(modString);
					String r_object_id = rs.getString(Constants.d2ChronicleId);
					String flocLabels = "";
					if (boIdsJson.has("FLOC_create")) {
						String[] flocIds = boIdsJson.get("FLOC_create").toString().split(",");
						for (String floc : flocIds) {
							if (!bu.flocLabelToAlternate.containsKey(floc)) {
								flocLabels = flocLabels + ",'" + floc + "'";
							}
						}
					}
					if (boIdsJson.has("FLOC_remove")) {
						String[] flocIds = boIdsJson.get("FLOC_remove").toString().split(",");
						for (String floc : flocIds) {
							if (!bu.flocLabelToAlternate.containsKey(floc)) {
								flocLabels = flocLabels + ",'" + floc + "'";
							}
						}
					}
					if (!flocLabels.equals("")) {
			        	flocLabels = flocLabels.substring(1);
			        	if (!flocLabels.equals("")) {
			        		DatabaseFunctions.getAlternateLabelsFromDb(flocLabels, bu);
			        	}
			        }
					Integer sno = rs.getInt(Constants.dbSnoCol);
					String dirNumber = rs.getString(Constants.dbDirNumberCol);
					String planningPlant = rs.getString(Constants.dbPlant); // R4A
					String title = rs.getString(Constants.d2TitleField); // R4A
					esSap.execute(new SAPOperations(boIdsJson, r_object_id, operation, bu, sno, sapLatch, dirNumber, planningPlant, title));// R4A
				}
				sapLatch.await();
			}
			DIRBulkUpdate.logger.info("{}:{} [BAPI] No records left to process", bu.buName, Thread.currentThread().getName());
            esSap.shutdown();
		} catch (Exception e) {
			DIRBulkUpdate.logger.error("{}:{} [BAPI] Error in processDirs", bu.buName, Thread.currentThread().getName(), e);
		}
	}

	public static void updateDbPostBapi(BUProperties bu, Integer sno, String message, String status, String dirNumber) {
		try {
			PreparedStatement stmt = bu.conn.prepareStatement(bu.processingDbUpdateSql);
			stmt.setString(1, status);
			stmt.setString(2, message);
			stmt.setString(3, dirNumber);
			stmt.setInt(4, sno);
			stmt.execute();
		} catch (Exception e) {
			DIRBulkUpdate.logger.error("{}:{} [BAPI] Error updating DB post-BAPI sno={}", bu.buName, Thread.currentThread().getName(), sno, e);
		}
	}

	public static void updateDbError(BUProperties bu, String r_object_id, String message, String status, String boIds, String operation, String dirNumber) {
		try {
			PreparedStatement stmt = bu.conn.prepareStatement(bu.processingDbInsertStmt);
			stmt.setString(1, r_object_id);
            stmt.setString(2, bu.buName);
            stmt.setString(3, boIds);
            stmt.setInt(4, bu.batchNo);
            stmt.setString(5, status);
            stmt.setString(6, operation);
            stmt.setString(7, dirNumber);
            stmt.setString(8, message);
            stmt.setString(9, null);
            stmt.setString(10, null);
            stmt.setString(11, null); // R4A
            
			stmt.execute();
		} catch (Exception e) {
			DIRBulkUpdate.logger.error("{}:{} [BAPI] Error inserting error row objectId={}", bu.buName, Thread.currentThread().getName(), r_object_id, e);
		}
	}

	public static void updateDeletedDocRows(BUProperties bu) {
		try {
			String updateDelSql = String.format(bu.megaDeleteSelectSql, bu.processingTable, Constants.delete, bu.buName, Constants.completedStatus);
			DIRBulkUpdate.logger.debug("{}:{} [CLEANUP] updateDeletedDocRows SQL: {}", bu.buName, Thread.currentThread().getName(), updateDelSql);
			PreparedStatement docStmt = bu.conn.prepareStatement(updateDelSql);
			ResultSet rs = docStmt.executeQuery();
			String chronicleIds = "";
			if (rs.isBeforeFirst()) {
				while (rs.next()) {
					chronicleIds = chronicleIds + ",'" + rs.getString(Constants.d2ChronicleId) + "'";
				}
				chronicleIds = chronicleIds.substring(1);
				PreparedStatement stmt = bu.conn.prepareStatement(String.format(bu.megaDeleteUpdateSql, bu.processingTable, "DELETED", chronicleIds, bu.buName));
				DIRBulkUpdate.logger.debug("{}:{} [CLEANUP] megaDeleteUpdate SQL: {}", bu.buName, Thread.currentThread().getName(), stmt);
				stmt.execute();
				DIRBulkUpdate.logger.info("{}:{} [CLEANUP] Deleted DIR records updated", bu.buName, Thread.currentThread().getName());
			}
		} catch (Exception e) {
			DIRBulkUpdate.logger.error("{}:{} [CLEANUP] Error updating deleted doc rows", bu.buName, Thread.currentThread().getName(), e);
		}
	}

	public static void cleanUpStagingTable(Connection conn, String selectSql, String processingTable, String buName, Integer batchNo, String deleteSql, String stagingTable) {
		try {
			String selectErrorSql = String.format(selectSql, processingTable, buName, batchNo);
			DIRBulkUpdate.logger.debug("{}:{} [CLEANUP] cleanUpStagingTable SQL: {}", buName, Thread.currentThread().getName(), selectErrorSql);
			PreparedStatement docStmt = conn.prepareStatement(selectErrorSql);
			ResultSet rs = docStmt.executeQuery();
			String chronicleIds = "";
			if (rs.isBeforeFirst()) {
				while (rs.next()) {
					if (!rs.getString(Constants.d2ChronicleId).equals("NA")) {
						chronicleIds = chronicleIds + ",'" + rs.getString(Constants.d2ChronicleId) + "'";
					}
				}
				chronicleIds = chronicleIds.substring(1);
				PreparedStatement stmt = conn.prepareStatement(String.format(deleteSql, stagingTable, buName, batchNo, chronicleIds));
				DIRBulkUpdate.logger.debug("{}:{} [CLEANUP] cleanStagingDelete SQL: {}", buName, Thread.currentThread().getName(), stmt);
				stmt.execute();
				DIRBulkUpdate.logger.info("{}:{} [CLEANUP] Error staging records cleaned up", buName, Thread.currentThread().getName());
			}
		} catch (Exception e) {
			DIRBulkUpdate.logger.error("{}:{} [CLEANUP] Error cleaning staging table", buName, Thread.currentThread().getName(), e);
		}
	}

	public static void getAlternateLabelsFromDb(String flocLabels, BUProperties bu) {
		String flocSql = String.format(bu.flocSql, bu.flocTable, flocLabels, bu.buName);
		DIRBulkUpdate.logger.debug("{}:{} [FLOC] getAlternateLabelsFromDb SQL: {}", bu.buName, Thread.currentThread().getName(), flocSql);
		try {
			PreparedStatement flocQuery = bu.conn.prepareStatement(flocSql);
			ResultSet rs = flocQuery.executeQuery();
			if (rs == null) {
                DIRBulkUpdate.logger.warn("{}:{} [FLOC] No alternate labels in DB for {}", bu.buName, Thread.currentThread().getName(), flocLabels);
            } else {
            	while (rs.next()) {
                	bu.flocLabelToAlternate.put(rs.getString(Constants.dbLabel), rs.getString(Constants.dbAlternateLabel));
            	}
            }
		} catch (Exception e) {
			DIRBulkUpdate.logger.warn("{}:{} [FLOC] Error fetching alternate labels from DB for {}", bu.buName, Thread.currentThread().getName(), flocLabels, e);
		}
	}

	public static synchronized String getAuthToken(BUProperties bu) {
		try {
				String encodedPassword = URLEncoder.encode(bu.apimPassword, StandardCharsets.UTF_8.toString()); // R4A
				String body = String.format(Constants.authTokenCallPayload, bu.clientId, bu.clientSecret, bu.resource, bu.apimUsername, encodedPassword);
				HttpRequest request = (HttpRequest) HttpRequest.newBuilder()
						.uri(URI.create(bu.authEndpoint))
						.header("Content-Type", "application/x-www-form-urlencoded")
						.method("GET", HttpRequest.BodyPublishers.ofString(body)).build();
				HttpResponse<String> response = bu.clientAuth.send(request, HttpResponse.BodyHandlers.ofString());
				JSONObject res = new JSONObject(response.body());
				bu.authToken = res;
				DIRBulkUpdate.logger.info("{}:{} [BAPI] Auth token acquired", bu.buName, Thread.currentThread().getName());
				return res.getString("access_token");
			} catch (Exception e) {
				//R4A
			    if (bu.authTokenFlag.compareAndSet(false, true)) {
			    	DatabaseFunctions.updateDbError(bu, "NA", "Could not fetch Auth Token", Constants.errorStatus, null, "NA", null);
			        DatabaseFunctions.updateProcessToError(bu); // R4A
			    }
				JSONObject authError = new JSONObject();
				authError.put("expires_in", "1");
				bu.authToken = authError;
				DIRBulkUpdate.logger.error("{}:{} [BAPI] Could not fetch auth token", bu.buName, Thread.currentThread().getName(), e);
				return "";
			}
		}

	/*
	 * R4A - For updating the readytoprocess entries to ERROR in case of token error for the current processing batch
	 */
	public static void updateProcessToError(BUProperties bu) {
		try {
			PreparedStatement stmt = bu.conn.prepareStatement(bu.processingDbUpdateProcessErrorSql);
			stmt.setString(1, Constants.errorStatus);
			stmt.setString(2, "Could not fetch Auth token");
			stmt.setString(3, Constants.process);
			stmt.setInt(4, bu.batchNo);
			stmt.setString(5, bu.buName);
			stmt.execute();
		} catch (Exception e) {
			DIRBulkUpdate.logger.error("{}:{} [BAPI] Error updating processing table to error status", bu.buName, Thread.currentThread().getName(), e);
		}
	}
}
