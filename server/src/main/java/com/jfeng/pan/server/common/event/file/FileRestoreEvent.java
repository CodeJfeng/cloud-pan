package com.jfeng.pan.server.common.event.file;

import lombok.*;
import org.springframework.context.ApplicationEvent;

import java.time.Clock;
import java.util.List;

/**
 * 文件还原事件实体
 */

@EqualsAndHashCode
@ToString
@Getter
@Setter
public class FileRestoreEvent extends ApplicationEvent {
    /**
     * 被成功还原的文件记录ID集合
     */
    private List<Long> fileListId;


    public FileRestoreEvent(Object source, List<Long> fileListId) {
        super(source);

        this.fileListId = fileListId;
    }
}
