package DIRBulkUpdate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Properties;

import com.documentum.fc.client.DfQuery;
import com.documentum.fc.client.IDfCollection;
import com.documentum.fc.client.IDfQuery;
import com.documentum.fc.client.IDfSession;
import com.documentum.fc.client.IDfSysObject;
import com.documentum.fc.common.DfException;
import com.documentum.fc.common.DfId;

public class XECMDoc2TagServiceMethod {

	IDfCollection collectionObj = null;
	int cvlCreationCnt = 0;
	int cvlTotalCount = 0;

	static String SRC_REPOSITORY_NAME = "SRC_REPOSITORY_NAME";
	static String SRC_REPOSITORY_USER_NAME = "SRC_REPOSITORY_USER_NAME";
	static String SRC_REPOSITORY_USER_PASSWORD = "SRC_REPOSITORY_USER_PASSWORD";
	static String BU_NAME_AND_TAGATTR_TYPE_NAME = "BU_NAME_AND_TAGATTR_TYPE_NAME";
	static String DOC_TAG_SELECT_QUERY = "DOC_TAG_SELECT_QUERY";
	static String WO_PARENT_INFO_SELECT_QUERY = "WO_PARENT_INFO_SELECT_QUERY";
	static String INSPLAN_INFO_SELECT_QUERY = "INSPLAN_INFO_SELECT_QUERY";
	static String TASKLIST_INFO_SELECT_QUERY = "TASKLIST_INFO_SELECT_QUERY";
	static String EQUIP_PARENT_INFO_SELECT_QUERY = "EQUIP_PARENT_INFO_SELECT_QUERY";

	HashMap<String, String> buTagTypeMap = new HashMap<String, String>();
	String tagAttrTypeName = null;
	String strDocTagQuery = null;
	String strWOParentObjQry = null;

	public XECMDoc2TagServiceMethod() {
		try {
			System.out.println("Loading properties");
			// PropertyLoader prop = new PropertyLoader();
			// config=prop.loadProperties();
		} catch (Exception exception) {

		}
	}
	/*
	 * private void establishRepositoryConnection(Properties config) { try {
	 * System.out.println("SRC_REPOSITORY_NAME="+config.getProperty(
	 * SRC_REPOSITORY_NAME));
	 * System.out.println("SRC_REPOSITORY_USER_NAME="+config.getProperty(
	 * SRC_REPOSITORY_USER_NAME));
	 * System.out.println("BU_NAME_AND_TAGATTR_TYPE_NAME="+config.getProperty(
	 * BU_NAME_AND_TAGATTR_TYPE_NAME));
	 * 
	 * System.out.println("\nSource Repository Connection: IN PROCESS...");
	 * 
	 * sessionUtil = new SessionManager( config.getProperty(SRC_REPOSITORY_NAME),
	 * config.getProperty(SRC_REPOSITORY_USER_NAME),
	 * config.getProperty(SRC_REPOSITORY_USER_PASSWORD));
	 * 
	 * sessionObj = sessionUtil.getDCTMSession(); // create session object
	 * 
	 * System.out.println("Source Repository Connection: COMPLETED");
	 * System.out.println(
	 * "***************************************************************");
	 * 
	 * }catch(Exception exception) { exception.printStackTrace(); } }
	 */

	public void processTag2DocInfo(Properties config, IDfSession sessionObj, String objectId) {

		try {
			String repositoryName = sessionObj.getDocbaseName(); // R4A
			tagAttrTypeName = getBUTagTypeName(repositoryName, config);
			HashMap<String, ArrayList<String>> docTagsInfo = getTag2DocInfo(tagAttrTypeName, config, sessionObj, objectId);
			System.out.println("Before WO Update docTagsInfo=" + docTagsInfo);
			updateSAPWorkOrderParentTagsObjects(docTagsInfo, config, sessionObj);
			System.out.println("After WO update docTagsInfo=" + docTagsInfo);
			updateSAPEquipmentParentTagObject(docTagsInfo, config, sessionObj);
		} catch (DfException e) {
			e.printStackTrace();
		}
	}

