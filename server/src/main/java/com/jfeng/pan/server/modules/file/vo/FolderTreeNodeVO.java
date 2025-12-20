package com.jfeng.pan.server.modules.file.vo;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.jfeng.pan.web.serializer.IdEncryptSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@Tag(name = "文件夹树节点实体")
public class FolderTreeNodeVO implements Serializable {

    @Serial
    private static final long serialVersionUID = -654852365233234L;

    @Schema(name = "文件夹名称")
    private String label;

    @Schema(name = "文件ID")
    @JsonSerialize(using = IdEncryptSerializer.class)
    private Long id;

    @Schema(name = "父文件ID")
    @JsonSerialize(using = IdEncryptSerializer.class)
    private Long parentId;

    @Schema(name = "子节点集合")
    private List<FolderTreeNodeVO> children;

    public void print(){
        String jsonString = JSON.toJSONString(this);
        System.out.println(jsonString);
    }

}
