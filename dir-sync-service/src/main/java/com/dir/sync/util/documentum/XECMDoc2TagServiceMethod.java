package com.dir.sync.util.documentum;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.documentum.fc.client.DfQuery;
import com.documentum.fc.client.IDfCollection;
import com.documentum.fc.client.IDfQuery;
import com.documentum.fc.client.IDfSession;
import com.documentum.fc.client.IDfSysObject;
import com.documentum.fc.common.DfException;
import com.documentum.fc.common.DfId;

/**
 * Ported from DIRBulkUpdate.XECMDoc2TagServiceMethod.
 * All logic preserved exactly; static DIRBulkUpdate.logger replaced with
 * per-class LogManager logger.
 */
public class XECMDoc2TagServiceMethod {

    private static final Logger logger = LogManager.getLogger(XECMDoc2TagServiceMethod.class);

    IDfCollection collectionObj = null;
    int cvlCreationCnt = 0;
    int cvlTotalCount = 0;

    static String SRC_REPOSITORY_NAME          = "SRC_REPOSITORY_NAME";
    static String SRC_REPOSITORY_USER_NAME      = "SRC_REPOSITORY_USER_NAME";
    static String SRC_REPOSITORY_USER_PASSWORD  = "SRC_REPOSITORY_USER_PASSWORD";
    static String BU_NAME_AND_TAGATTR_TYPE_NAME = "BU_NAME_AND_TAGATTR_TYPE_NAME";
    static String DOC_TAG_SELECT_QUERY          = "DOC_TAG_SELECT_QUERY";
    static String WO_PARENT_INFO_SELECT_QUERY   = "WO_PARENT_INFO_SELECT_QUERY";
    static String INSPLAN_INFO_SELECT_QUERY     = "INSPLAN_INFO_SELECT_QUERY";
    static String TASKLIST_INFO_SELECT_QUERY    = "TASKLIST_INFO_SELECT_QUERY";
    static String EQUIP_PARENT_INFO_SELECT_QUERY = "EQUIP_PARENT_INFO_SELECT_QUERY";

    HashMap<String, String> buTagTypeMap = new HashMap<>();
    String tagAttrTypeName = null;
    String strDocTagQuery  = null;
    String strWOParentObjQry = null;

    public XECMDoc2TagServiceMethod() {
        logger.info("[DCTM] XECMDoc2TagServiceMethod loading properties");
    }

    public void processTag2DocInfo(Properties config, IDfSession sessionObj, String objectId) {
        try {
            String repositoryName = sessionObj.getDocbaseName();
            tagAttrTypeName = getBUTagTypeName(repositoryName, config);
            HashMap<String, ArrayList<String>> docTagsInfo = getTag2DocInfo(tagAttrTypeName, config, sessionObj, objectId);
            logger.debug("[DCTM] Before WO Update docTagsInfo={}", docTagsInfo);
            updateSAPWorkOrderParentTagsObjects(docTagsInfo, config, sessionObj);
            logger.debug("[DCTM] After WO update docTagsInfo={}", docTagsInfo);
            updateSAPEquipmentParentTagObject(docTagsInfo, config, sessionObj);
        } catch (DfException e) {
            logger.error("[DCTM] Error in processTag2DocInfo for objectId={}", objectId, e);
        }
    }