	private void updateSAPEquipmentParentTagObject(HashMap<String, ArrayList<String>> docTagsInfo, Properties config,
			IDfSession sessionObj) {
		try {
			// HashMap<String,ArrayList<String>> docTagsInfoORG = docTagsInfo;

			ArrayList<String> equipList = docTagsInfo.get("sap_xecm_equip_tags");

			System.out.println("equipList=" + equipList);
			if (equipList != null && !equipList.isEmpty())
				for (String strEquip : equipList) {
					HashMap<String, String> equipParentTags = getEquipParentObjects(strEquip, config, sessionObj);
					System.out.println("equipParentTags-->" + equipParentTags);
					// append wo parent floc with existing ones
					if (equipParentTags.containsKey("parent_floc_id") && docTagsInfo.get("sap_xecm_floc_tags") != null
							&& !docTagsInfo.get("sap_xecm_floc_tags").contains(equipParentTags.get("parent_floc_id"))) {
						docTagsInfo.get("sap_xecm_floc_tags").add(equipParentTags.get("parent_floc_id"));
						ArrayList<String> flocList = docTagsInfo.get("sap_xecm_floc_tags");
						docTagsInfo.put("sap_xecm_floc_tags", flocList);
					} else {
						docTagsInfo.put("sap_xecm_floc_tags",
								new ArrayList<String>(Arrays.asList(equipParentTags.get("parent_floc_id"))));
					}
				}
			updateEquipTagsValue2Doc(docTagsInfo, sessionObj);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private HashMap<String, String> getEquipParentObjects(String strEquip, Properties config, IDfSession sessionObj)
			throws DfException {
		HashMap<String, String> equipParentTagInfo = new HashMap<String, String>();
		IDfQuery queryObj = new DfQuery();
		IDfCollection collectionObj = null;

		String strObjectID = null;
		String strParentFLOCTag = null;

		try {
			String strEquipParentObjQry = config.getProperty(EQUIP_PARENT_INFO_SELECT_QUERY).replace("$equip_num",
					strEquip);
			System.out.println("strEquipParentObjQry-->" + strEquipParentObjQry);
			queryObj.setDQL(strEquipParentObjQry);
			collectionObj = queryObj.execute(sessionObj, IDfQuery.DF_EXECREAD_QUERY);
			while (collectionObj.next()) {
				strObjectID = collectionObj.getString("r_object_id");
				strParentFLOCTag = collectionObj.getString("parent_floc_id");
			}
			if (strObjectID != null && !strObjectID.isEmpty())
				equipParentTagInfo.put("r_object_id", strObjectID);
			if (strParentFLOCTag != null && !strParentFLOCTag.isEmpty())
				equipParentTagInfo.put("parent_floc_id", strParentFLOCTag);

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (collectionObj != null) {
				collectionObj.close();
			}
		}
		return equipParentTagInfo;
	}

	private void updateEquipTagsValue2Doc(HashMap<String, ArrayList<String>> docTagsInfo, IDfSession sessionObj) {

		try {
			IDfSysObject tagDocumentObj = (IDfSysObject) sessionObj
					.getObject(new DfId(docTagsInfo.get("r_object_id").get(0).toString()));

			// udpate floc tag values, ignore if values were exist
			int tagIndex = 0;
			ArrayList<String> flocList = docTagsInfo.get("sap_xecm_floc_tags");
			System.out.println("flocList-->" + flocList);
			if (flocList != null && !flocList.isEmpty())
				for (String flocValue : flocList) {
					tagDocumentObj.setRepeatingString("sap_xecm_floc_tags", tagIndex, flocValue);
					tagIndex++;
				}
			tagDocumentObj.save();

		} catch (Exception exception) {
			exception.printStackTrace();
		}

	}

	public void updateSAPWorkOrderParentTagsObjects(HashMap<String, ArrayList<String>> docTagsInfo, Properties config,
			IDfSession sessionObj) {
		try {
			// HashMap<String,ArrayList<String>> docTagsInfoORG = docTagsInfo;
			// get the WO list
			// select
			// r_object_id,order_number,parent_floc_id,parent_equipment_id,parent_tsklst_id,parent_inspection_id
			// from xecm_sap_work_order where object_name='$wo_num' and
			// order_number='$wo_num'
			ArrayList<String> woList = docTagsInfo.get("sap_xecm_wo_tags");

			System.out.println("woList=" + woList);
			if (woList != null && !woList.isEmpty())
				for (String strWO : woList) {
					HashMap<String, String> woParentTags = getWOParentObjects(strWO, config, sessionObj);

					System.out.println("woParentTags-->" + woParentTags);
					// append wo parent floc with existing ones
					if (woParentTags.containsKey("parent_floc_id") && docTagsInfo.get("sap_xecm_floc_tags") != null
							&& !docTagsInfo.get("sap_xecm_floc_tags").contains(woParentTags.get("parent_floc_id"))) {
						docTagsInfo.get("sap_xecm_floc_tags").add(woParentTags.get("parent_floc_id"));
						ArrayList<String> flocList = docTagsInfo.get("sap_xecm_floc_tags");
						docTagsInfo.put("sap_xecm_floc_tags", flocList);
					} else {
						docTagsInfo.put("sap_xecm_floc_tags",
								new ArrayList<String>(Arrays.asList(woParentTags.get("parent_floc_id"))));
					}

					// append wo parent equipment with existing ones
					if (woParentTags.containsKey("parent_equipment_id")
							&& docTagsInfo.get("sap_xecm_equip_tags") != null && !docTagsInfo.get("sap_xecm_equip_tags")
									.contains(woParentTags.get("parent_equipment_id"))) {
						docTagsInfo.get("sap_xecm_equip_tags").add(woParentTags.get("parent_equipment_id"));
						ArrayList<String> equipList = docTagsInfo.get("sap_xecm_equip_tags");
						docTagsInfo.put("sap_xecm_equip_tags", equipList);
					} else {
						docTagsInfo.put("sap_xecm_equip_tags",
								new ArrayList<String>(Arrays.asList(woParentTags.get("parent_equipment_id"))));
					}

					// append wo parent TaskList with existing ones
					if (woParentTags.containsKey("parent_tsklst_id") && docTagsInfo.get("sap_xecm_tsklst_tags") != null
							&& !docTagsInfo.get("sap_xecm_tsklst_tags")
									.contains(woParentTags.get("parent_tsklst_id"))) {
						docTagsInfo.get("sap_xecm_tsklst_tags").add(woParentTags.get("parent_tsklst_id"));
						ArrayList<String> taskList = docTagsInfo.get("sap_xecm_tsklst_tags");
						docTagsInfo.put("sap_xecm_tsklst_tags", taskList);
					} else {
						docTagsInfo.put("sap_xecm_tsklst_tags",
								new ArrayList<String>(Arrays.asList(woParentTags.get("parent_tsklst_id"))));
					}

					// append wo parent Inspection list with existing ones
					if (woParentTags.containsKey("parent_inspection_id")
							&& docTagsInfo.get("sap_xecm_insp_tags") != null && !docTagsInfo.get("sap_xecm_insp_tags")
									.contains(woParentTags.get("parent_inspection_id"))) {
						docTagsInfo.get("sap_xecm_insp_tags").add(woParentTags.get("parent_inspection_id"));
						ArrayList<String> inspList = docTagsInfo.get("sap_xecm_insp_tags");
						docTagsInfo.put("sap_xecm_insp_tags", inspList);
					} else {
						docTagsInfo.put("sap_xecm_insp_tags",
								new ArrayList<String>(Arrays.asList(woParentTags.get("parent_inspection_id"))));
					}

				}
			updateWOTagsValue2Doc(docTagsInfo, config, sessionObj);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void updateWOTagsValue2Doc(HashMap<String, ArrayList<String>> docTagsInfo, Properties config,
			IDfSession sessionObj) {
		try {
			IDfSysObject tagDocumentObj = (IDfSysObject) sessionObj
					.getObject(new DfId(docTagsInfo.get("r_object_id").get(0).toString()));

			// udpate floc tag values, ignore if values were exist
			int tagIndex = 0;
			ArrayList<String> flocList = docTagsInfo.get("sap_xecm_floc_tags");
			System.out.println("flocList-->" + flocList);
			if (flocList != null && !flocList.isEmpty()) {
				for (String flocValue : flocList) {
					tagDocumentObj.setRepeatingString("sap_xecm_floc_tags", tagIndex, flocValue);
					tagIndex++;
				}
			}

			tagIndex = 0;
			ArrayList<String> equipList = docTagsInfo.get("sap_xecm_equip_tags");
			if (equipList != null && !equipList.isEmpty()) {
				for (String equipValue : equipList) {
					tagDocumentObj.setRepeatingString("sap_xecm_equip_tags", tagIndex, equipValue);
					tagIndex++;
				}
			}

			tagIndex = 0;
			ArrayList<String> taskList = docTagsInfo.get("sap_xecm_tsklst_tags");
			System.out.println("S-tasklist value=" + taskList);
			if (taskList != null && !taskList.isEmpty())
				for (String taskValue : taskList) {
					String taskListTagName = getTaskListWSName(taskValue, config, sessionObj);
					// if(!docTagsInfo.get("sap_xecm_tsklst_tags").contains(taskListTagName)) {
					tagDocumentObj.setRepeatingString("sap_xecm_tsklst_tags", tagIndex, taskListTagName);
					tagIndex++;
					// }
				}

			tagIndex = 0;
			ArrayList<String> inspList = docTagsInfo.get("sap_xecm_insp_tags");
			if (inspList != null && !inspList.isEmpty())
				for (String inspValue : inspList) {
					String inspTagName = getInspWSName(inspValue, config, sessionObj);
					System.out.println("inspTagName-->" + inspTagName);
					// if(!docTagsInfo.get("sap_xecm_insp_tags").contains(inspTagName)) {
					tagDocumentObj.setRepeatingString("sap_xecm_insp_tags", tagIndex, inspTagName);
					tagIndex++;
					// }
				}
			tagDocumentObj.save();

		} catch (Exception exception) {
			exception.printStackTrace();
		}

	}

	private String getInspWSName(String inspValue, Properties config, IDfSession sessionObj) throws DfException {
		IDfQuery queryObj = new DfQuery();
		IDfCollection collectionObj = null;
		String inspectionName = null;
		try {
			System.out.println("inspValue--" + inspValue);
			if (inspValue != null) {
				String strInspValueQry = config.getProperty(INSPLAN_INFO_SELECT_QUERY).replace("$inspValue", inspValue);
				System.out.println("strInspValueQry-->" + strInspValueQry);
				queryObj.setDQL(strInspValueQry);
				collectionObj = queryObj.execute(sessionObj, IDfQuery.DF_EXEC_QUERY);
				while (collectionObj.next()) {
					inspectionName = collectionObj.getString("object_name");
				}
			}
		} catch (Exception exception) {
			exception.printStackTrace();
		} finally {
			if (collectionObj != null) {
				collectionObj.close();
			}
		}
		return inspectionName;
	}

	private String getTaskListWSName(String taskListValue, Properties config, IDfSession sessionObj)
			throws DfException {
		IDfQuery queryObj = new DfQuery();
		IDfCollection collectionObj = null;
		String taskListName = null;
		try {

			String strtaskListValueQry = config.getProperty(TASKLIST_INFO_SELECT_QUERY).replace("$taskListValue",
					taskListValue);
			System.out.println("strtaskListValueQry-->" + strtaskListValueQry);
			queryObj.setDQL(strtaskListValueQry);
			collectionObj = queryObj.execute(sessionObj, IDfQuery.DF_EXEC_QUERY);
			while (collectionObj.next()) {
				taskListName = collectionObj.getString("object_name");
			}
			System.out.println("Sam-TaskList=" + taskListName);
		} catch (Exception exception) {
			exception.printStackTrace();
		} finally {
			if (collectionObj != null) {
				collectionObj.close();
			}
		}
		return taskListName;
	}

	public HashMap<String, String> getWOParentObjects(String strWO, Properties config, IDfSession sessionObj)
			throws DfException {
		HashMap<String, String> woParentTagInfo = new HashMap<String, String>();
		IDfQuery queryObj = new DfQuery();
		IDfCollection collectionObj = null;

		String strObjectID = null;
		String strParentEquipTag = null;
		String strParentFLOCTag = null;
		String strParentTaskListTag = null;
		String strParentInspListTag = null;

		try {
			strWOParentObjQry = config.getProperty(WO_PARENT_INFO_SELECT_QUERY).replace("$wo_num", strWO);
			System.out.println("strWOParentObjQry-->" + strWOParentObjQry);
			queryObj.setDQL(strWOParentObjQry);
			collectionObj = queryObj.execute(sessionObj, IDfQuery.DF_EXECREAD_QUERY);
			while (collectionObj.next()) {
				strObjectID = collectionObj.getString("r_object_id");
				strParentEquipTag = collectionObj.getString("parent_equipment_id");
				strParentFLOCTag = collectionObj.getString("parent_floc_id");
				strParentTaskListTag = collectionObj.getString("parent_tsklst_id");
				strParentInspListTag = collectionObj.getString("parent_inspection_id");

			}
			if (strObjectID != null && !strObjectID.isEmpty())
				woParentTagInfo.put("r_object_id", strObjectID);
			if (strParentEquipTag != null && !strParentEquipTag.isEmpty())
				woParentTagInfo.put("parent_equipment_id", strParentEquipTag);
			if (strParentFLOCTag != null && !strParentFLOCTag.isEmpty())
				woParentTagInfo.put("parent_floc_id", strParentFLOCTag);
			if (strParentTaskListTag != null && !strParentTaskListTag.isEmpty())
				woParentTagInfo.put("parent_tsklst_id", strParentTaskListTag);
			if (strParentInspListTag != null && !strParentInspListTag.isEmpty())
				woParentTagInfo.put("parent_inspection_id", strParentInspListTag);

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (collectionObj != null) {
				collectionObj.close();
			}
		}
		return woParentTagInfo;
	}

	public HashMap<String, ArrayList<String>> getTag2DocInfo(String tagAttrTypeName, Properties config,
			IDfSession sessionObj, String objectId) throws DfException {

		IDfQuery queryObj = new DfQuery();
		IDfCollection collectionObj = null;

		String strObjectID = null;
		String str_sap_xecm_equip_tags = null;
		String str_sap_xecm_floc_tags = null;
		String str_sap_xecm_insp_tags = null;
		String str_sap_xecm_tsklst_tags = null;
		String str_sap_xecm_wo_tags = null;

		HashMap<String, ArrayList<String>> docTagsInfo = new HashMap<String, ArrayList<String>>();

		try {

			System.out.println("DOC_TAG_SELECT_QUERY" + config.getProperty(DOC_TAG_SELECT_QUERY));
			strDocTagQuery = config.getProperty(DOC_TAG_SELECT_QUERY).replace("$document_type", tagAttrTypeName);
			strDocTagQuery = strDocTagQuery.replace("$object_id", objectId);
			System.out.println("strDocTagQuery" + strDocTagQuery);
			queryObj.setDQL(strDocTagQuery);
			collectionObj = queryObj.execute(sessionObj, IDfQuery.DF_EXECREAD_QUERY);
			while (collectionObj.next()) {
				strObjectID = collectionObj.getString("r_object_id");
				str_sap_xecm_equip_tags = collectionObj.getAllRepeatingStrings("sap_xecm_equip_tags", ",");
				str_sap_xecm_floc_tags = collectionObj.getAllRepeatingStrings("sap_xecm_floc_tags", ",");
				str_sap_xecm_insp_tags = collectionObj.getAllRepeatingStrings("sap_xecm_insp_tags", ",");
				str_sap_xecm_tsklst_tags = collectionObj.getAllRepeatingStrings("sap_xecm_tsklst_tags", ",");
				str_sap_xecm_wo_tags = collectionObj.getAllRepeatingStrings("sap_xecm_wo_tags", ",");
			}

			/*
			 * System.out.println("strObjectID-->"+strObjectID);
			 * System.out.println("str_sap_xecm_equip_tags-->"+str_sap_xecm_equip_tags);
			 * System.out.println("str_sap_xecm_floc_tags-->"+str_sap_xecm_floc_tags);
			 * System.out.println("str_sap_xecm_insp_tags-->"+str_sap_xecm_insp_tags);
			 * System.out.println("str_sap_xecm_tsklst_tags-->"+str_sap_xecm_tsklst_tags);
			 * System.out.println("str_sap_xecm_wo_tags-->"+str_sap_xecm_wo_tags);
			 */

			if (strObjectID != null && !strObjectID.isEmpty()) {
				docTagsInfo.put("r_object_id", new ArrayList<String>(Arrays.asList(strObjectID.split(","))));
			}
			if (str_sap_xecm_floc_tags != null && !str_sap_xecm_floc_tags.isEmpty()) {
				docTagsInfo.put("sap_xecm_floc_tags",
						new ArrayList<String>(Arrays.asList(str_sap_xecm_floc_tags.split(","))));
			}
			if (str_sap_xecm_equip_tags != null && !str_sap_xecm_equip_tags.isEmpty()) {
				docTagsInfo.put("sap_xecm_equip_tags",
						new ArrayList<String>(Arrays.asList(str_sap_xecm_equip_tags.split(","))));
			}
			if (str_sap_xecm_insp_tags != null && !str_sap_xecm_insp_tags.isEmpty()) {
				docTagsInfo.put("sap_xecm_insp_tags",
						new ArrayList<String>(Arrays.asList(str_sap_xecm_insp_tags.split(","))));
			}
			if (str_sap_xecm_tsklst_tags != null && !str_sap_xecm_tsklst_tags.isEmpty()) {
				docTagsInfo.put("sap_xecm_tsklst_tags",
						new ArrayList<String>(Arrays.asList(str_sap_xecm_tsklst_tags.split(","))));
			}
			if (str_sap_xecm_wo_tags != null && !str_sap_xecm_wo_tags.isEmpty()) {
				docTagsInfo.put("sap_xecm_wo_tags",
						new ArrayList<String>(Arrays.asList(str_sap_xecm_wo_tags.split(","))));
			}

			System.out.println("docTagsInfo=" + docTagsInfo);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			if (collectionObj != null) {
				collectionObj.close();
			}
		}
		return docTagsInfo;
	}

	public String getBUTagTypeName(String repositoryName, Properties config) {

		try {
			getBUTagTypeMapping(config);
			for (Entry<String, String> entrySet : buTagTypeMap.entrySet()) {
				if (repositoryName.contains(entrySet.getKey())) {
					tagAttrTypeName = entrySet.getValue();
				}
			}
		} catch (Exception exception) {
			exception.printStackTrace();
		}
		return tagAttrTypeName;
	}

	public void getBUTagTypeMapping(Properties config) {

		try {
			String strBUTagTypeMap = config.getProperty(BU_NAME_AND_TAGATTR_TYPE_NAME);
			System.out.println("strBUTagTypeMap: " + strBUTagTypeMap);
			String[] temp = strBUTagTypeMap.split("\\;");
			for (String token : temp) {
				String[] temp1 = token.split("\\|");
				buTagTypeMap.put(temp1[0], temp1[1]);
			}
		} catch (Exception exception) {
			exception.printStackTrace();
		}
	}

	void processDocLink2Tags(IDfSession sessionObj, String r_object_id) {
		try {
			System.out.println("Running Core Method");
			DfId id = new DfId(r_object_id);
			applyD2Configurations(id.toString(), sessionObj);
			System.out.println("Run Core Method");
		} catch (DfException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/*
	 * private void processDocLink2Tags(Properties config) { IDfQuery queryObj = new
	 * DfQuery(); IDfCollection collectionObj = null; try { String
	 * repositoryName=sessionObj.getDocbaseName(); tagAttrTypeName =
	 * getBUTagTypeName(repositoryName, config); HashMap<String,ArrayList<String>>
	 * docTagsInfo = getTag2DocInfo(tagAttrTypeName, config);
	 * 
	 * ArrayList<String> flocList=docTagsInfo.get("sap_xecm_floc_tags");
	 * ArrayList<String> equipList=docTagsInfo.get("sap_xecm_equip_tags");
	 * ArrayList<String> taskList=docTagsInfo.get("sap_xecm_tsklst_tags");
	 * ArrayList<String> inspList=docTagsInfo.get("sap_xecm_insp_tags");
	 * ArrayList<String> woList=docTagsInfo.get("sap_xecm_wo_tags");
	 * 
	 * System.out.println("DOC_TAG_SELECT_QUERY"+config.getProperty(
	 * DOC_TAG_SELECT_QUERY)); strDocTagQuery =
	 * config.getProperty(DOC_TAG_SELECT_QUERY).replace("$document_type",
	 * tagAttrTypeName); System.out.println("strDocTagQuery"+strDocTagQuery);
	 * 
	 * IDfSysObject tagDocumentObj = (IDfSysObject)sessionObj.getObject(new
	 * DfId(docTagsInfo.get("r_object_id").get(0).toString()));
	 * 
	 * tagDocumentObj.link("0b015f8b8004bb1c");
	 * tagDocumentObj.link("0b015f8b8004a3ec");
	 * tagDocumentObj.link("0b015f8b8004a364"); tagDocumentObj.save();
	 * 
	 * 
	 * System.out.println("flocList="+flocList); if(flocList!= null &&
	 * !flocList.isEmpty()) for (String strFloc : flocList) {
	 * queryObj.setDQL(strDocTagQuery); collectionObj = queryObj.execute(sessionObj,
	 * IDfQuery.DF_EXECREAD_QUERY); while (collectionObj.next()) { }}}
	 * 
	 * catch (DfException e) { e.printStackTrace(); }
	 */

	protected void applyD2Configurations(String qualifier, IDfSession sessionObj) throws DfException {
		IDfQuery query = new DfQuery();
		String relatedTagsDql = String.format(
				"execute do_method with method='D2CoreMethod', arguments='-docbase_name %s -user dmadmin -id %s -naming false -autolink true -security true -apply_for_vd false -create false -transaction false';",
				sessionObj.getDocbaseName(), qualifier);
		// System.out.println(relatedTagsDql);
		query.setDQL(relatedTagsDql);
		try {
			query.execute(sessionObj, IDfQuery.DF_QUERY);
			System.out.println("Related Tags Method successful for:" + qualifier);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Related Tags Method failed for:" + qualifier);
		}

	}

	public static void main(String[] args) {

		System.out.println("Loading propertiess");
		/*
		 * Properties config = null; try { //config=prop.loadProperties();
		 * System.out.println(config); } catch (Exception e) { // TODO Auto-generated
		 * catch block e.printStackTrace(); }
		 * System.out.println("XECMDoc2TagServiceMethod main()");
		 * XECMDoc2TagServiceMethod doc2tagsvc = new XECMDoc2TagServiceMethod();
		 * doc2tagsvc.establishRepositoryConnection(config);
		 */
		// doc2tagsvc.processTag2DocInfo(config);
		// doc2tagsvc.processDocLink2Tags();
	}
}
