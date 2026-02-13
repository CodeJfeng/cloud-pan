package com.jfeng.pan.server.common.stream.event.file;

import lombok.*;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 文件还原事件实体
 */

@EqualsAndHashCode
@ToString
@Getter
@Setter
public class FileRestoreEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = -375623845734L;

    /**
     * 被成功还原的文件记录ID集合
     */
    private List<Long> fileListId;


    public FileRestoreEvent(List<Long> fileListId) {
        this.fileListId = fileListId;
    }
}
