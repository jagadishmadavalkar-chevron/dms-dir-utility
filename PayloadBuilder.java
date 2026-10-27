package DIRBulkUpdate;

import org.json.JSONArray;
import org.json.JSONObject;

public class PayloadBuilder {
	protected static String buildPayload(JSONObject boIdsJson, String r_object_id, BUProperties bu) {
		String root="";
		JSONObject table1 = new JSONObject();
		root=root+"{\""+Constants.sapCharacteristicValuesTable+"\":"+ buildCharacteristicValues(bu).toString()+",";
		root=root+"\""+Constants.sapClassAllocationsTable+"\":"+ buildClassAllocations(bu).toString()+",";
		root=root+"\""+Constants.sapDocumentDataTable+"\":"+ buildDocumentData("*",bu, bu.titles.get(r_object_id)).toString()+",";
		table1.put("item", buildDocumentFiles(r_object_id, bu, bu.titles.get(r_object_id))); // R4A
		root=root+"\""+Constants.sapDocumentFilesTable+"\":"+table1.toString()+",";
		root=root+"\""+Constants.sapObjectLinksTable+"\":"+ buildObjectLinks(boIdsJson,bu)+"}";
		return root.toString();
	}
	public static String buildUpdatePayload(JSONObject boIdsJson, String r_object_id, BUProperties bu, String dirNumber) {
		String root="";
		root=root+"{\""+Constants.sapDocTypeField+"\":\""+bu.docType+"\",";
		root=root+"\""+Constants.sapDocNumberField+"\":\""+dirNumber+"\",";
		root=root+"\""+Constants.sapDocVersionField+"\":\""+bu.docVersion+"\",";
		root=root+"\""+Constants.sapDocPartField+"\":\""+bu.docPart+"\",";
		root=root+"\""+Constants.sapObjectLinksTable+"\":"+ buildObjectLinks(boIdsJson,bu)+"}";
		return root.toString();

	}
	private static JSONObject buildCharacteristicValues(BUProperties bu) {
		JSONObject table = new JSONObject();
		JSONArray items = new JSONArray();
		JSONObject item = new JSONObject();
		item.put(Constants.sapClassTypeField, bu.classType);
		item.put(Constants.sapClassNameField, bu.className);
		item.put(Constants.sapCharNameField, bu.charName);
		items.put(item);
		table.put("item", item);
		return table;
	}
	private static JSONObject buildClassAllocations(BUProperties bu) {
		JSONObject table = new JSONObject();
		JSONArray items = new JSONArray();
		JSONObject item = new JSONObject();
		item.put(Constants.sapClassTypeField, bu.classType);
		item.put(Constants.sapClassNameField, bu.className);
		items.put(item);
		table.put("item", item);
		return table;
	}
	private static JSONArray buildDocumentData(String dirNumber, BUProperties bu, String title) {
		JSONArray items = new JSONArray();
		JSONObject item = new JSONObject();
		item.put(Constants.sapDocTypeField, bu.docType);
		item.put(Constants.sapDocNumberField, dirNumber);
		item.put(Constants.sapDocPartField, bu.docPart);
		item.put(Constants.sapDocVersionField, bu.docVersion);
		item.put(Constants.sapDescriptionField, title);
		items.put(item);
		return items;
	}
	private static JSONArray buildDocumentFiles(String r_object_id, BUProperties bu, String title) {
		//JSONObject table = new JSONObject();
		JSONArray items = new JSONArray();
		JSONObject item = new JSONObject();
		item.put(Constants.sapStorageCategoryField, DIRBulkUpdate.prop.getProperty(bu.buName+"-storageCategory"));
		item.put(Constants.sapWSApplicationField, bu.wsApplication);
		item.put(Constants.sapDocFileField, String.format(bu.docFile,bu.docBase,bu.docBase,r_object_id));
		item.put(Constants.sapDescriptionField, title); // R4A
		items.put(item);
		//table.put("item", item);
		return items;
	}
	private static String buildObjectLinks(JSONObject boIdsJson, BUProperties bu) {
		String table = null;
		for(String key : boIdsJson.keySet() ) {
			boolean delete = false;
			String keyJson=key;
			if(key.contains("_")) {
				
				if(key.contains("remove")) {
					delete = true;
				}
				key = key.split("_")[0];
			}
			
			if(boIdsJson.getString(keyJson).equals(Constants.na) || boIdsJson.getString(keyJson).equals("0-Not Available")) {
				//System.out.println("Skipping NA");
			}else {
				String[] ids = boIdsJson.getString(keyJson).split(",");
				for(String id : ids) {
					//System.out.println("id: "+key);
					JSONArray items = new JSONArray();
					JSONObject item = new JSONObject();
					if(delete) {
						//System.out.println("Here");
						item.put("DELETEVALUE", "X");
						item.put(Constants.sapObjectTypeField, Constants.objIndex.get(key));
						item.put(Constants.sapObjectKeyField, getSAPId(id,key,bu));
						
					}else {
						item.put(Constants.sapObjectTypeField, Constants.objIndex.get(key));
						item.put(Constants.sapObjectKeyField, getSAPId(id,key,bu));
					}
					//System.out.println("Item:" +item);
					if(!item.get(Constants.sapObjectKeyField).equals("NA")) {
						items.put(item);
						//System.out.println(item.toString());
						table = new StringBuilder(table+",\"item\":"+items.toString()).toString();
					}
				}
			}
			
		}
		
		//System.out.println("Table:" +table);
		table = new StringBuilder("{"+table.substring(5)+"}").toString();
		return table;
	}
	private static String getSAPId(String id, String key, BUProperties bu) {
		if(key.contains(Constants.equi) || key.contains(Constants.tlst) || key.contains(Constants.wrdr)) {
			return id;
		}else if(key.contains(Constants.floc)){ 
			if(bu.flocLabelToAlternate.containsKey(id)) {
				return bu.flocLabelToAlternate.get(id);
			}else {
				return(id);
				
			}
		}else {
			String[] ids = id.split(Constants.d2IplnSplitValue);
			return ids[ids.length-1];
		}
	}
}
