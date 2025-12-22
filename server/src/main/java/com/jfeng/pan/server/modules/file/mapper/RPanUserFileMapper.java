package com.jfeng.pan.server.modules.file.mapper;

import com.jfeng.pan.server.modules.file.context.FileSearchContext;
import com.jfeng.pan.server.modules.file.context.QueryFileListContext;
import com.jfeng.pan.server.modules.file.entity.RPanUserFile;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jfeng.pan.server.modules.file.vo.FileSearchResultVO;
import com.jfeng.pan.server.modules.file.vo.RPanUserFileVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
* @author 16837
* @description 针对表【r_pan_user_file(用户文件信息表)】的数据库操作Mapper
* @createDate 2025-11-06 19:22:58
* @Entity com.jfeng.pan.server.modules.file.entity.RPanUserFile
*/
public interface RPanUserFileMapper extends BaseMapper<RPanUserFile> {

    /**
     * 查询用户的文件列表
     * @param queryFileListContext
     * @return
     */
    List<RPanUserFileVO> selectFileList(@Param("param") QueryFileListContext queryFileListContext);

    /**
     * 文件搜索
     * @param context
     * @return
     */
    List<FileSearchResultVO> searchFile(@Param("param")FileSearchContext context);
}




