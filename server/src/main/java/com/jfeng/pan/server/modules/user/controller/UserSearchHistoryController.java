package com.jfeng.pan.server.modules.user.controller;


import com.jfeng.pan.core.response.R;
import com.jfeng.pan.server.common.utils.UserIdUtil;
import com.jfeng.pan.server.modules.user.context.QueryUserSearchHistoryContext;
import com.jfeng.pan.server.modules.user.service.IUserSearchHistoryService;
import com.jfeng.pan.server.modules.user.vo.UserSearchHistoryVO;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@Tag(name = "用户搜索历史")
@RestController
public class UserSearchHistoryController {
    @Autowired
    private IUserSearchHistoryService iUserSearchHistoryService;

    @Schema(
            name = "获取用户最新的搜索历史，默认前十条",
            description = "提供实现获取用户最新的搜索历史的功能",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    @GetMapping("user/search/histories")
    public R<List<UserSearchHistoryVO>> getUserSearchHistories(){
        QueryUserSearchHistoryContext context = new QueryUserSearchHistoryContext();
        context.setUserId(UserIdUtil.get());
        List<UserSearchHistoryVO> result = iUserSearchHistoryService.getUserSearchHistory(context);
        return R.data(result);
    }
}
