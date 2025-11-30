package com.jfeng.pan.server.modules.user.converter;

import com.jfeng.pan.server.modules.user.context.*;
import com.jfeng.pan.server.modules.user.entity.RPanUser;
import com.jfeng.pan.server.modules.user.po.*;
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

}
