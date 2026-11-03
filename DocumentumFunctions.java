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

		DIRBulkUpdate.logger.debug("{}:{} [DCTM] relatedTagsDocumentFetchDql: {}", bu.buName, Thread.currentThread().getName(), documentFetchDql);
		query.setDQL(documentFetchDql);
		IDfCollection collection = null;
		try {
			collection = query.execute(bu.session, IDfQuery.DF_READ_QUERY);
			if (collection == null) {
				DIRBulkUpdate.logger.warn("{}:{} [DCTM] No documents in D2 to process", bu.buName, Thread.currentThread().getName());
			} else {
				while (collection.next()) {
					String rObjId = collection.getString(Constants.dbDocId);
					executeRelatedTags(bu, rObjId);
				}
			}
		} catch (Exception e) {
			DIRBulkUpdate.logger.error("{}:{} [DCTM] Error in updateParentTags", bu.buName, Thread.currentThread().getName(), e);
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
		DIRBulkUpdate.logger.debug("{}:{} [DCTM] dql template: {}", bu.buName, Thread.currentThread().getName(), bu.dql);
		String fields = DIRBulkUpdate.prop.getProperty("inputAttrsr2") + "," + Constants.d2DirField;

		String documentFetchDql = String.format(bu.dql, fields, DIRBulkUpdate.prop.getProperty("type"),
				bu.dateOfLastRun, DIRBulkUpdate.prop.getProperty("statusForDir"),
				DIRBulkUpdate.prop.getProperty("doctype"), DIRBulkUpdate.prop.getProperty("documentTypeToSkip"));

		DIRBulkUpdate.logger.debug("{}:{} [DCTM] checkInputDocuments DQL: {}", bu.buName, Thread.currentThread().getName(), documentFetchDql);
		query.setDQL(documentFetchDql);
		IDfCollection collection = null;
		try {
			collection = query.execute(bu.session, IDfQuery.DF_READ_QUERY);
			if (collection == null) {
				DatabaseFunctions.updateDbError(bu, "NA", "No documents in D2 to process. ", Constants.completedStatus,
						null, "NA", null);
				DIRBulkUpdate.logger.warn("{}:{} [DCTM] No documents in D2 to process", bu.buName, Thread.currentThread().getName());
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
						input.put(bu.attrIndex.get(DIRBulkUpdate.prop.getProperty("FLOCField")), StringUtils.join(ids, ","));
					}

					valueCount = collection.getValueCount(DIRBulkUpdate.prop.getProperty("TLSTField"));
					if (valueCount != 0 && !collection.getRepeatingString(DIRBulkUpdate.prop.getProperty("TLSTField"), 0).trim().isEmpty()) {
						ids = new String[valueCount];
						for (int i = 0; i < valueCount; i++) {
							ids[i] = collection.getRepeatingString(DIRBulkUpdate.prop.getProperty("TLSTField"), i);
						}
						input.put(bu.attrIndex.get(DIRBulkUpdate.prop.getProperty("TLSTField")), StringUtils.join(ids, ","));
					}
					valueCount = collection.getValueCount(DIRBulkUpdate.prop.getProperty("IPLNField"));
					if (valueCount != 0 && !collection.getRepeatingString(DIRBulkUpdate.prop.getProperty("IPLNField"), 0).trim().isEmpty()) {
						ids = new String[valueCount];
						for (int i = 0; i < valueCount; i++) {
							ids[i] = collection.getRepeatingString(DIRBulkUpdate.prop.getProperty("IPLNField"), i);
						}
						input.put(bu.attrIndex.get(DIRBulkUpdate.prop.getProperty("IPLNField")), StringUtils.join(ids, ","));
					}
					valueCount = collection.getValueCount(DIRBulkUpdate.prop.getProperty("WRDRField"));
					if (valueCount != 0 && !collection.getRepeatingString(DIRBulkUpdate.prop.getProperty("WRDRField"), 0).trim().isEmpty()) {
						ids = new String[valueCount];
						for (int i = 0; i < valueCount; i++) {
							ids[i] = collection.getRepeatingString(DIRBulkUpdate.prop.getProperty("WRDRField"), i);
						}
						input.put(bu.attrIndex.get(DIRBulkUpdate.prop.getProperty("WRDRField")), StringUtils.join(ids, ","));
					}
					valueCount = collection.getValueCount(DIRBulkUpdate.prop.getProperty("EQUIField"));
					if (valueCount != 0 && !collection.getRepeatingString(DIRBulkUpdate.prop.getProperty("EQUIField"), 0).trim().isEmpty()) {
						ids = new String[valueCount];
						for (int i = 0; i < valueCount; i++) {
							ids[i] = collection.getRepeatingString(DIRBulkUpdate.prop.getProperty("EQUIField"), i);
						}
						input.put(bu.attrIndex.get(DIRBulkUpdate.prop.getProperty("EQUIField")), StringUtils.join(ids, ","));
					}

					bu.buMap.put(collection.getString(Constants.d2ChronicleId), input);
					bu.buMapWithPlants.put(collection.getString(Constants.d2ChronicleId), ""); // R4A

					if (!(collection.getString(Constants.d2DirField).equals(null)
							|| collection.getString(Constants.d2DirField).equals(""))) {
						dirMap.put(collection.getString(Constants.dbDocId), collection.getString(Constants.d2DirField));
					} else {
						dirMap.put(collection.getString(Constants.dbDocId), "");
					}
					bu.docsWithNewVersion.put(collection.getString(Constants.d2ChronicleId), dirMap);
					bu.titles.put(collection.getString(Constants.d2ChronicleId), collection.getString(Constants.d2TitleField));
				}

				DIRBulkUpdate.logger.debug("{}:{} [DCTM] checkInputDocuments buMap: {}", bu.buName, Thread.currentThread().getName(), bu.buMap);
				DIRBulkUpdate.logger.debug("{}:{} [DCTM] checkInputDocuments docsWithNewVersion: {}", bu.buName, Thread.currentThread().getName(), bu.docsWithNewVersion);
				DIRBulkUpdate.logger.debug("{}:{} [DCTM] checkInputDocuments titles: {}", bu.buName, Thread.currentThread().getName(), bu.titles);
			}
		} catch (Exception e) {
			DIRBulkUpdate.logger.error("{}:{} [DCTM] Error in checkInputDocuments", bu.buName, Thread.currentThread().getName(), e);
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
		if (operation == "CREATE") {
			buMap = bu.buMap;
		} else {
			buMap = bu.buMapForUpdate;
		}

		for (String docId : buMap.keySet()) {
			JSONObject docValue = buMap.getJSONObject(docId);
			DIRBulkUpdate.logger.debug("{}:{} [PLANTS] docId={} docValue={}", bu.buName, Thread.currentThread().getName(), docId, docValue);

			if (docValue == null || docValue.length() == 0) {
				bu.buMapWithPlants.put(docId, "No object links");
				continue;
			}

			String objectKey = docValue.keys().next();
			String objectName = docValue.optString(objectKey).split(",")[0].trim();
			if (objectKey.contains("_create") || objectKey.contains("_remove")) {
				objectKey = objectKey.split("_")[0].trim();
			}
			d2ObjectType = Constants.objTypes.get(objectKey);
			planningPlantFetchDql = String.format(bu.planningPlantDql, d2ObjectType, objectName);
			DIRBulkUpdate.logger.debug("{}:{} [PLANTS] planningPlantFetchDql: {}", bu.buName, Thread.currentThread().getName(), planningPlantFetchDql);
			query.setDQL(planningPlantFetchDql);

			try {
				String objName = "";
				String planningPlant = "";
				collection = query.execute(bu.session, IDfQuery.DF_READ_QUERY);
				if (collection == null) {
					DIRBulkUpdate.logger.warn("{}:{} [PLANTS] No planning plant values present in D2", bu.buName, Thread.currentThread().getName());
				} else {
					while (collection.next()) {
						objName = collection.getString(Constants.d2ObjectName);
						planningPlant = collection.getString(Constants.d2PlanningPlant);
						DIRBulkUpdate.logger.debug("{}:{} [PLANTS] objectName={} planningPlant={}", bu.buName, Thread.currentThread().getName(), objName, planningPlant);
					}
				}

				if (planningPlant != null && !planningPlant.isEmpty()) {
					bu.buMapWithPlants.put(docId, planningPlant);
				} else {
					bu.buMapWithPlants.put(docId, "No planning plant");
				}
			} catch (Exception e) {
				DIRBulkUpdate.logger.error("{}:{} [PLANTS] Error in planning plant fetch operation", bu.buName, Thread.currentThread().getName(), e);
			}

			DIRBulkUpdate.logger.debug("{}:{} [PLANTS] docId={} firstKey={} firstValue={}", bu.buName, Thread.currentThread().getName(), docId, objectKey, objectName);
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
		DIRBulkUpdate.logger.debug("{}:{} [PLANTS] DELETE chronicleIds: {}", bu.buName, Thread.currentThread().getName(), deleteIds);
		String fetchDeleteIdsPlanningPlantsSql = String.format(bu.fetchDeleteIdsPlanningPlantsSql, bu.processingTable, deleteIds);

		try {
			PreparedStatement stmt = bu.conn.prepareStatement(fetchDeleteIdsPlanningPlantsSql);
			ResultSet rs = stmt.executeQuery();
			while (rs.next()) {
				String docId = rs.getString(Constants.d2ChronicleId);
				String plant = rs.getString("plant");
				if (plant != null) {
					DIRBulkUpdate.logger.debug("{}:{} [PLANTS] DELETE plant from DB: objectId={} plant={}", bu.buName, Thread.currentThread().getName(), docId, plant);
					bu.buMapForDeleteWithPlants.put(docId, plant);
				} else {
					String boIds = rs.getString("bo_ids");
					JSONObject boIdsJson = new JSONObject(boIds);

					String objectKey = boIdsJson.keys().next();
					String objectName = boIdsJson.getString(objectKey).split(",")[0].trim();
					DIRBulkUpdate.logger.debug("{}:{} [PLANTS] DELETE OP objectKey={} objectName={}", bu.buName, Thread.currentThread().getName(), objectKey, objectName);
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
								DIRBulkUpdate.logger.warn("{}:{} [PLANTS] No planning plant values present for DELETE", bu.buName, Thread.currentThread().getName());
							} else {
								while (collection.next()) {
									objName = collection.getString("object_name");
									planningPlant = collection.getString("maint_plant");
									DIRBulkUpdate.logger.debug("{}:{} [PLANTS] DELETE Operation: objectName={} planningPlant={}", bu.buName, Thread.currentThread().getName(), objName, planningPlant);
								}
							}

							if (planningPlant != null && !planningPlant.isEmpty()) {
								bu.buMapForDeleteWithPlants.put(docId, planningPlant);
							} else {
								bu.buMapForDeleteWithPlants.put(docId, "No planning plant");
							}

						} catch (Exception e) {
							DIRBulkUpdate.logger.error("{}:{} [PLANTS] Error in planning plant fetch for DELETE", bu.buName, Thread.currentThread().getName(), e);
						}
					}
				}
			}

		} catch (Exception e) {
			DIRBulkUpdate.logger.error("{}:{} [PLANTS] Error fetching plants from DB for DELETE", bu.buName, Thread.currentThread().getName(), e);
		}
	}

	protected static int checkDeleteDocuments(BUProperties bu) throws DfException {
		int count = 0;
		IDfQuery queryTrail = new DfQuery();
		String deleteTrailDql = String.format(bu.deleteTrailDql, bu.dateOfLastRun);
		queryTrail.setDQL(deleteTrailDql);

		IDfCollection collection = null;
		try {
			String input = "";
			collection = queryTrail.execute(bu.session, IDfQuery.DF_READ_QUERY);
			if (collection == null) {
				DIRBulkUpdate.logger.warn("{}:{} [DCTM] No audit trail in D2 to process", bu.buName, Thread.currentThread().getName());
			} else {
				while (collection.next()) {
					input = input + ",'" + collection.getString(Constants.d2AuditedField) + "'";
				}
				collection.close();
			}

			if (!input.equals("")) {
				input = input.substring(1);
				PreparedStatement stmt = bu.conn.prepareStatement(String.format(bu.deleteSql, bu.processingTable, input));
				DIRBulkUpdate.logger.debug("{}:{} [DCTM] checkDeleteDocuments SQL: {}", bu.buName, Thread.currentThread().getName(), stmt);
				ResultSet rs = stmt.executeQuery();
				input = "";
				while (rs.next()) {
					if (!(rs.getString(Constants.dbDirNumberCol).equals(null)
							|| rs.getString(Constants.dbDirNumberCol).equals(""))) {
						bu.buMapForDelete.put(rs.getString(Constants.d2ChronicleId), rs.getString(Constants.dbDirNumberCol));
						input = input + ",'" + rs.getString(Constants.d2ChronicleId) + "'";
					}
				}
			}

			if (!input.equals("")) {
				input = input.substring(1);
				IDfCollection collectionForDirsNeeded = null;
				IDfQuery query = new DfQuery();
				String deleteDql = String.format(bu.deleteDql, DIRBulkUpdate.prop.getProperty("type"), input,
						DIRBulkUpdate.prop.getProperty("statusForDir"));
				DIRBulkUpdate.logger.debug("{}:{} [DCTM] deleteDql: {}", bu.buName, Thread.currentThread().getName(), deleteDql);
				query.setDQL(deleteDql);
				collectionForDirsNeeded = query.execute(bu.session, IDfQuery.DF_READ_QUERY);
				while (collectionForDirsNeeded.next()) {
					if (bu.buMapForDelete.containsKey(collectionForDirsNeeded.getString(Constants.d2ChronicleId))) {
						bu.buMapForDelete.remove(collectionForDirsNeeded.getString(Constants.d2ChronicleId));
					}
				}
			}

			DIRBulkUpdate.logger.info("{}:{} [DCTM] checkDeleteDocuments complete", bu.buName, Thread.currentThread().getName());
		} catch (Exception e) {
			DIRBulkUpdate.logger.error("{}:{} [DCTM] Error in checkDeleteDocuments", bu.buName, Thread.currentThread().getName(), e);
			count = 0;
		} finally {
			if (collection != null) {
				collection.close();
			}
		}

		return count;
	}

	public static void addDirNumber(String dirNumber, String r_object_id, BUProperties bu, Integer sno, String message) {
		try {
			IDfQuery query = new DfQuery();
			String documentUpdateDql = String.format(bu.updateDql, DIRBulkUpdate.prop.getProperty("type"), dirNumber, r_object_id);
			DIRBulkUpdate.logger.debug("{}:{} [DCTM] addDirNumber DQL: {}", bu.buName, Thread.currentThread().getName(), documentUpdateDql);
			query.setDQL(documentUpdateDql);
			query.execute(bu.session, IDfQuery.DF_EXEC_QUERY);
			PreparedStatement stmt = bu.conn.prepareStatement(bu.processingDbCreateUpdateSql);
			stmt.setString(1, dirNumber);
			stmt.setString(2, Constants.completedStatus);
			stmt.setString(3, message);
			stmt.setInt(4, sno);
			DIRBulkUpdate.logger.debug("{}:{} [DCTM] addDirNumber update SQL: {}", bu.buName, Thread.currentThread().getName(), stmt);
			stmt.execute();
			DIRBulkUpdate.logger.info("{}:{} [DCTM] DIR Number added to document sno={}", bu.buName, Thread.currentThread().getName(), sno);
		} catch (Exception e) {
			DIRBulkUpdate.logger.error("{}:{} [DCTM] Error adding DIR number sno={}", bu.buName, Thread.currentThread().getName(), sno, e);
			DatabaseFunctions.updateDbPostBapi(bu, sno,
					"DIR Created Successfully but Document not updated. " + e.getMessage(),
					Constants.partCompletedStatus, dirNumber);
		}
	}

	public static void addDirNumberWhichFailed(BUProperties bu) {
		try {
			String updateDirSql = String.format(DIRBulkUpdate.prop.getProperty("selectDocumentsForDocUpdate"),
					bu.processingTable, Constants.partCompletedStatus, bu.buName);
			DIRBulkUpdate.logger.debug("{}:{} [DCTM] addDirNumberWhichFailed SQL: {}", bu.buName, Thread.currentThread().getName(), updateDirSql);
			PreparedStatement docStmt = bu.conn.prepareStatement(updateDirSql);
			ResultSet rs = docStmt.executeQuery();
			if (rs.isBeforeFirst()) {
				while (rs.next()) {
					String dirNumber = rs.getString(Constants.dbDirNumberCol);
					Integer sno = rs.getInt(Constants.dbSnoCol);
					String r_object_id = rs.getString(Constants.dbDocId);
					IDfQuery query = new DfQuery();
					String documentUpdateDql = String.format(bu.updateDql, DIRBulkUpdate.prop.getProperty("type"), dirNumber, r_object_id);
					DIRBulkUpdate.logger.debug("{}:{} [DCTM] addDirNumberWhichFailed DQL: {}", bu.buName, Thread.currentThread().getName(), documentUpdateDql);
					query.setDQL(documentUpdateDql);
					query.execute(bu.session, IDfQuery.DF_QUERY);
					PreparedStatement stmt = bu.conn.prepareStatement(bu.processingDbCreateUpdateSql);
					stmt.setString(1, dirNumber);
					stmt.setString(2, Constants.completedStatus);
					stmt.setString(3, "DIR successfully created and Document updated.");
					stmt.setInt(4, sno);
					DIRBulkUpdate.logger.debug("{}:{} [DCTM] addDirNumberWhichFailed update SQL: {}", bu.buName, Thread.currentThread().getName(), stmt);
					stmt.execute();
					DIRBulkUpdate.logger.info("{}:{} [DCTM] DIR Number added to document sno={}", bu.buName, Thread.currentThread().getName(), sno);
				}
			}
		} catch (Exception e) {
			DIRBulkUpdate.logger.error("{}:{} [DCTM] Error in addDirNumberWhichFailed", bu.buName, Thread.currentThread().getName(), e);
		}
	}

	public static void finaliseCutoverInput(String documents, BUProperties bu) {
		IDfQuery query = new DfQuery();
		String documentFetchDql = String.format(bu.cutoverDql, DIRBulkUpdate.prop.getProperty("type"), documents);
		DIRBulkUpdate.logger.debug("{}:{} [DCTM] finaliseCutoverInput DQL: {}", bu.buName, Thread.currentThread().getName(), documentFetchDql);
		query.setDQL(documentFetchDql);
		IDfCollection collection = null;

		try {
			collection = query.execute(bu.session, IDfQuery.DF_READ_QUERY);
			if (collection == null) {
				DatabaseFunctions.updateDbError(bu, "NA", "No documents in D2 to process. ", Constants.completedStatus,
						null, "NA", null);
				DIRBulkUpdate.logger.warn("{}:{} [DCTM] No documents in D2 to process (cutover)", bu.buName, Thread.currentThread().getName());
			} else {
				while (collection.next()) {
					Map<String, String> input = new HashMap<>();
					Integer valueCount = 0;
					String[] ids;
					valueCount = collection.getValueCount(DIRBulkUpdate.prop.getProperty("FLOCField"));
					DIRBulkUpdate.logger.debug("{}:{} [DCTM] cutover floc valueCount={}", bu.buName, Thread.currentThread().getName(), valueCount);
					if (valueCount != 0) {
						ids = new String[valueCount];
						for (int i = 0; i < valueCount; i++) {
							ids[i] = collection.getRepeatingString(DIRBulkUpdate.prop.getProperty("FLOCField"), i);
						}
						input.put(bu.attrIndex.get(DIRBulkUpdate.prop.getProperty("FLOCField")), StringUtils.join(ids, ","));
					}

					valueCount = collection.getValueCount(DIRBulkUpdate.prop.getProperty("TLSTField"));
					DIRBulkUpdate.logger.debug("{}:{} [DCTM] cutover TLST valueCount={}", bu.buName, Thread.currentThread().getName(), valueCount);
					if (valueCount != 0) {
						ids = new String[valueCount];
						for (int i = 0; i < valueCount; i++) {
							ids[i] = collection.getRepeatingString(DIRBulkUpdate.prop.getProperty("TLSTField"), i);
						}
						input.put(bu.attrIndex.get(DIRBulkUpdate.prop.getProperty("TLSTField")), StringUtils.join(ids, ","));
					}
					valueCount = collection.getValueCount(DIRBulkUpdate.prop.getProperty("IPLNField"));
					if (valueCount != 0) {
						ids = new String[valueCount];
						for (int i = 0; i < valueCount; i++) {
							ids[i] = collection.getRepeatingString(DIRBulkUpdate.prop.getProperty("IPLNField"), i);
						}
						input.put(bu.attrIndex.get(DIRBulkUpdate.prop.getProperty("IPLNField")), StringUtils.join(ids, ","));
					}
					valueCount = collection.getValueCount(DIRBulkUpdate.prop.getProperty("WRDRField"));
					DIRBulkUpdate.logger.debug("{}:{} [DCTM] cutover WRDR valueCount={}", bu.buName, Thread.currentThread().getName(), valueCount);
					if (valueCount != 0) {
						ids = new String[valueCount];
						for (int i = 0; i < valueCount; i++) {
							ids[i] = collection.getRepeatingString(DIRBulkUpdate.prop.getProperty("WRDRField"), i);
						}
						input.put(bu.attrIndex.get(DIRBulkUpdate.prop.getProperty("WRDRField")), StringUtils.join(ids, ","));
					}
					valueCount = collection.getValueCount(DIRBulkUpdate.prop.getProperty("EQUIField"));
					DIRBulkUpdate.logger.debug("{}:{} [DCTM] cutover EQUI valueCount={}", bu.buName, Thread.currentThread().getName(), valueCount);
					if (valueCount != 0) {
						ids = new String[valueCount];
						for (int i = 0; i < valueCount; i++) {
							ids[i] = collection.getRepeatingString(DIRBulkUpdate.prop.getProperty("EQUIField"), i);
						}
						input.put(bu.attrIndex.get(DIRBulkUpdate.prop.getProperty("EQUIField")), StringUtils.join(ids, ","));
					}
					bu.buMap.put(collection.getString(Constants.d2ChronicleId), input);
					bu.buMapWithPlants.put(collection.getString(Constants.d2ChronicleId), "");

					Map<String, String> dirMap = new HashMap<>();
					String objectId = collection.getString(Constants.dbDocId);
					String chronicleId = collection.getString(Constants.d2ChronicleId);
					String dirNumber = collection.getString(Constants.d2DirField);
					if (!(dirNumber.equals(null) || dirNumber.equals(""))) {
						bu.docsWithDir.put(chronicleId, dirNumber);
						dirMap.put(objectId, dirNumber);
					} else {
						dirMap.put(objectId, "");
					}
					bu.docsWithNewVersion.put(chronicleId, dirMap);
					bu.titles.put(chronicleId, collection.getString(Constants.d2TitleField));
				}

				DIRBulkUpdate.logger.debug("{}:{} [DCTM] cutover buMap: {}", bu.buName, Thread.currentThread().getName(), bu.buMap);
				DIRBulkUpdate.logger.debug("{}:{} [DCTM] cutover docsWithNewVersion: {}", bu.buName, Thread.currentThread().getName(), bu.docsWithNewVersion);
				DIRBulkUpdate.logger.debug("{}:{} [DCTM] cutover titles: {}", bu.buName, Thread.currentThread().getName(), bu.titles);
				DIRBulkUpdate.logger.info("{}:{} [PLANTS] Fetching planning plants from workspace during cutover", bu.buName, Thread.currentThread().getName());
			}
		} catch (Exception e) {
			DIRBulkUpdate.logger.error("{}:{} [DCTM] Error in finaliseCutoverInput", bu.buName, Thread.currentThread().getName(), e);
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

		DIRBulkUpdate.logger.debug("{}:{} [FLOC] getAlternateLabelsFromD2 DQL: {}", bu.buName, Thread.currentThread().getName(), flocDql);
		IDfQuery flocQuery = new DfQuery();
		flocQuery.setDQL(flocDql);
		IDfCollection flocCollection;
		try {
			flocCollection = flocQuery.execute(bu.session, IDfQuery.DF_READ_QUERY);
			if (flocCollection == null) {
				DIRBulkUpdate.logger.warn("{}:{} [FLOC] No new FLOCs in D2", bu.buName, Thread.currentThread().getName());
			} else {
				PreparedStatement stmtFloc = bu.conn.prepareStatement(bu.flocDbUpdateSql);
				bu.conn.setAutoCommit(false);
				while (flocCollection.next()) {
					if (!(flocCollection.getString(Constants.d2AlternateLabel) == null
							|| flocCollection.getString(Constants.d2AlternateLabel).equals(""))) {
						DIRBulkUpdate.logger.debug("{}:{} [FLOC] AlternateLabel={}", bu.buName, Thread.currentThread().getName(), flocCollection.getString(Constants.d2AlternateLabel));
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
			DIRBulkUpdate.logger.error("{}:{} [FLOC] Error fetching FLOCs from D2", bu.buName, Thread.currentThread().getName(), e);
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
			DIRBulkUpdate.logger.debug("{}:{} [DCTM] executeRelatedTags config: {}", bu.buName, Thread.currentThread().getName(), config);
			doc2tagsvc.processTag2DocInfo(config, bu.session, r_object_id);
			doc2tagsvc.processDocLink2Tags(bu.session, r_object_id);
			DIRBulkUpdate.logger.info("{}:{} [DCTM] Related Tags method successful for objectId={}", bu.buName, Thread.currentThread().getName(), r_object_id);
		} catch (Exception e) {
			DIRBulkUpdate.logger.error("{}:{} [DCTM] Related Tags method failed for objectId={}", bu.buName, Thread.currentThread().getName(), r_object_id, e);
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
			DIRBulkUpdate.logger.error("[DCTM] Error fetching xecmtag2docconfig dictionary", e);
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
}