    private void updateSAPEquipmentParentTagObject(HashMap<String, ArrayList<String>> docTagsInfo,
            Properties config, IDfSession sessionObj) {
        try {
            ArrayList<String> equipList = docTagsInfo.get("sap_xecm_equip_tags");
            logger.debug("[DCTM] equipList={}", equipList);
            if (equipList != null && !equipList.isEmpty())
                for (String strEquip : equipList) {
                    HashMap<String, String> equipParentTags = getEquipParentObjects(strEquip, config, sessionObj);
                    logger.debug("[DCTM] equipParentTags={}", equipParentTags);
                    if (equipParentTags.containsKey("parent_floc_id") && docTagsInfo.get("sap_xecm_floc_tags") != null
                            && !docTagsInfo.get("sap_xecm_floc_tags").contains(equipParentTags.get("parent_floc_id"))) {
                        docTagsInfo.get("sap_xecm_floc_tags").add(equipParentTags.get("parent_floc_id"));
                        ArrayList<String> flocList = docTagsInfo.get("sap_xecm_floc_tags");
                        docTagsInfo.put("sap_xecm_floc_tags", flocList);
                    } else {
                        docTagsInfo.put("sap_xecm_floc_tags",
                                new ArrayList<>(Arrays.asList(equipParentTags.get("parent_floc_id"))));
                    }
                }
            updateEquipTagsValue2Doc(docTagsInfo, sessionObj);
        } catch (Exception e) {
            logger.error("[DCTM] Error in updateSAPEquipmentParentTagObject", e);
        }
    }

    private HashMap<String, String> getEquipParentObjects(String strEquip, Properties config,
            IDfSession sessionObj) throws DfException {
        HashMap<String, String> equipParentTagInfo = new HashMap<>();
        IDfQuery queryObj = new DfQuery();
        IDfCollection coll = null;
        String strObjectID      = null;
        String strParentFLOCTag = null;
        try {
            String strEquipParentObjQry = config.getProperty(EQUIP_PARENT_INFO_SELECT_QUERY)
                    .replace("$equip_num", strEquip);
            logger.debug("[DCTM] strEquipParentObjQry={}", strEquipParentObjQry);
            queryObj.setDQL(strEquipParentObjQry);
            coll = queryObj.execute(sessionObj, IDfQuery.DF_EXECREAD_QUERY);
            while (coll.next()) {
                strObjectID      = coll.getString("r_object_id");
                strParentFLOCTag = coll.getString("parent_floc_id");
            }
            if (strObjectID      != null && !strObjectID.isEmpty())      equipParentTagInfo.put("r_object_id",    strObjectID);
            if (strParentFLOCTag != null && !strParentFLOCTag.isEmpty()) equipParentTagInfo.put("parent_floc_id", strParentFLOCTag);
        } catch (Exception e) {
            logger.error("[DCTM] Error in getEquipParentObjects for strEquip={}", strEquip, e);
        } finally {
            if (coll != null) coll.close();
        }
        return equipParentTagInfo;
    }

    private void updateEquipTagsValue2Doc(HashMap<String, ArrayList<String>> docTagsInfo, IDfSession sessionObj) {
        try {
            IDfSysObject tagDocumentObj = (IDfSysObject) sessionObj
                    .getObject(new DfId(docTagsInfo.get("r_object_id").get(0).toString()));
            int tagIndex = 0;
            ArrayList<String> flocList = docTagsInfo.get("sap_xecm_floc_tags");
            logger.debug("[DCTM] updateEquipTagsValue2Doc flocList={}", flocList);
            if (flocList != null && !flocList.isEmpty())
                for (String flocValue : flocList) {
                    tagDocumentObj.setRepeatingString("sap_xecm_floc_tags", tagIndex, flocValue);
                    tagIndex++;
                }
            tagDocumentObj.save();
        } catch (Exception e) {
            logger.error("[DCTM] Error in updateEquipTagsValue2Doc", e);
        }
    }

