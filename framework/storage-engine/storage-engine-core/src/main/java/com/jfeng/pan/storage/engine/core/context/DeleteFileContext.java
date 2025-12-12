package com.jfeng.pan.storage.engine.core.context;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 删除物理文件的上下文实体信息
 */
@Data
public class DeleteFileContext implements Serializable {

    /**
     * 要删除的物理文件的路径集合
     *
     */
    private List<String> realPathList;


}
