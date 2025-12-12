package com.jfeng.pan.storage.engine.fastdfs;

import com.jfeng.pan.storage.engine.core.AbstractStorageEngine;
import com.jfeng.pan.storage.engine.core.context.DeleteFileContext;
import com.jfeng.pan.storage.engine.core.context.StoreFileContext;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class FastDFSStorageEngine extends AbstractStorageEngine {

    @Override
    protected void doStore(StoreFileContext context) throws IOException {

    }

    @Override
    protected void doDelete(DeleteFileContext context) throws IOException {

    }
}
