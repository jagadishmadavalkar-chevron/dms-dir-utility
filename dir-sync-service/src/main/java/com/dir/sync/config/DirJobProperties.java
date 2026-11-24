package com.dir.sync.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import jakarta.annotation.PostConstruct;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.documentum.fc.client.DfQuery;
import com.documentum.fc.client.IDfCollection;
import com.documentum.fc.client.IDfQuery;
import com.documentum.fc.client.IDfSession;

/**
 * Loads all configuration from the D2 data dictionary at startup —
 * mirrors the original DIRBulkUpdate.getD2DictionaryConfigValues().
 * All runtime code reads from this bean instead of the static Properties field.
 */
@Component
public class DirJobProperties {

    private static final Logger logger = LogManager.getLogger(DirJobProperties.class);

    private static final String DICTIONARY_NAME = "xecm-sap-dir-service-bulk-config";

    @Autowired
    private DocumentumConfig documentumConfig;

    private final Properties props = new Properties();

    @PostConstruct
    public void loadFromD2() {
        IDfSession session = null;
        IDfCollection collection = null;
        try {
            session = documentumConfig.getSession();
            if (session == null) {
                logger.error("[CONFIG] Documentum session is null — cannot load data dictionary");
                return;
            }
            Map<String, String> configValues = new HashMap<>();
            IDfQuery query = new DfQuery();
            query.setDQL(
                "select r_object_id, object_name, locale_value from d2_dictionary_value " +
                "where dictionary_name='" + DICTIONARY_NAME + "'"
            );
            collection = query.execute(session, IDfQuery.DF_EXECREAD_QUERY);
            while (collection.next()) {
                configValues.put(
                    collection.getString("object_name"),
                    collection.getString("locale_value")
                );
            }
            if (!configValues.isEmpty()) {
                props.putAll(configValues);
                logger.info("[CONFIG] Data dictionary loaded: {} entries", props.size());
            } else {
                logger.error("[CONFIG] Data dictionary '{}' is empty or not found", DICTIONARY_NAME);
            }
        } catch (Exception e) {
            logger.error("[CONFIG] Error loading data dictionary", e);
        } finally {
            try { if (collection != null) collection.close(); } catch (Exception ignored) {}
        }
    }

    /** Returns the value for the given dictionary key (may be null). */
    public String get(String key) {
        return props.getProperty(key);
    }

    /** Returns all loaded properties as a String map. */
    public Map<String, String> getAll() {
        Map<String, String> map = new HashMap<>();
        props.stringPropertyNames().forEach(k -> map.put(k, props.getProperty(k)));
        return map;
    }

    /** Overrides a property in-memory (does not write back to D2). */
    public void set(String key, String value) {
        props.setProperty(key, value);
    }
}
