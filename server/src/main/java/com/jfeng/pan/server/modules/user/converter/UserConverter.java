package com.jfeng.pan.server.modules.user.converter;

import com.jfeng.pan.server.modules.file.entity.RPanUserFile;
import com.jfeng.pan.server.modules.user.context.*;
import com.jfeng.pan.server.modules.user.entity.RPanUser;
import com.jfeng.pan.server.modules.user.po.*;
import com.jfeng.pan.server.modules.user.vo.UserInfoVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel="spring")
public interface UserConverter {
    /**
     * UserRegisterPO转化成UserRegisterContext
     *
     * @param userRegisterPO
     * @return
     */
    UserRegisterContext userRegisterPO2UserRegisterContext(UserRegisterPO userRegisterPO);

    /**
     * UserRegisterContext转RPanUser
     *
     * @param userRegisterContext
     * @return
     */
    @Mapping(target = "password", ignore = true)
    RPanUser userRegisterContext2RPanUser(UserRegisterContext userRegisterContext);

    /**
     * UserLoginPO转化成UserLoginContext
     * @param userLoginPO
     * @return
     */
    UserLoginContext userLoginPO2RPanLoginContext(UserLoginPO userLoginPO);

    /**
     * CheckUsernamePO装化成 CheckUsernameContext
     * @param checkUsernamePO
     * @return
     */
    CheckUsernameContext checkUsernamePO2CheckUsernameContext(CheckUsernamePO checkUsernamePO);

    /**
     * CheckAnswerPO装化成 CheckAnswerContext
     * @param checkAnswerPO checkAnswer的PO对象
     * @return CheckAnswerContext上下文
     */
    CheckAnswerContext checkAnswerPO2CheckAnswerContext(CheckAnswerPO checkAnswerPO);

    /**
     * ResetPasswordPO 转化成 ResetPasswordContext
     * @param resetPasswordPO resetPassword的PO对象
     * @return ResetPasswordContext上下文
     */
    ResetPasswordContext  resetPasswordPO2ResetPasswordContext(ResetPasswordPO resetPasswordPO);

    /**
     * ChangePasswordPO 转化成 ChangePasswordContext
     * @param changePasswordPO changePasswordPO的PO对象
     * @return ChangePasswordContext上下文
     */
    ChangePasswordContext changePasswordPO2ChangePasswordContext(ChangePasswordPO changePasswordPO);

    /**
     * 拼装用户基本信息实体
     * @param rPanUser
     * @param rPanUserFile
     * @return
     */
    @Mapping(source = "rPanUser.username", target = "username")
    @Mapping(source = "rPanUserFile.fileId", target = "rootFileId")
    @Mapping(source = "rPanUserFile.filename", target = "rootFilename")
    UserInfoVO assembleUserInfoVO(RPanUser rPanUser, RPanUserFile rPanUserFile);
}
