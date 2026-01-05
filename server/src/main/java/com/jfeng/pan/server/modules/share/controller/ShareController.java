package com.jfeng.pan.server.modules.share.controller;

import com.jfeng.pan.core.constants.RPanConstants;
import com.jfeng.pan.core.response.R;
import com.jfeng.pan.core.utils.IdUtil;
import com.jfeng.pan.server.common.annotation.LoginIgnore;
import com.jfeng.pan.server.common.annotation.NeedShareCode;
import com.jfeng.pan.server.common.utils.ShareIdUtil;
import com.jfeng.pan.server.common.utils.UserIdUtil;
import com.jfeng.pan.server.modules.share.context.*;
import com.jfeng.pan.server.modules.share.converter.ShareConverter;
import com.jfeng.pan.server.modules.share.po.CancelSharePO;
import com.jfeng.pan.server.modules.share.po.CheckShareCodePO;
import com.jfeng.pan.server.modules.share.po.CreateShareUrlPO;
import com.jfeng.pan.server.modules.share.service.IShareService;
import com.jfeng.pan.server.modules.share.vo.ShareDetailVO;
import com.jfeng.pan.server.modules.share.vo.ShareSimpleDetailVO;
import com.jfeng.pan.server.modules.share.vo.ShareUrlListVO;
import com.jfeng.pan.server.modules.share.vo.ShareUrlVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
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

    @Operation(
            summary = "取消分享",
            description = "该接口提供了取消分享的功能"
    )
    @DeleteMapping("share")
    public R cancelShare(@Validated @RequestBody CancelSharePO cancelSharePO){
        CancelShareContext context = new CancelShareContext();
        context.setUserId(UserIdUtil.get());
        context.setShareIdList(IdUtil.decryptIdList(cancelSharePO.getShareIds()));
        iShareService.cancelShare(context);
        return R.success("取消分享成功");
    }

    @Operation(
            summary = "校验分享码",
            description = "该接口提供了校验分享码的功能"
    )
    @LoginIgnore
    @PostMapping("share/code/check")
    public R<String> checkShareCode(@Validated @RequestBody CheckShareCodePO checkShareCodePO){
        CheckShareCodeContext context = new CheckShareCodeContext();
        context.setShareId(IdUtil.decrypt(checkShareCodePO.getShareCode()));
        context.setShareCode(checkShareCodePO.getShareCode());
        String token = iShareService.checkShareCode(context);
        return R.data(token);
    }

    @Operation(
            summary = "查询分享的详情",
            description = "该接口提供了查询分享的功能"
    )
    @LoginIgnore
    @NeedShareCode
    @GetMapping("share")
    public R<ShareDetailVO> detail(){
        QueryShareDetailContext context = new QueryShareDetailContext();
        context.setShareId(ShareIdUtil.get());
        ShareDetailVO vo = iShareService.detail(context);
        return R.data(vo);
    }

    @Operation(
            summary = "查询分享的简单详情",
            description = "该接口提供了查询分享的简单详情的功能"
    )
    @LoginIgnore
    @GetMapping("share/simple")
    public R<ShareSimpleDetailVO> simpleDetail(@NotBlank(message = "分享的ID不能为空") @RequestParam(value = "shareId", required = false) String shareId){
        QueryShareSimpleDetailContext context = new QueryShareSimpleDetailContext();
        context.setShareId(IdUtil.decrypt(shareId));
        ShareSimpleDetailVO vo = iShareService.simpleDetail(context);
        return R.data(vo);
    }

}
