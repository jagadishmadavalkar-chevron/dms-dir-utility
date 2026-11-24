package com.dir.sync.util;

import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONObject;

import com.dir.sync.model.BuContext;

/**
 * Builds SAP BAPI request payloads.
 * Logic is identical to the original PayloadBuilder — only the BUProperties reference
 * is replaced with the injected BuContext.
 */
public final class PayloadBuilder {

    private PayloadBuilder() {}

    public static String buildPayload(JSONObject boIdsJson, String chronicleId,
                                       BuContext ctx, String title) {
        JSONObject payload = new JSONObject();
        payload.put("DOCUMENTTYPE",    ctx.docType);
        payload.put("DOCUMENTPART",    ctx.docPart);
        payload.put("DOCUMENTVERSION", ctx.docVersion);
        payload.put("DESCRIPTION",     title);
        payload.put("CLASSTYPE",       ctx.classType);
        payload.put("CLASSNAME",       ctx.className);
        payload.put("CHARNAME",        ctx.charName);
        payload.put("CHARVALUE",       chronicleId);
        payload.put("DOCUMENTFILES",   buildDocumentFiles(ctx));
        payload.put("OBJECTLINKS",     buildObjectLinks(boIdsJson, ctx));
        return payload.toString();
    }

    public static String buildUpdatePayload(JSONObject boIdsJson, String chronicleId,
                                             BuContext ctx, String dirNumber) {
        JSONObject payload = new JSONObject();
        payload.put("DOCUMENTTYPE",    ctx.docType);
        payload.put("DOCUMENTNUMBER",  dirNumber);
        payload.put("DOCUMENTVERSION", ctx.docVersion);
        payload.put("DOCUMENTPART",    ctx.docPart);
        payload.put("OBJECTLINKS",     buildObjectLinks(boIdsJson, ctx));
        return payload.toString();
    }

    private static JSONArray buildObjectLinks(JSONObject boIdsJson, BuContext ctx) {
        JSONArray links = new JSONArray();
        Iterator<String> keys = boIdsJson.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            String val = boIdsJson.optString(key, "");
            if (val.isEmpty()) continue;

            String baseKey = key.replace("_create", "").replace("_remove", "");
            boolean isRemove = key.endsWith("_remove");

            for (String id : val.split(",")) {
                id = id.trim();
                if (id.isEmpty() || "NA".equals(id) || "0-Not Available".equals(id)) continue;

                String sapId = getSapId(id, baseKey, ctx);
                if (sapId == null || sapId.isEmpty()) continue;

                JSONObject link = new JSONObject();
                link.put("OBJECTTYPE", AppConstants.objIndex.getOrDefault(baseKey, baseKey));
                link.put("OBJECTKEY",  sapId);
                if (isRemove) {
                    link.put("DELETEVALUE", "X");
                }
                links.put(link);
            }
        }
        return links;
    }

    private static String getSapId(String id, String boType, BuContext ctx) {
        if (AppConstants.floc.equals(boType)) {
            String alt = ctx != null ? null : null;
            // Use alternate label if available
            // Note: flocLabelToAlternate is in PipelineState; callers must resolve before invoking
            return id; // alternate label substitution done by caller (SAPOperations/strategies)
        }
        if (AppConstants.ipln.equals(boType)) {
            // Split on "Q" and take last segment
            String[] parts = id.split(AppConstants.d2IplnSplitValue);
            return parts[parts.length - 1];
        }
        return id;
    }

    /**
     * Variant that accepts the flocLabelToAlternate map for FLOC resolution.
     */
    public static String buildPayload(JSONObject boIdsJson, String chronicleId,
                                       BuContext ctx, String title,
                                       java.util.Map<String, String> flocLabelToAlternate) {
        JSONObject resolved = resolveFloc(boIdsJson, flocLabelToAlternate);
        return buildPayload(resolved, chronicleId, ctx, title);
    }

    public static String buildUpdatePayload(JSONObject boIdsJson, String chronicleId,
                                             BuContext ctx, String dirNumber,
                                             java.util.Map<String, String> flocLabelToAlternate) {
        JSONObject resolved = resolveFloc(boIdsJson, flocLabelToAlternate);
        return buildUpdatePayload(resolved, chronicleId, ctx, dirNumber);
    }

    private static JSONObject resolveFloc(JSONObject boIdsJson, java.util.Map<String, String> map) {
        JSONObject out = new JSONObject(boIdsJson.toString());
        for (String key : out.keySet()) {
            if (!key.startsWith("FLOC")) continue;
            String val = out.optString(key, "");
            if (val.isEmpty()) continue;
            String[] ids = val.split(",");
            StringBuilder resolved = new StringBuilder();
            for (String id : ids) {
                String alt = map.getOrDefault(id.trim(), id.trim());
                if (resolved.length() > 0) resolved.append(",");
                resolved.append(alt);
            }
            out.put(key, resolved.toString());
        }
        return out;
    }

    private static JSONArray buildDocumentFiles(BuContext ctx) {
        JSONArray files = new JSONArray();
        JSONObject file = new JSONObject();
        file.put("STORAGECATEGORY", ctx.docFile);
        file.put("WSAPPLICATION",   ctx.wsApplication);
        files.put(file);
        return files;
    }
}
