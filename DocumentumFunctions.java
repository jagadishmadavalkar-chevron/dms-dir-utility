package DIRBulkUpdate;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import com.documentum.fc.client.DfQuery;
import com.documentum.fc.client.IDfCollection;

import com.documentum.fc.client.IDfQuery;
import com.documentum.fc.client.IDfSession;
import com.documentum.fc.common.DfException;

public class DocumentumFunctions {

	protected static void updateParentTags(BUProperties bu) {
		IDfQuery query = new DfQuery();
		String documentFetchDql = String.format(bu.relatedTagsDql, DIRBulkUpdate.prop.getProperty("type"),
				bu.dateOfLastRun, DIRBulkUpdate.prop.getProperty("statusForDir"),
				DIRBulkUpdate.prop.getProperty("doctype"), DIRBulkUpdate.prop.getProperty("documentTypeToSkip"));
			
		System.out.println("relatedTagsDocumentFetchDql: " + documentFetchDql);
		query.setDQL(documentFetchDql);
		IDfCollection collection = null;
		try {
			collection = query.execute(bu.session, IDfQuery.DF_READ_QUERY);
			if (collection == null) {
				System.err.println("There are no documents in " + bu.buName + " D2 to process.");
			} else {
				while (collection.next()) {
					String rObjId = collection.getString(Constants.dbDocId);
					executeRelatedTags(bu, rObjId);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println(bu.buName + ":" + Thread.currentThread().getName() + ": Error :" + e.getMessage());
		}
	}

	/*
	 * This function is used to pull the directory containing all the input files
	 * from the BUs D2 Repository. It returns a count of the number of files within
	 * the directory, and assigns the actual directory contents to the BU
	 * properties.
	 */
	protected static void checkInputDocuments(BUProperties bu) throws DfException {

		IDfQuery query = new DfQuery();
		System.out.println(bu.dql);
		String fields = DIRBulkUpdate.prop.getProperty("inputAttrsr2") + "," + Constants.d2DirField;

		// select title, r_object_id,i_chronicle_id,%s from %s where r_modify_date >=
		// '%s'
		// and a_status in (%s) and %s not in (%s) and any r_version_label='CURRENT';

		// selecting title, r_object_id, ichronicle_id, and gom_document;
		String documentFetchDql = String.format(bu.dql, fields, DIRBulkUpdate.prop.getProperty("type"),
				bu.dateOfLastRun, DIRBulkUpdate.prop.getProperty("statusForDir"),
				DIRBulkUpdate.prop.getProperty("doctype"), DIRBulkUpdate.prop.getProperty("documentTypeToSkip"));// ,DIRBulkUpdate.prop.getProperty("documentTypeToSkip"));

		System.out.println(documentFetchDql);
		query.setDQL(documentFetchDql);
		IDfCollection collection = null;
		try {
			collection = query.execute(bu.session, IDfQuery.DF_READ_QUERY);
			if (collection == null) {
				DatabaseFunctions.updateDbError(bu, "NA", "No documents in D2 to process. ", Constants.completedStatus,
						null, "NA", null);
				System.err.println("There are no documents in " + bu.buName + " D2 to process.");
				DIRBulkUpdate.logger.info("There are no documents in " + bu.buName + " D2 to process.");
			} else {
				while (collection.next()) {
					Map<String, String> input = new HashMap<>();
					Map<String, String> dirMap = new HashMap<>();
					Integer valueCount = 0;
					String[] ids;
					valueCount = collection.getValueCount(DIRBulkUpdate.prop.getProperty("FLOCField"));
					if (valueCount != 0 && !collection.getRepeatingString(DIRBulkUpdate.prop.getProperty("FLOCField"), 0).trim().isEmpty()) {
						ids = new String[valueCount];
						for (int i = 0; i < valueCount; i++) {
							ids[i] = collection.getRepeatingString(DIRBulkUpdate.prop.getProperty("FLOCField"), i);
						}
						input.put(bu.attrIndex.get(DIRBulkUpdate.prop.getProperty("FLOCField")),
								StringUtils.join(ids, ","));// "FLOC - sap_xecm_floc_tags");
						// flocLabels =
						// flocLabels+","+Arrays.stream(ids).map(key->"'"+key+"'").collect(Collectors.joining(","));
					}

					valueCount = collection.getValueCount(DIRBulkUpdate.prop.getProperty("TLSTField"));
					if (valueCount != 0 && !collection.getRepeatingString(DIRBulkUpdate.prop.getProperty("TLSTField"), 0).trim().isEmpty()) {
						ids = new String[valueCount];
						for (int i = 0; i < valueCount; i++) {
							ids[i] = collection.getRepeatingString(DIRBulkUpdate.prop.getProperty("TLSTField"), i);
						}
						input.put(bu.attrIndex.get(DIRBulkUpdate.prop.getProperty("TLSTField")),
								StringUtils.join(ids, ","));// "FLOC - sap_xecm_floc_tags");

					}
					valueCount = collection.getValueCount(DIRBulkUpdate.prop.getProperty("IPLNField"));
					if (valueCount != 0 && !collection.getRepeatingString(DIRBulkUpdate.prop.getProperty("IPLNField"), 0).trim().isEmpty()) {
						ids = new String[valueCount];
						for (int i = 0; i < valueCount; i++) {
							ids[i] = collection.getRepeatingString(DIRBulkUpdate.prop.getProperty("IPLNField"), i);
						}
						input.put(bu.attrIndex.get(DIRBulkUpdate.prop.getProperty("IPLNField")),
								StringUtils.join(ids, ","));// "FLOC - sap_xecm_floc_tags");

					}
					valueCount = collection.getValueCount(DIRBulkUpdate.prop.getProperty("WRDRField"));
					if (valueCount != 0 && !collection.getRepeatingString(DIRBulkUpdate.prop.getProperty("WRDRField"), 0).trim().isEmpty()) {
						ids = new String[valueCount];
						for (int i = 0; i < valueCount; i++) {
							ids[i] = collection.getRepeatingString(DIRBulkUpdate.prop.getProperty("WRDRField"), i);
						}
						input.put(bu.attrIndex.get(DIRBulkUpdate.prop.getProperty("WRDRField")),
								StringUtils.join(ids, ","));// "FLOC - sap_xecm_floc_tags");

					}
					valueCount = collection.getValueCount(DIRBulkUpdate.prop.getProperty("EQUIField"));
					if (valueCount != 0 && !collection.getRepeatingString(DIRBulkUpdate.prop.getProperty("EQUIField"), 0).trim().isEmpty()) {
						ids = new String[valueCount];
						for (int i = 0; i < valueCount; i++) {
							ids[i] = collection.getRepeatingString(DIRBulkUpdate.prop.getProperty("EQUIField"), i);
						}
						input.put(bu.attrIndex.get(DIRBulkUpdate.prop.getProperty("EQUIField")),
								StringUtils.join(ids, ","));// "FLOC - sap_xecm_floc_tags");

					}

					bu.buMap.put(collection.getString(Constants.d2ChronicleId), input);
					bu.buMapWithPlants.put(collection.getString(Constants.d2ChronicleId), ""); // R4As

					if (!(collection.getString(Constants.d2DirField).equals(null)
							|| collection.getString(Constants.d2DirField).equals(""))) {
						dirMap.put(collection.getString(Constants.dbDocId), collection.getString(Constants.d2DirField));
					} else {
						dirMap.put(collection.getString(Constants.dbDocId), "");
					}
					bu.docsWithNewVersion.put(collection.getString(Constants.d2ChronicleId), dirMap);
					bu.titles.put(collection.getString(Constants.d2ChronicleId),
							collection.getString(Constants.d2TitleField));

					// System.out.println(bu.buName + ":" + Thread.currentThread().getName() + ":
					// New Document--"+(count+1)+". id: "+input.get(Constants.dbDocId)+", isAveva:
					// "+input.get(Constants.fileSrcAveva)+", name:
					// "+input.get(Constants.d2ObjName));
					// DIRBulkUpdate.logger.info(bu.buName + ":" + Thread.currentThread().getName()
					// + ": New Document--"+(count+1)+". id: "+input.get(Constants.dbDocId)+",
					// isAveva: "+input.get(Constants.fileSrcAveva)+", name:
					// "+input.get(Constants.d2ObjName));
					// System.out.println(bu.docsWithNewVersion);

				}

//				System.out.println("bu.buMap from checkInputDocuments Evergreen: " + bu.buMap);
//				System.out
//						.println("bu.docsWithNewVersion from checkInputDocuments Evergreen: " + bu.docsWithNewVersion);
//				System.out.println("bu.titles from checkInputDocuments Evergreen: " + bu.titles);
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println(bu.buName + ":" + Thread.currentThread().getName() + ": Error :" + e.getMessage());
			DIRBulkUpdate.logger
					.info(bu.buName + ":" + Thread.currentThread().getName() + ": Error :" + e.getMessage());
			DatabaseFunctions.updateDbError(bu, "NA", "Could not fetch documents. " + e.getMessage(),
					Constants.errorStatus, null, "NA", null);
		} finally {
			if (collection != null) {
				collection.close();
			}
		}

	}

	// R4A
	protected static void FetchPlanningPlants(BUProperties bu, String operation) {
		IDfQuery query = new DfQuery();
		String planningPlantFetchDql = "";
		String d2ObjectType = "";
		IDfCollection collection = null;
		JSONObject buMap = new JSONObject();
		if(operation == "CREATE") {
			buMap = bu.buMap;
		} else {
			buMap = bu.buMapForUpdate;
		}

		// Map through buMap & update the plant details in buMapWithPlants itself.
		for (String docId : buMap.keySet()) {

			JSONObject docValue = buMap.getJSONObject(docId);

//			System.out.println("docValue: " + docValue);
//			System.out.println("docValue length: " + docValue.length());

			if (docValue == null || docValue.length() == 0) {
				bu.buMapWithPlants.put(docId, "No object links");
				continue; // empty inner object
			}

			// Get the first key from inner JSON
			String objectKey = docValue.keys().next();
			
			// Get its value and pick the first value before comma
			String objectName = docValue.optString(objectKey).split(",")[0].trim();
			if (objectKey.contains("_create") || objectKey.contains("_remove")) {
				objectKey = objectKey.split("_")[0].trim();
			} 
			d2ObjectType = Constants.objTypes.get(objectKey);
			planningPlantFetchDql = String.format(bu.planningPlantDql, d2ObjectType, objectName);
//			System.out.println("planningPlantFetchDql: " + planningPlantFetchDql);
			query.setDQL(planningPlantFetchDql);

			try {
				String objName = "";
				String planningPlant = "";
				collection = query.execute(bu.session, IDfQuery.DF_READ_QUERY);
				if (collection == null) {
					System.out.println("No planning plant values present");
					DIRBulkUpdate.logger.info(bu.buName + ":" + Thread.currentThread().getName() + ": No planning plant values present in D2");
				} else {
					while (collection.next()) {
						objName = collection.getString(Constants.d2ObjectName);
						planningPlant = collection.getString(Constants.d2PlanningPlant);
//						System.out.println("objectName: " + objName);
//						System.out.println("PlanningPlant: " + planningPlant);
					}
				}

				if (planningPlant != null && !planningPlant.isEmpty()) {
					bu.buMapWithPlants.put(docId, planningPlant);
				} else {
					bu.buMapWithPlants.put(docId, "No planning plant");
				}
			} catch (Exception e) {
				System.out.println("Error in planning plant fetch operation: " + e);
				DIRBulkUpdate.logger.info(bu.buName + ":" + Thread.currentThread().getName() + ": Error in planning plant fetch operation:" + e.getMessage());
			}

//			System.out.println("docId = " + docId);
//			System.out.println("firstKey = " + objectKey);
//			System.out.println("firstValue = " + objectName);
//			System.out.println("------------------------------");
		}

	}

	// R4A
	protected static void FetchPlanningPlantsDeleted(BUProperties bu) {
		IDfQuery query = new DfQuery();
		String planningPlantFetchDql = "";
		IDfCollection collection = null;
		String d2ObjectType = "";

		String deleteIds = "";
		for (String chronicleId : bu.buMapForDelete.keySet()) {
			deleteIds = deleteIds + ",'" + chronicleId + "'";
		}

		deleteIds = deleteIds.substring(1);
		System.out.println("DeleteIds: " + deleteIds);
		String fetchDeleteIdsPlanningPlantsSql = String.format(bu.fetchDeleteIdsPlanningPlantsSql, bu.processingTable,
				deleteIds);

		try {
			PreparedStatement stmt = bu.conn.prepareStatement(fetchDeleteIdsPlanningPlantsSql);
			ResultSet rs = stmt.executeQuery();
			while (rs.next()) {
				String docId = rs.getString(Constants.d2ChronicleId);
				String plant = rs.getString("plant");
				if (plant != null) {
					System.out.println("Plant available in database: ObjectId: " + docId + " Plant: " + plant);
					bu.buMapForDeleteWithPlants.put(docId, plant);
				} else {
					String boIds = rs.getString("bo_ids");
					JSONObject boIdsJson = new JSONObject(boIds);

					String objectKey = boIdsJson.keys().next();
					String objectName = boIdsJson.getString(objectKey).split(",")[0].trim();
//					System.out.println("DELETE OP objectKey: " + objectKey);
//					System.out.println("DELETE OP objectName: " + objectName);
					if (objectKey.contains("_create") || objectKey.contains("_remove")) {
						objectKey = objectKey.split("_")[0].trim();
					} else {
						d2ObjectType = Constants.objTypes.get(objectKey);
						planningPlantFetchDql = String.format(bu.planningPlantDql, d2ObjectType, objectName);
						query.setDQL(planningPlantFetchDql);

						try {
							String objName = "";
							String planningPlant = "";
							collection = query.execute(bu.session, IDfQuery.DF_READ_QUERY);
							if (collection == null) {
								System.out.println("No planning plant values present");
								DIRBulkUpdate.logger.info(bu.buName + ":" + Thread.currentThread().getName() + ": No planning plant values present");	
							} else {
								while (collection.next()) {
									objName = collection.getString("object_name");
									planningPlant = collection.getString("maint_plant");
//									System.out.println("DELETE Operation: objectName: " + objName);
//									System.out.println("DELETE Operation: planningPlant: " + planningPlant);
								}
							}

							if (planningPlant != null && !planningPlant.isEmpty()) {
								bu.buMapForDeleteWithPlants.put(docId, planningPlant);
							} else {
								bu.buMapForDeleteWithPlants.put(docId, "No planning plant");
							}

						} catch (Exception e) {
							System.out.println("Error in planning plant fetch operation: DELETE : " + e.getMessage());
							DIRBulkUpdate.logger.info(bu.buName + ":" + Thread.currentThread().getName() + ": Error in planning plant fetch operation: DELETE : " + e.getMessage());
						}
					}
				}

			}
			

		} catch (Exception e) {
			System.out.println("Error in fetching plants from DB: Operation: DELETE : ERROR: " + e.getMessage());
			DIRBulkUpdate.logger.info(bu.buName + ":" + Thread.currentThread().getName() + ": Error in fetching plants from DB: Operation: DELETE : ERROR: " + e.getMessage());
			
		}

	}

	protected static int checkDeleteDocuments(BUProperties bu) throws DfException {
		int count = 0;
		IDfQuery queryTrail = new DfQuery();
//		System.out.println(bu.deleteTrailDql);
		String deleteTrailDql = String.format(bu.deleteTrailDql, bu.dateOfLastRun);
//		System.out.println(deleteTrailDql);
		queryTrail.setDQL(deleteTrailDql);

		IDfCollection collection = null;
		try {
			String input = "";
			collection = queryTrail.execute(bu.session, IDfQuery.DF_READ_QUERY);
			if (collection == null) {
				System.err.println("There are no report in " + bu.buName + " D2 to process.");
				DIRBulkUpdate.logger.info("There are no report in " + bu.buName + " D2 to process.");
			} else {
				while (collection.next()) {
					input = input + ",'" + collection.getString(Constants.d2AuditedField) + "'";
				}
				collection.close();
			}

			if (!input.equals("")) {
				input = input.substring(1);
//				System.out.println("result: " + input);
				PreparedStatement stmt = bu.conn
						.prepareStatement(String.format(bu.deleteSql, bu.processingTable, input));
				System.out.println(stmt);
				ResultSet rs = stmt.executeQuery();
				input = "";
				while (rs.next()) {
					if (!(rs.getString(Constants.dbDirNumberCol).equals(null)
							|| rs.getString(Constants.dbDirNumberCol).equals(""))) {
						bu.buMapForDelete.put(rs.getString(Constants.d2ChronicleId),
								rs.getString(Constants.dbDirNumberCol));
						input = input + ",'" + rs.getString(Constants.d2ChronicleId) + "'";
					}

				}

			}

			if (!input.equals("")) {
				input = input.substring(1);
				IDfCollection collectionForDirsNeeded = null;
				IDfQuery query = new DfQuery();
//				System.out.println(bu.deleteDql);
				String deleteDql = String.format(bu.deleteDql, DIRBulkUpdate.prop.getProperty("type"), input,
						DIRBulkUpdate.prop.getProperty("statusForDir"));
				System.out.println(deleteDql);
				query.setDQL(deleteDql);
				collectionForDirsNeeded = query.execute(bu.session, IDfQuery.DF_READ_QUERY);
				while (collectionForDirsNeeded.next()) {
					if (bu.buMapForDelete.containsKey(collectionForDirsNeeded.getString(Constants.d2ChronicleId))) {
						bu.buMapForDelete.remove(collectionForDirsNeeded.getString(Constants.d2ChronicleId));

					}
				}
			}



			System.out.println("There are no documents left to process.");
			DIRBulkUpdate.logger.info("There are no documents left to process.");
		} catch (Exception e) {
			System.out.println(bu.buName + ":" + Thread.currentThread().getName() + ": Error :" + e.getMessage());
			DIRBulkUpdate.logger
					.info(bu.buName + ":" + Thread.currentThread().getName() + ": Error :" + e.getMessage());
			count = 0;
		} finally {
			if (collection != null) {
				collection.close();
			}
		}

		return count;
	}

	public static void addDirNumber(String dirNumber, String r_object_id, BUProperties bu, Integer sno,
			String message) {
		// TODO Auto-generated method stub
		try {
			IDfQuery query = new DfQuery();
			String documentUpdateDql = String.format(bu.updateDql, DIRBulkUpdate.prop.getProperty("type"), dirNumber,
					r_object_id);
			System.out.println(documentUpdateDql);
			query.setDQL(documentUpdateDql);
			query.execute(bu.session, IDfQuery.DF_EXEC_QUERY);
			PreparedStatement stmt = bu.conn.prepareStatement(bu.processingDbCreateUpdateSql);
			stmt.setString(1, dirNumber);
			stmt.setString(2, Constants.completedStatus);
			stmt.setString(3, message);
			stmt.setInt(4, sno);
			System.out.println(stmt);
			stmt.execute();
			System.out.println("DIR Number Added to Document");
		} catch (Exception e) {
			System.out.println(bu.buName + ":" + Thread.currentThread().getName() + ": Error :" + e.getMessage());
			DIRBulkUpdate.logger
					.info(bu.buName + ":" + Thread.currentThread().getName() + ": Error :" + e.getMessage());
			DatabaseFunctions.updateDbPostBapi(bu, sno,
					"DIR Created Successfully but Document not updated. " + e.getMessage(),
					Constants.partCompletedStatus, dirNumber);
		}
	}

	public static void addDirNumberWhichFailed(BUProperties bu) {
		// TODOAuto-generated method stub
		try {
			String updateDirSql = String.format(DIRBulkUpdate.prop.getProperty("selectDocumentsForDocUpdate"),
					bu.processingTable, Constants.partCompletedStatus, bu.buName);
			System.out.println(updateDirSql);
			PreparedStatement docStmt = bu.conn.prepareStatement(updateDirSql);
			ResultSet rs = docStmt.executeQuery();
			if (rs.isBeforeFirst()) {
				while (rs.next()) {
					String dirNumber = rs.getString(Constants.dbDirNumberCol);
					Integer sno = rs.getInt(Constants.dbSnoCol);
					String r_object_id = rs.getString(Constants.dbDocId);
					IDfQuery query = new DfQuery();
					String documentUpdateDql = String.format(bu.updateDql, DIRBulkUpdate.prop.getProperty("type"),
							dirNumber, r_object_id);
					System.out.println(documentUpdateDql);
					query.setDQL(documentUpdateDql);
					query.execute(bu.session, IDfQuery.DF_QUERY);
					PreparedStatement stmt = bu.conn.prepareStatement(bu.processingDbCreateUpdateSql);
					stmt.setString(1, dirNumber);
					stmt.setString(2, Constants.completedStatus);
					stmt.setString(3, "DIR successfully created and Document updated.");
					stmt.setInt(4, sno);
					System.out.println(stmt);
					stmt.execute();
					System.out.println("DIR Number Added to Document");
				}
			}
		} catch (Exception e) {
			System.out.println(bu.buName + ":" + Thread.currentThread().getName() + ": Error :" + e.getMessage());
			DIRBulkUpdate.logger
					.info(bu.buName + ":" + Thread.currentThread().getName() + ": Error :" + e.getMessage());
			e.printStackTrace();

		}
	}

	public static void finaliseCutoverInput(String documents, BUProperties bu) {
		IDfQuery query = new DfQuery();
		// System.out.println(bu.cutoverDql);
		String documentFetchDql = String.format(bu.cutoverDql, DIRBulkUpdate.prop.getProperty("type"), documents);// ,DIRBulkUpdate.prop.getProperty("documentTypeToSkip"));
		System.out.println(documentFetchDql);
		query.setDQL(documentFetchDql);
		IDfCollection collection = null;

		try {
			collection = query.execute(bu.session, IDfQuery.DF_READ_QUERY);
			if (collection == null) {
				DatabaseFunctions.updateDbError(bu, "NA", "No documents in D2 to process. ", Constants.completedStatus,
						null, "NA", null);
				System.err.println("There are no report in " + bu.buName + " D2 to process.");
				DIRBulkUpdate.logger.info("There are no report in " + bu.buName + " D2 to process.");
			} else {
				while (collection.next()) {
					// new imp for related tags
					Map<String, String> input = new HashMap<>();
					Integer valueCount = 0;
					String[] ids;
					valueCount = collection.getValueCount(DIRBulkUpdate.prop.getProperty("FLOCField"));
					System.out.println("floc valueCount: " + valueCount);
					if (valueCount != 0) {
						ids = new String[valueCount];
						for (int i = 0; i < valueCount; i++) {
							ids[i] = collection.getRepeatingString(DIRBulkUpdate.prop.getProperty("FLOCField"), i);
						}
						input.put(bu.attrIndex.get(DIRBulkUpdate.prop.getProperty("FLOCField")),
								StringUtils.join(ids, ","));// "FLOC - sap_xecm_floc_tags");
						// flocLabels =
						// flocLabels+","+Arrays.stream(ids).map(key->"'"+key+"'").collect(Collectors.joining(","));
					}

					valueCount = collection.getValueCount(DIRBulkUpdate.prop.getProperty("TLSTField"));
					System.out.println("TLST valueCount: " + valueCount);
					if (valueCount != 0) {
						ids = new String[valueCount];
						for (int i = 0; i < valueCount; i++) {
							ids[i] = collection.getRepeatingString(DIRBulkUpdate.prop.getProperty("TLSTField"), i);
						}
						input.put(bu.attrIndex.get(DIRBulkUpdate.prop.getProperty("TLSTField")),
								StringUtils.join(ids, ","));// "FLOC - sap_xecm_floc_tags");

					}
					valueCount = collection.getValueCount(DIRBulkUpdate.prop.getProperty("IPLNField"));
					if (valueCount != 0) {
						ids = new String[valueCount];
						for (int i = 0; i < valueCount; i++) {
							ids[i] = collection.getRepeatingString(DIRBulkUpdate.prop.getProperty("IPLNField"), i);
						}
						input.put(bu.attrIndex.get(DIRBulkUpdate.prop.getProperty("IPLNField")),
								StringUtils.join(ids, ","));// "FLOC - sap_xecm_floc_tags");

					}
					valueCount = collection.getValueCount(DIRBulkUpdate.prop.getProperty("WRDRField"));
					System.out.println("WRDR valueCount: " + valueCount);
					if (valueCount != 0) {
						ids = new String[valueCount];
						for (int i = 0; i < valueCount; i++) {
							ids[i] = collection.getRepeatingString(DIRBulkUpdate.prop.getProperty("WRDRField"), i);
						}
						input.put(bu.attrIndex.get(DIRBulkUpdate.prop.getProperty("WRDRField")),
								StringUtils.join(ids, ","));// "FLOC - sap_xecm_floc_tags");

					}
					valueCount = collection.getValueCount(DIRBulkUpdate.prop.getProperty("EQUIField"));
					System.out.println("EQUI valueCount: " + valueCount);
					if (valueCount != 0) {
						ids = new String[valueCount];
						for (int i = 0; i < valueCount; i++) {
							ids[i] = collection.getRepeatingString(DIRBulkUpdate.prop.getProperty("EQUIField"), i);
						}
						input.put(bu.attrIndex.get(DIRBulkUpdate.prop.getProperty("EQUIField")),
								StringUtils.join(ids, ","));// "FLOC - sap_xecm_floc_tags");

					}
					bu.buMap.put(collection.getString(Constants.d2ChronicleId), input);
					bu.buMapWithPlants.put(collection.getString(Constants.d2ChronicleId), "");
					// end new imp for related tags

					Map<String, String> dirMap = new HashMap<>();
					String objectId = collection.getString(Constants.dbDocId);
					String chronicleId = collection.getString(Constants.d2ChronicleId);
					String dirNumber = collection.getString(Constants.d2DirField);
					// System.out.println("C: "+chronicleId+": R: "+objectId+": D: "+dirNumber);
					/*
					 * if(!objectId.equals(chronicleId)) {
					 * bu.buMap.put(chronicleId,bu.buMap.get(objectId)); bu.buMap.remove(objectId);
					 * }
					 */
					if (!(dirNumber.equals(null) || dirNumber.equals(""))) {
						bu.docsWithDir.put(chronicleId, dirNumber);
						dirMap.put(objectId, dirNumber);
					} else {
						dirMap.put(objectId, "");
					}
					bu.docsWithNewVersion.put(chronicleId, dirMap);
					// System.out.println(bu.docsWithNewVersion);
					bu.titles.put(chronicleId, collection.getString(Constants.d2TitleField));
				}

				System.out.println("bu.buMap from checkInputDocuments cutover: " + bu.buMap);
				System.out
						.println("bu.docsWithNewVersion from checkInputDocuments cutover: " + bu.docsWithNewVersion);
				System.out.println("bu.titles from checkInputDocuments cutover: " + bu.titles);

				System.out.println(">>>>>>>>>Fetching planning plants from workspace during cutover<<<<<<<<<<<");
//				FetchPlanningPlants(bu);
			}
		} catch (Exception e) {
			System.out.println(bu.buName + ":" + Thread.currentThread().getName() + ": Error :" + e.getMessage());
			DIRBulkUpdate.logger
					.info(bu.buName + ":" + Thread.currentThread().getName() + ": Error :" + e.getMessage());

		}

	}

	public static void getFlocAlternateLabels(BUProperties bu) {
		String flocLabels = "";

		for (String entry : bu.buMap.keySet()) {
			if (bu.buMap.get(entry).toString().contains("FLOC\":")) {
				JSONObject boIds = (JSONObject) bu.buMap.get(entry);
				String[] flocIds = boIds.get("FLOC").toString().split(",");
				for (String floc : flocIds) {
					flocLabels = flocLabels + ",'" + floc + "'";
				}
			}
		}
		for (String entry : bu.buMapForUpdate.keySet()) {
			if (bu.buMapForUpdate.get(entry).toString().contains("FLOC_create\":")) {
				JSONObject boIds = (JSONObject) bu.buMapForUpdate.get(entry);
				String[] flocIds = boIds.get("FLOC_create").toString().split(",");
				for (String floc : flocIds) {
					flocLabels = flocLabels + ",'" + floc + "'";
				}
			}
			if (bu.buMapForUpdate.get(entry).toString().contains("FLOC_remove\":")) {
				JSONObject boIds = (JSONObject) bu.buMapForUpdate.get(entry);
				String[] flocIds = boIds.get("FLOC_remove").toString().split(",");
				for (String floc : flocIds) {
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
	}

	public static void getAlternateLabelsFromD2(BUProperties bu) {
		String date;
		if (DIRBulkUpdate.prop.getProperty("populateFlocDb").equals("True")) {
			date = "01/01/2020";
		} else {
			date = bu.dateOfLastRun;
		}
		String flocDql = String.format(bu.flocDql, DIRBulkUpdate.prop.getProperty("flocTable"), date,
				DIRBulkUpdate.prop.getProperty("sapSystem"));

		System.out.println(bu.buName + ":" + Thread.currentThread().getName() + ": " + flocDql);
		IDfQuery flocQuery = new DfQuery();
		flocQuery.setDQL(flocDql);
		IDfCollection flocCollection;
		try {
			flocCollection = flocQuery.execute(bu.session, IDfQuery.DF_READ_QUERY);
			if (flocCollection == null) {
				System.err.println(bu.buName + ":" + Thread.currentThread().getName() + ": "
						+ "There are no new FLOCs in " + bu.buName + " D2");
				DIRBulkUpdate.logger.info(bu.buName + ":" + Thread.currentThread().getName() + ": "
						+ "There are no new FLOCs in " + bu.buName + " D2");
			} else {
				PreparedStatement stmtFloc = bu.conn.prepareStatement(bu.flocDbUpdateSql);
				bu.conn.setAutoCommit(false);
				while (flocCollection.next()) {
					if (!(flocCollection.getString(Constants.d2AlternateLabel) == null
							|| flocCollection.getString(Constants.d2AlternateLabel).equals(""))) {
						System.out.println("AlternateLabel: " + flocCollection.getString(Constants.d2AlternateLabel));
						stmtFloc.setString(2, flocCollection.getString(Constants.d2AlternateLabel));
						stmtFloc.setString(1, flocCollection.getString(Constants.d2ObjectName));
						stmtFloc.setString(3, bu.buName);
						stmtFloc.addBatch();
					}
				}
				stmtFloc.executeBatch();
				bu.conn.commit();
				bu.conn.setAutoCommit(true);
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println(bu.buName + ":" + Thread.currentThread().getName() + ": "
					+ "Error while fetching FLOCs from " + bu.buName + " D2");
			DIRBulkUpdate.logger.info(bu.buName + ":" + Thread.currentThread().getName() + ": "
					+ "Error while FLOCs from " + bu.buName + " D2");
		}
	}

	protected static void executeRelatedTags(BUProperties bu, String r_object_id) {
		
		// R4A
//		if(DIRBulkUpdate.prop.getProperty("cutOverFlag").equalsIgnoreCase("True")) {
//			 IDfQuery query = new DfQuery();
//			 String relatedTagsDql = String.format("execute do_method with method='XECMTag2DocServiceMethod', arguments='-docbase_name %s -user dmadmin -id %s -naming false -autolink true -security false -apply_for_vd false-create false -transaction false';",bu.docBase,r_object_id);
//			 System.out.println(relatedTagsDql);
//			 query.setDQL(relatedTagsDql);
//			 try {
//				 query.execute(bu.session, IDfQuery.DF_QUERY);				
//			} catch (Exception e) {
//				// TODO: handle exception
//				System.out.println("Error n executeRelatedTags: " + e);
//			}
//		}
		
		//IDfQuery query = new DfQuery();
        //String relatedTagsDql = String.format("execute do_method with method='XECMTag2DocServiceMethod', arguments='-docbase_name %s -user dmadmin -id %s -naming false -autolink true -security false -apply_for_vd false -create false -transaction false';",bu.docBase,r_object_id);
        //System.out.println(relatedTagsDql);
        //query.setDQL(relatedTagsDql);

		
		try {
			//query.execute(bu.session, IDfQuery.DF_QUERY);
			XECMDoc2TagServiceMethod doc2tagsvc = new XECMDoc2TagServiceMethod();
			Properties config = getD2DictionaryConfigValues(bu.session);
//			System.out.println("relatedTags getD2DictionaryConfigValues config properties: " + config);
			doc2tagsvc.processTag2DocInfo(config, bu.session, r_object_id);
			doc2tagsvc.processDocLink2Tags(bu.session, r_object_id);
			System.out.println("Related Tags Method successful for:" + r_object_id);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Related Tags Method failed for:" + r_object_id);
		}
	}

	private static Properties getD2DictionaryConfigValues(IDfSession dctmSession) throws DfException {
		Properties d2DDConfig = new Properties();
		HashMap<String, String> configValues = new HashMap<String, String>();
		IDfQuery queryObj = new DfQuery();
		IDfCollection collectionObj = null;
		try {
			queryObj.setDQL(
					"select r_object_id,object_name,locale_value from d2_dictionary_value where dictionary_name='xecmtag2docconfig'");
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
			d2DDConfig.putAll(configValues); // populating Properties object with key value pairs from DD.
		}
		return d2DDConfig;
	}

}
