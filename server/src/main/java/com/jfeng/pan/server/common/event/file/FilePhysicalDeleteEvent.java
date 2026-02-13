//package com.jfeng.pan.server.common.event.file;
//
//import com.jfeng.pan.server.modules.file.entity.RPanUserFile;
//import lombok.EqualsAndHashCode;
//import lombok.Getter;
//import lombok.Setter;
//import lombok.ToString;
//import org.springframework.context.ApplicationEvent;
//
//import java.util.List;
//
///**
// * 文件被物理删除的事件实体
// */
//@Getter
//@Setter
//@EqualsAndHashCode
//@ToString
//public class FilePhysicalDeleteEvent extends ApplicationEvent {
//
//    /**
//     * 所有被删除的物理文件实体集合
//     */
//    private List<RPanUserFile> allRecords;
//
//
//    public FilePhysicalDeleteEvent(Object source, List<RPanUserFile> allRecords) {
//        super(source);
//        this.allRecords = allRecords;
//    }
//}
