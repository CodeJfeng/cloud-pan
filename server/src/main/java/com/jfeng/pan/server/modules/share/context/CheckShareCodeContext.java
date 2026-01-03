package com.jfeng.pan.server.modules.share.context;

import com.jfeng.pan.server.modules.share.entity.RPanShare;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 校验分享码上下文实体对象
 */
@Data
public class CheckShareCodeContext implements Serializable {

    @Serial
    private static final long serialVersionUID = -213967455923L;

    /**
     * 分享的ID
     */
    private Long shareId;

    /**
     * 分享码
     */
    private String shareCode;

    /**
     * 对应的分享信息实体
     */
    private RPanShare record;


}
