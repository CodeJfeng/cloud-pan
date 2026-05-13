package com.jfeng.pan.server.modules.user.converter;

import com.jfeng.pan.server.modules.file.entity.RPanUserFile;
import com.jfeng.pan.server.modules.user.context.ChangePasswordContext;
import com.jfeng.pan.server.modules.user.context.CheckAnswerContext;
import com.jfeng.pan.server.modules.user.context.CheckUsernameContext;
import com.jfeng.pan.server.modules.user.context.ResetPasswordContext;
import com.jfeng.pan.server.modules.user.context.UserLoginContext;
import com.jfeng.pan.server.modules.user.context.UserRegisterContext;
import com.jfeng.pan.server.modules.user.entity.RPanUser;
import com.jfeng.pan.server.modules.user.po.ChangePasswordPO;
import com.jfeng.pan.server.modules.user.po.CheckAnswerPO;
import com.jfeng.pan.server.modules.user.po.CheckUsernamePO;
import com.jfeng.pan.server.modules.user.po.ResetPasswordPO;
import com.jfeng.pan.server.modules.user.po.UserLoginPO;
import com.jfeng.pan.server.modules.user.po.UserRegisterPO;
import com.jfeng.pan.server.modules.user.vo.UserInfoVO;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-05-13T15:28:33+0800",
    comments = "version: 1.5.5.Final, compiler: Eclipse JDT (IDE) 3.46.0.v20260407-0427, environment: Java 21.0.10 (Eclipse Adoptium)"
)
@Component
public class UserConverterImpl implements UserConverter {

    @Override
    public UserRegisterContext userRegisterPO2UserRegisterContext(UserRegisterPO userRegisterPO) {
        if ( userRegisterPO == null ) {
            return null;
        }

        UserRegisterContext userRegisterContext = new UserRegisterContext();

        userRegisterContext.setAnswer( userRegisterPO.getAnswer() );
        userRegisterContext.setPassword( userRegisterPO.getPassword() );
        userRegisterContext.setQuestion( userRegisterPO.getQuestion() );
        userRegisterContext.setUsername( userRegisterPO.getUsername() );

        return userRegisterContext;
    }

    @Override
    public RPanUser userRegisterContext2RPanUser(UserRegisterContext userRegisterContext) {
        if ( userRegisterContext == null ) {
            return null;
        }

        RPanUser rPanUser = new RPanUser();

        rPanUser.setAnswer( userRegisterContext.getAnswer() );
        rPanUser.setQuestion( userRegisterContext.getQuestion() );
        rPanUser.setUsername( userRegisterContext.getUsername() );

        return rPanUser;
    }

    @Override
    public UserLoginContext userLoginPO2RPanLoginContext(UserLoginPO userLoginPO) {
        if ( userLoginPO == null ) {
            return null;
        }

        UserLoginContext userLoginContext = new UserLoginContext();

        userLoginContext.setPassword( userLoginPO.getPassword() );
        userLoginContext.setUsername( userLoginPO.getUsername() );

        return userLoginContext;
    }

    @Override
    public CheckUsernameContext checkUsernamePO2CheckUsernameContext(CheckUsernamePO checkUsernamePO) {
        if ( checkUsernamePO == null ) {
            return null;
        }

        CheckUsernameContext checkUsernameContext = new CheckUsernameContext();

        checkUsernameContext.setUsername( checkUsernamePO.getUsername() );

        return checkUsernameContext;
    }

    @Override
    public CheckAnswerContext checkAnswerPO2CheckAnswerContext(CheckAnswerPO checkAnswerPO) {
        if ( checkAnswerPO == null ) {
            return null;
        }

        CheckAnswerContext checkAnswerContext = new CheckAnswerContext();

        checkAnswerContext.setAnswer( checkAnswerPO.getAnswer() );
        checkAnswerContext.setQuestion( checkAnswerPO.getQuestion() );
        checkAnswerContext.setUsername( checkAnswerPO.getUsername() );

        return checkAnswerContext;
    }

    @Override
    public ResetPasswordContext resetPasswordPO2ResetPasswordContext(ResetPasswordPO resetPasswordPO) {
        if ( resetPasswordPO == null ) {
            return null;
        }

        ResetPasswordContext resetPasswordContext = new ResetPasswordContext();

        resetPasswordContext.setPassword( resetPasswordPO.getPassword() );
        resetPasswordContext.setToken( resetPasswordPO.getToken() );
        resetPasswordContext.setUsername( resetPasswordPO.getUsername() );

        return resetPasswordContext;
    }

    @Override
    public ChangePasswordContext changePasswordPO2ChangePasswordContext(ChangePasswordPO changePasswordPO) {
        if ( changePasswordPO == null ) {
            return null;
        }

        ChangePasswordContext changePasswordContext = new ChangePasswordContext();

        changePasswordContext.setNewPassword( changePasswordPO.getNewPassword() );
        changePasswordContext.setOldPassword( changePasswordPO.getOldPassword() );

        return changePasswordContext;
    }

    @Override
    public UserInfoVO assembleUserInfoVO(RPanUser rPanUser, RPanUserFile rPanUserFile) {
        if ( rPanUser == null && rPanUserFile == null ) {
            return null;
        }

        UserInfoVO userInfoVO = new UserInfoVO();

        if ( rPanUser != null ) {
            userInfoVO.setUsername( rPanUser.getUsername() );
        }
        if ( rPanUserFile != null ) {
            userInfoVO.setRootFileId( rPanUserFile.getFileId() );
            userInfoVO.setRootFilename( rPanUserFile.getFilename() );
        }

        return userInfoVO;
    }
}
