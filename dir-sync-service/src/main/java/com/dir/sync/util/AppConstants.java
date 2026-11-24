package com.dir.sync.util;

public final class AppConstants {

    private AppConstants() {}

    // SAP object index keys (Documentum attribute → SAP table ID)
    public static final String floc = "FLOC";
    public static final String ipln = "IPLN";
    public static final String tlst = "TLST";
    public static final String wrdr = "WRDR";
    public static final String equi = "EQUI";

    // SAP object type lookup
    public static final java.util.Map<String, String> objIndex = java.util.Map.of(
            floc, "IFLOT",
            tlst, "PMPLKO",
            ipln, "QMPLKO",
            wrdr, "PMAUFK",
            equi, "EQUI"
    );

    // Documentum object types for each SAP BO type
    public static final java.util.Map<String, String> objTypes = java.util.Map.of(
            floc, "xecm_sap_functl_loc",
            tlst, "xecm_sap_tasklist",
            ipln, "xecm_sap_inspection_plan",
            wrdr, "xecm_sap_work_order",
            equi, "xecm_sap_equipment"
    );

    // SAP BAPI response keys
    public static final String updateResponse = "rfc:BAPI_DOCUMENT_CHANGE2.Response";
    public static final String createResponse = "rfc:BAPI_DOCUMENT_CREATE2.Response";
    public static final String deleteResponse = "rfc:BAPI_DOCUMENT_DELETE.Response";
    public static final String ret           = "rfc:return";
    public static final String message       = "MESSAGE";
    public static final String dirNumberResponse = "DOCUMENTNUMBER";

    // SAP DELETE header names
    public static final String sapDocTypeField    = "DocumentType";
    public static final String sapDocNumberField  = "DocumentNumber";
    public static final String sapDocPartField    = "DocumentPart";
    public static final String sapDocVersionField = "DocumentVersion";

    // Auth token request payload template
    public static final String authTokenCallPayload =
            "grant_type=password&client_id=%s&client_secret=%s&resource=%s&username=%s&password=%s";

    // D2 field / column names
    public static final String d2ChronicleId  = "i_chronicle_id";
    public static final String d2DirField     = "sap_dir_num";
    public static final String d2TitleField   = "title";
    public static final String dbDocId        = "r_object_id";
    public static final String dbTags         = "bo_ids";
    public static final String dbSnoCol       = "sno";
    public static final String dbDirNumberCol = "dir_number";
    public static final String dbOperationCol = "operation";
    public static final String dbPlant        = "plant";
    public static final String dbLabel        = "label";
    public static final String dbAlternateLabel = "alternate_label";

    // Processing / status values
    public static final String process            = "readytoprocess";
    public static final String create             = "CREATE";
    public static final String update             = "UPDATE";
    public static final String delete             = "DELETE";
    public static final String completedStatus    = "COMPLETED";
    public static final String errorStatus        = "ERROR";
    public static final String partCompletedStatus = "PART_COMPLETED";

    // IPLN split token
    public static final String d2IplnSplitValue = "Q";

    // D2 planning plant attribute
    public static final String d2PlanningPlant = "maint_plng_plan";
}