    public void updateSAPWorkOrderParentTagsObjects(HashMap<String, ArrayList<String>> docTagsInfo,
            Properties config, IDfSession sessionObj) {
        try {
            ArrayList<String> woList = docTagsInfo.get("sap_xecm_wo_tags");
            logger.debug("[DCTM] woList={}", woList);
            if (woList != null && !woList.isEmpty())
                for (String strWO : woList) {
                    HashMap<String, String> woParentTags = getWOParentObjects(strWO, config, sessionObj);
                    logger.debug("[DCTM] woParentTags={}", woParentTags);
                    if (woParentTags.containsKey("parent_floc_id") && docTagsInfo.get("sap_xecm_floc_tags") != null
                            && !docTagsInfo.get("sap_xecm_floc_tags").contains(woParentTags.get("parent_floc_id"))) {
                        docTagsInfo.get("sap_xecm_floc_tags").add(woParentTags.get("parent_floc_id"));
                        docTagsInfo.put("sap_xecm_floc_tags", docTagsInfo.get("sap_xecm_floc_tags"));
                    } else {
                        docTagsInfo.put("sap_xecm_floc_tags",
                                new ArrayList<>(Arrays.asList(woParentTags.get("parent_floc_id"))));
                    }
                    if (woParentTags.containsKey("parent_equipment_id")
                            && docTagsInfo.get("sap_xecm_equip_tags") != null
                            && !docTagsInfo.get("sap_xecm_equip_tags").contains(woParentTags.get("parent_equipment_id"))) {
                        docTagsInfo.get("sap_xecm_equip_tags").add(woParentTags.get("parent_equipment_id"));
                        docTagsInfo.put("sap_xecm_equip_tags", docTagsInfo.get("sap_xecm_equip_tags"));
                    } else {
                        docTagsInfo.put("sap_xecm_equip_tags",
                                new ArrayList<>(Arrays.asList(woParentTags.get("parent_equipment_id"))));
                    }
                    if (woParentTags.containsKey("parent_tsklst_id")
                            && docTagsInfo.get("sap_xecm_tsklst_tags") != null
                            && !docTagsInfo.get("sap_xecm_tsklst_tags").contains(woParentTags.get("parent_tsklst_id"))) {
                        docTagsInfo.get("sap_xecm_tsklst_tags").add(woParentTags.get("parent_tsklst_id"));
                        docTagsInfo.put("sap_xecm_tsklst_tags", docTagsInfo.get("sap_xecm_tsklst_tags"));
                    } else {
                        docTagsInfo.put("sap_xecm_tsklst_tags",
                                new ArrayList<>(Arrays.asList(woParentTags.get("parent_tsklst_id"))));
                    }
                    if (woParentTags.containsKey("parent_inspection_id")
                            && docTagsInfo.get("sap_xecm_insp_tags") != null
                            && !docTagsInfo.get("sap_xecm_insp_tags").contains(woParentTags.get("parent_inspection_id"))) {
                        docTagsInfo.get("sap_xecm_insp_tags").add(woParentTags.get("parent_inspection_id"));
                        docTagsInfo.put("sap_xecm_insp_tags", docTagsInfo.get("sap_xecm_insp_tags"));
                    } else {
                        docTagsInfo.put("sap_xecm_insp_tags",
                                new ArrayList<>(Arrays.asList(woParentTags.get("parent_inspection_id"))));
                    }
                }
            updateWOTagsValue2Doc(docTagsInfo, config, sessionObj);
        } catch (Exception e) {
            logger.error("[DCTM] Error in updateSAPWorkOrderParentTagsObjects", e);
        }
    }

