package com.jfeng.pan.server.modules.share.context;

import lombok.Data;

import javax.servlet.http.HttpServletResponse;
import java.io.Serial;
import java.io.Serializable;

/**
 * 分享文件下载上下文实体对象
 */
@Data
public class ShareFileDownloadContext implements Serializable {
    @Serial
    private static final long serialVersionUID = -943531415434L;

    /**
     * 要下载的文件ID
     */
    private Long FileId;

    /**
     * 当前登录用户ID
     */
    private Long userId;

    /**
     * 分享ID
     */
    private Long ShareId;

    /**
     * Http相应实体
     */
    private HttpServletResponse response;
}
