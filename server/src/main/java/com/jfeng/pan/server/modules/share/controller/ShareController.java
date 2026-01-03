package com.jfeng.pan.server.modules.share.controller;

import com.jfeng.pan.core.constants.RPanConstants;
import com.jfeng.pan.core.response.R;
import com.jfeng.pan.core.utils.IdUtil;
import com.jfeng.pan.server.common.utils.UserIdUtil;
import com.jfeng.pan.server.modules.share.context.CreateShareUrlContext;
import com.jfeng.pan.server.modules.share.context.QueryShareListContext;
import com.jfeng.pan.server.modules.share.converter.ShareConverter;
import com.jfeng.pan.server.modules.share.po.CreateShareUrlPO;
import com.jfeng.pan.server.modules.share.service.IShareService;
import com.jfeng.pan.server.modules.share.vo.ShareUrlListVO;
import com.jfeng.pan.server.modules.share.vo.ShareUrlVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@Tag(name = "分享接口")
public class ShareController {

    @Autowired
    private IShareService iShareService;

    @Autowired
    private ShareConverter shareConverter;

    @Operation(
            summary = "创建分享链接",
            description = "该接口提供了创建分享链接的功能"
    )
    @PostMapping("share")
    public R<ShareUrlVO> create(@Validated @RequestBody CreateShareUrlPO createShareUrlPO){
        CreateShareUrlContext context = shareConverter.createShareUrlPO2CreateShareUrlContext(createShareUrlPO);

        String shareFileIds = createShareUrlPO.getShareFileIds();
        List<Long> shareFileIdList = Arrays.stream(shareFileIds.split(RPanConstants.COMMON_SEPARATOR)).map(IdUtil::decrypt).toList();
        context.setShareFileIdList(shareFileIdList);

        ShareUrlVO vo = iShareService.create(context);
        return R.data(vo);
    }

    @Operation(
            summary = "查询分享链接列表",
            description = "该接口提供了查询分享链接列表的功能"
    )
    @GetMapping("shares")
    public R<List<ShareUrlListVO>> getShares(){
        QueryShareListContext shareListContext = new QueryShareListContext();
        shareListContext.setUserId(UserIdUtil.get());
        List<ShareUrlListVO> result = iShareService.getShares(shareListContext);
        return R.data(result);
    }

}