    private void updateWOTagsValue2Doc(HashMap<String, ArrayList<String>> docTagsInfo,
            Properties config, IDfSession sessionObj) {
        try {
            IDfSysObject tagDocumentObj = (IDfSysObject) sessionObj
                    .getObject(new DfId(docTagsInfo.get("r_object_id").get(0).toString()));
            int tagIndex = 0;
            ArrayList<String> flocList = docTagsInfo.get("sap_xecm_floc_tags");
            logger.debug("[DCTM] updateWOTagsValue2Doc flocList={}", flocList);
            if (flocList != null && !flocList.isEmpty())
                for (String flocValue : flocList) {
                    tagDocumentObj.setRepeatingString("sap_xecm_floc_tags", tagIndex, flocValue);
                    tagIndex++;
                }
            tagIndex = 0;
            ArrayList<String> equipList = docTagsInfo.get("sap_xecm_equip_tags");
            if (equipList != null && !equipList.isEmpty())
                for (String equipValue : equipList) {
                    tagDocumentObj.setRepeatingString("sap_xecm_equip_tags", tagIndex, equipValue);
                    tagIndex++;
                }
            tagIndex = 0;
            ArrayList<String> taskList = docTagsInfo.get("sap_xecm_tsklst_tags");
            logger.debug("[DCTM] updateWOTagsValue2Doc taskList={}", taskList);
            if (taskList != null && !taskList.isEmpty())
                for (String taskValue : taskList) {
                    String taskListTagName = getTaskListWSName(taskValue, config, sessionObj);
                    tagDocumentObj.setRepeatingString("sap_xecm_tsklst_tags", tagIndex, taskListTagName);
                    tagIndex++;
                }
            tagIndex = 0;
            ArrayList<String> inspList = docTagsInfo.get("sap_xecm_insp_tags");
            if (inspList != null && !inspList.isEmpty())
                for (String inspValue : inspList) {
                    String inspTagName = getInspWSName(inspValue, config, sessionObj);
                    logger.debug("[DCTM] inspTagName={}", inspTagName);
                    tagDocumentObj.setRepeatingString("sap_xecm_insp_tags", tagIndex, inspTagName);
                    tagIndex++;
                }
            tagDocumentObj.save();
        } catch (Exception e) {
            logger.error("[DCTM] Error in updateWOTagsValue2Doc", e);
        }
    }

    private String getInspWSName(String inspValue, Properties config, IDfSession sessionObj) throws DfException {
        IDfQuery queryObj = new DfQuery();
        IDfCollection coll = null;
        String inspectionName = null;
        try {
            logger.debug("[DCTM] getInspWSName inspValue={}", inspValue);
            if (inspValue != null) {
                String strInspValueQry = config.getProperty(INSPLAN_INFO_SELECT_QUERY).replace("$inspValue", inspValue);
                logger.debug("[DCTM] strInspValueQry={}", strInspValueQry);
                queryObj.setDQL(strInspValueQry);
                coll = queryObj.execute(sessionObj, IDfQuery.DF_EXEC_QUERY);
                while (coll.next()) inspectionName = coll.getString("object_name");
            }
        } catch (Exception e) {
            logger.error("[DCTM] Error in getInspWSName for inspValue={}", inspValue, e);
        } finally {
            if (coll != null) coll.close();
        }
        return inspectionName;
    }

    private String getTaskListWSName(String taskListValue, Properties config, IDfSession sessionObj) throws DfException {
        IDfQuery queryObj = new DfQuery();
        IDfCollection coll = null;
        String taskListName = null;
        try {
            String strtaskListValueQry = config.getProperty(TASKLIST_INFO_SELECT_QUERY).replace("$taskListValue", taskListValue);
            logger.debug("[DCTM] strtaskListValueQry={}", strtaskListValueQry);
            queryObj.setDQL(strtaskListValueQry);
            coll = queryObj.execute(sessionObj, IDfQuery.DF_EXEC_QUERY);
            while (coll.next()) taskListName = coll.getString("object_name");
            logger.debug("[DCTM] taskListName={}", taskListName);
        } catch (Exception e) {
            logger.error("[DCTM] Error in getTaskListWSName for taskListValue={}", taskListValue, e);
        } finally {
            if (coll != null) coll.close();
        }
        return taskListName;
    }

