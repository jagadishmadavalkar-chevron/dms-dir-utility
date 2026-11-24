package com.dir.sync.service.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.dir.sync.config.DirJobProperties;
import com.documentum.fc.client.IDfSession;

/**
 * Thin wrapper that delegates to the existing XECMDoc2TagServiceMethod
 * (kept in src/main/java/com/dir/sync/util/documentum/) without changing its logic.
 */
class XECMDoc2TagServiceMethodRunner {

    private static final Logger logger = LogManager.getLogger(XECMDoc2TagServiceMethodRunner.class);

    private final IDfSession    session;
    private final String        rObjectId;
    private final String        docBase;
    private final String        buName;
    private final DirJobProperties props;

    XECMDoc2TagServiceMethodRunner(IDfSession session, String rObjectId,
                                    String docBase, String buName, DirJobProperties props) {
        this.session   = session;
        this.rObjectId = rObjectId;
        this.docBase   = docBase;
        this.buName    = buName;
        this.props     = props;
    }

    void execute() throws Exception {
        com.dir.sync.util.documentum.XECMDoc2TagServiceMethod method =
                new com.dir.sync.util.documentum.XECMDoc2TagServiceMethod(
                        session, rObjectId, docBase, buName, props);
        method.applyD2Configurations();
    }
}
