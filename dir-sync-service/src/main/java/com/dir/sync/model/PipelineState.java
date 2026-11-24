package com.dir.sync.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONObject;
import org.springframework.stereotype.Component;

/**
 * Mutable per-run pipeline state — holds all the maps and runtime data
 * that accumulate as pipeline steps execute.
 * Call initialize() at the start of each job run to reset state.
 */
@Component
public class PipelineState {

    /** Documents to CREATE: chronicleId → { FLOC: "...", IPLN: "...", ... } */
    public JSONObject buMap = new JSONObject();

    /** Documents to UPDATE: chronicleId → { FLOC_create: "...", FLOC_remove: "...", ... } */
    public JSONObject buMapForUpdate = new JSONObject();

    /** Documents to DELETE: chronicleId → existing dirNumber */
    public Map<String, String> buMapForDelete = new HashMap<>();

    /** Planning plants for CREATE/UPDATE: chronicleId → plant */
    public JSONObject buMapWithPlants = new JSONObject();

    /** Planning plants for DELETE: chronicleId → plant */
    public Map<String, String> buMapForDeleteWithPlants = new HashMap<>();

    /** Current version: chronicleId → { r_object_id → dirNumber } */
    public Map<String, Map<String, String>> docsWithNewVersion = new HashMap<>();

    /** Existing DIRs: chronicleId → dirNumber */
    public Map<String, String> docsWithDir = new HashMap<>();

    /** Titles: chronicleId → title */
    public Map<String, String> titles = new HashMap<>();

    /** Business-object messages built for logging: chronicleId → message prefix */
    public Map<String, String> messages = new HashMap<>();

    /** FLOC alternate labels: floc label → alternate label */
    public Map<String, String> flocLabelToAlternate = new HashMap<>();

    /** Workspace values from D2 */
    public List<String> wsValues = new ArrayList<>();

    /** Current APIM auth token (shared across threads; updates are synchronised in AuthTokenService) */
    public JSONObject authToken = new JSONObject();

    /** Prevents duplicate error inserts on token failure — same AtomicBoolean semantics as original */
    public AtomicBoolean authTokenFlag = new AtomicBoolean(false);

    /** Resets all mutable state for a fresh job run. */
    public void initialize() {
        buMap                = new JSONObject();
        buMapForUpdate       = new JSONObject();
        buMapForDelete       = new HashMap<>();
        buMapWithPlants      = new JSONObject();
        buMapForDeleteWithPlants = new HashMap<>();
        docsWithNewVersion   = new HashMap<>();
        docsWithDir          = new HashMap<>();
        titles               = new HashMap<>();
        messages             = new HashMap<>();
        flocLabelToAlternate = new HashMap<>();
        wsValues             = new ArrayList<>();
        authToken            = new JSONObject();
        authTokenFlag.set(false);
    }
}
