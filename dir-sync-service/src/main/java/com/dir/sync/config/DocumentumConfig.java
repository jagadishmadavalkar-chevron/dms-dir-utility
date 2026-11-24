package com.dir.sync.config;

import jakarta.annotation.PostConstruct;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.dir.sync.util.Decryptor;
import com.documentum.fc.client.DfClient;
import com.documentum.fc.client.IDfClient;
import com.documentum.fc.client.IDfSession;
import com.documentum.fc.client.IDfSessionManager;
import com.documentum.fc.common.DfLoginInfo;
import com.documentum.fc.common.IDfLoginInfo;

/**
 * Creates and manages the Documentum session.
 * Credentials are read from environment variables — same as the original DIRBulkUpdate.main().
 */
@Component
public class DocumentumConfig {

    private static final Logger logger = LogManager.getLogger(DocumentumConfig.class);

    @Value("${documentum.user:#{null}}")
    private String documentumUser;

    @Value("${documentum.password:#{null}}")
    private String documentumPasswordEncrypted;

    @Value("${documentum.docbase:#{null}}")
    private String docBase;

    private final Decryptor decryptor;

    private IDfSession     session;
    private IDfSessionManager sessionManager;
    private String         buName;

    public DocumentumConfig(Decryptor decryptor) {
        this.decryptor = decryptor;
    }

    @PostConstruct
    public void init() {
        try {
            // Resolve user from env var if not set via property
            if (documentumUser == null || documentumUser.isBlank()) {
                documentumUser = System.getenv("DocumentumUser");
            }
            if (docBase == null || docBase.isBlank()) {
                docBase = System.getenv("Docbase");
            }

            String password;
            if (documentumPasswordEncrypted == null || documentumPasswordEncrypted.isBlank()) {
                String enc = System.getenv("DocumentumPw");
                if (enc == null) {
                    logger.error("[INIT] DocumentumPw environment variable not set");
                    return;
                }
                password = decryptor.decrypt(enc);
            } else {
                password = decryptor.decrypt(documentumPasswordEncrypted);
            }

            if (documentumUser == null || docBase == null) {
                logger.error("[INIT] DocumentumUser or Docbase not configured");
                return;
            }

            logger.info("[INIT] Connecting to Documentum: user={} docbase={}", documentumUser, docBase);
            IDfClient client = new DfClient();
            sessionManager = client.newSessionManager();

            IDfLoginInfo loginInfo = new DfLoginInfo();
            loginInfo.setUser(documentumUser);
            loginInfo.setPassword(password);
            sessionManager.setIdentity(docBase, loginInfo);
            session = sessionManager.getSession(docBase);

            buName = deriveBuName(docBase);
            logger.info("[INIT] Documentum session created. docBase={} buName={}", docBase, buName);

        } catch (Exception e) {
            logger.error("[INIT] Failed to create Documentum session", e);
        }
    }

    /** Returns the active DFC session (may be null if connection failed). */
    public IDfSession getSession() {
        return session;
    }

    /** Returns the resolved BU name derived from the docbase name. */
    public String getBuName() {
        return buName;
    }

    /** Returns the docbase name. */
    public String getDocBase() {
        return docBase;
    }

    /**
     * Derives the BU name from the docbase name.
     * Mirrors the mapping logic in DIRBulkUpdate.main().
     */
    private static String deriveBuName(String docBase) {
        if (docBase == null) return null;
        String lc = docBase.toLowerCase();
        if (lc.contains("mcbu"))                       return "MCBU";
        if (lc.contains("cbu"))                        return "CBU";
        if (lc.contains("sjv"))                        return "SJV";
        if (lc.contains("gom"))                        return "GOM";
        if (lc.contains("dmcspo") || lc.contains("cpp")) return "CREG";
        if (lc.contains("sntb"))                       return "STCO";
        return null;
    }
}
