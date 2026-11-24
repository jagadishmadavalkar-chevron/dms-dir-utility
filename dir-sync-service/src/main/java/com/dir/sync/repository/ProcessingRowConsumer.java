package com.dir.sync.repository;

import com.dir.sync.model.DirRecord;

@FunctionalInterface
public interface ProcessingRowConsumer {
    void accept(DirRecord record) throws Exception;
}
