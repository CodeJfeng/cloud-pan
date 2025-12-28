package com.jfeng.pan.server.modules.recycle.controller;

import com.jfeng.pan.core.constants.RPanConstants;
import com.jfeng.pan.core.response.R;
import com.jfeng.pan.core.utils.IdUtil;
import com.jfeng.pan.server.modules.file.vo.RPanUserFileVO;
import com.jfeng.pan.server.modules.recycle.context.QueryRecycleFileListContext;
import com.jfeng.pan.server.modules.recycle.context.RestoreContext;
import com.jfeng.pan.server.modules.recycle.po.RestorePO;
import com.jfeng.pan.server.modules.recycle.service.IRecycleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Arrays;
import java.util.List;

/**
 * 回收站模块控制器
 */
@Controller
@Tag(name = "回收站模块")
@Validated
public class RecycleController {

    @Autowired
    private IRecycleService iRecycleService;

    @Operation(summary  = "获取回收站文件列表",
        description = "该接口提供了获取回收站列表的功能",
        tags = {"回收站管理"}
    )
    @GetMapping("recycles")
    public R<List<RPanUserFileVO>> recycles(){
        QueryRecycleFileListContext queryRecycleFileListContext = new QueryRecycleFileListContext();
        queryRecycleFileListContext.setUserId(IdUtil.get());
        List<RPanUserFileVO> result = iRecycleService.recycles(queryRecycleFileListContext);
        return R.data(result);
    }

    @Operation(summary  = "回收站文件批量还原",
            description = "该接口提供了回收站文件批量还原的功能",
            tags = {"回收站管理"}
    )
    @PutMapping("recycle/restore")
    public R restore(@Validated @RequestBody RestorePO restorePO){
        RestoreContext context = new RestoreContext();
        context.setUserId(IdUtil.get());
        List<Long> fileIds = Arrays.stream(restorePO.getFileIds().split(RPanConstants.COMMON_SEPARATOR)).map(IdUtil::decrypt).toList();
        context.setFileIdList(fileIds);
        iRecycleService.restore(context);
        return R.success();
    }

}