    public HashMap<String, String> getWOParentObjects(String strWO, Properties config, IDfSession sessionObj)
            throws DfException {
        HashMap<String, String> woParentTagInfo = new HashMap<>();
        IDfQuery queryObj = new DfQuery();
        IDfCollection coll = null;
        String strObjectID           = null;
        String strParentEquipTag     = null;
        String strParentFLOCTag      = null;
        String strParentTaskListTag  = null;
        String strParentInspListTag  = null;
        try {
            strWOParentObjQry = config.getProperty(WO_PARENT_INFO_SELECT_QUERY).replace("$wo_num", strWO);
            logger.debug("[DCTM] strWOParentObjQry={}", strWOParentObjQry);
            queryObj.setDQL(strWOParentObjQry);
            coll = queryObj.execute(sessionObj, IDfQuery.DF_EXECREAD_QUERY);
            while (coll.next()) {
                strObjectID          = coll.getString("r_object_id");
                strParentEquipTag    = coll.getString("parent_equipment_id");
                strParentFLOCTag     = coll.getString("parent_floc_id");
                strParentTaskListTag = coll.getString("parent_tsklst_id");
                strParentInspListTag = coll.getString("parent_inspection_id");
            }
            if (strObjectID          != null && !strObjectID.isEmpty())          woParentTagInfo.put("r_object_id",           strObjectID);
            if (strParentEquipTag    != null && !strParentEquipTag.isEmpty())    woParentTagInfo.put("parent_equipment_id",   strParentEquipTag);
            if (strParentFLOCTag     != null && !strParentFLOCTag.isEmpty())     woParentTagInfo.put("parent_floc_id",        strParentFLOCTag);
            if (strParentTaskListTag != null && !strParentTaskListTag.isEmpty()) woParentTagInfo.put("parent_tsklst_id",      strParentTaskListTag);
            if (strParentInspListTag != null && !strParentInspListTag.isEmpty()) woParentTagInfo.put("parent_inspection_id",  strParentInspListTag);
        } catch (Exception e) {
            logger.error("[DCTM] Error in getWOParentObjects for strWO={}", strWO, e);
        } finally {
            if (coll != null) coll.close();
        }
        return woParentTagInfo;
    }

    public HashMap<String, ArrayList<String>> getTag2DocInfo(String tagAttrTypeName, Properties config,
            IDfSession sessionObj, String objectId) throws DfException {
        IDfQuery queryObj = new DfQuery();
        IDfCollection coll = null;
        String strObjectID              = null;
        String str_sap_xecm_equip_tags  = null;
        String str_sap_xecm_floc_tags   = null;
        String str_sap_xecm_insp_tags   = null;
        String str_sap_xecm_tsklst_tags = null;
        String str_sap_xecm_wo_tags     = null;
        HashMap<String, ArrayList<String>> docTagsInfo = new HashMap<>();
        try {
            logger.debug("[DCTM] DOC_TAG_SELECT_QUERY template={}", config.getProperty(DOC_TAG_SELECT_QUERY));
            strDocTagQuery = config.getProperty(DOC_TAG_SELECT_QUERY).replace("$document_type", tagAttrTypeName);
            strDocTagQuery = strDocTagQuery.replace("$object_id", objectId);
            logger.debug("[DCTM] strDocTagQuery={}", strDocTagQuery);
            queryObj.setDQL(strDocTagQuery);
            coll = queryObj.execute(sessionObj, IDfQuery.DF_EXECREAD_QUERY);
            while (coll.next()) {
                strObjectID              = coll.getString("r_object_id");
                str_sap_xecm_equip_tags  = coll.getAllRepeatingStrings("sap_xecm_equip_tags",  ",");
                str_sap_xecm_floc_tags   = coll.getAllRepeatingStrings("sap_xecm_floc_tags",   ",");
                str_sap_xecm_insp_tags   = coll.getAllRepeatingStrings("sap_xecm_insp_tags",   ",");
                str_sap_xecm_tsklst_tags = coll.getAllRepeatingStrings("sap_xecm_tsklst_tags", ",");
                str_sap_xecm_wo_tags     = coll.getAllRepeatingStrings("sap_xecm_wo_tags",     ",");
            }
            logger.debug("[DCTM] strObjectID={}", strObjectID);
            logger.debug("[DCTM] str_sap_xecm_equip_tags={}", str_sap_xecm_equip_tags);
            logger.debug("[DCTM] str_sap_xecm_floc_tags={}", str_sap_xecm_floc_tags);
            logger.debug("[DCTM] str_sap_xecm_insp_tags={}", str_sap_xecm_insp_tags);
            logger.debug("[DCTM] str_sap_xecm_tsklst_tags={}", str_sap_xecm_tsklst_tags);
            logger.debug("[DCTM] str_sap_xecm_wo_tags={}", str_sap_xecm_wo_tags);
            if (strObjectID              != null && !strObjectID.isEmpty())              docTagsInfo.put("r_object_id",           new ArrayList<>(Arrays.asList(strObjectID.split(","))));
            if (str_sap_xecm_floc_tags   != null && !str_sap_xecm_floc_tags.isEmpty())  docTagsInfo.put("sap_xecm_floc_tags",    new ArrayList<>(Arrays.asList(str_sap_xecm_floc_tags.split(","))));
            if (str_sap_xecm_equip_tags  != null && !str_sap_xecm_equip_tags.isEmpty()) docTagsInfo.put("sap_xecm_equip_tags",   new ArrayList<>(Arrays.asList(str_sap_xecm_equip_tags.split(","))));
            if (str_sap_xecm_insp_tags   != null && !str_sap_xecm_insp_tags.isEmpty())  docTagsInfo.put("sap_xecm_insp_tags",    new ArrayList<>(Arrays.asList(str_sap_xecm_insp_tags.split(","))));
            if (str_sap_xecm_tsklst_tags != null && !str_sap_xecm_tsklst_tags.isEmpty()) docTagsInfo.put("sap_xecm_tsklst_tags", new ArrayList<>(Arrays.asList(str_sap_xecm_tsklst_tags.split(","))));
            if (str_sap_xecm_wo_tags     != null && !str_sap_xecm_wo_tags.isEmpty())    docTagsInfo.put("sap_xecm_wo_tags",      new ArrayList<>(Arrays.asList(str_sap_xecm_wo_tags.split(","))));
            logger.debug("[DCTM] docTagsInfo={}", docTagsInfo);
        } catch (Exception e) {
            logger.error("[DCTM] Error in getTag2DocInfo for objectId={}", objectId, e);
        } finally {
            if (coll != null) coll.close();
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
        } catch (Exception e) {
            logger.error("[DCTM] Error in getBUTagTypeName for repositoryName={}", repositoryName, e);
        }
        return tagAttrTypeName;
    }

