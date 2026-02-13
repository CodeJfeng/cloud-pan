package com.jfeng.pan.server.common.stream.event.file;

import com.jfeng.pan.server.modules.file.entity.RPanUserFile;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 文件被物理删除的事件实体
 */
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class FilePhysicalDeleteEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = -474563148253L;

    /**
     * 所有被删除的物理文件实体集合
     */
    private List<RPanUserFile> allRecords;


    public FilePhysicalDeleteEvent( List<RPanUserFile> allRecords) {
        this.allRecords = allRecords;
    }
}