    public void getBUTagTypeMapping(Properties config) {
        try {
            String strBUTagTypeMap = config.getProperty(BU_NAME_AND_TAGATTR_TYPE_NAME);
            logger.debug("[DCTM] strBUTagTypeMap={}", strBUTagTypeMap);
            String[] temp = strBUTagTypeMap.split("\\;");
            for (String token : temp) {
                String[] temp1 = token.split("\\|");
                buTagTypeMap.put(temp1[0], temp1[1]);
            }
        } catch (Exception e) {
            logger.error("[DCTM] Error in getBUTagTypeMapping", e);
        }
    }

    void processDocLink2Tags(IDfSession sessionObj, String r_object_id) {
        try {
            logger.info("[DCTM] Running Core D2 Method for objectId={}", r_object_id);
            DfId id = new DfId(r_object_id);
            applyD2Configurations(id.toString(), sessionObj);
            logger.info("[DCTM] Core D2 Method completed for objectId={}", r_object_id);
        } catch (DfException e) {
            logger.error("[DCTM] Error in processDocLink2Tags for objectId={}", r_object_id, e);
        }
    }

    protected void applyD2Configurations(String qualifier, IDfSession sessionObj) throws DfException {
        IDfQuery query = new DfQuery();
        String relatedTagsDql = String.format(
                "execute do_method with method='D2CoreMethod', arguments='-docbase_name %s -user dmadmin -id %s"
                        + " -naming false -autolink true -security true -apply_for_vd false -create false -transaction false';",
                sessionObj.getDocbaseName(), qualifier);
        query.setDQL(relatedTagsDql);
        try {
            query.execute(sessionObj, IDfQuery.DF_QUERY);
            logger.info("[DCTM] Related Tags method successful for objectId={}", qualifier);
        } catch (Exception e) {
            logger.error("[DCTM] Related Tags method failed for objectId={}", qualifier, e);
        }
    }
}
