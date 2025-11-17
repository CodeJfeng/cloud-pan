package com.jfeng.pan.web.exception;

import com.jfeng.pan.core.exception.RPanBusinessException;
import com.jfeng.pan.core.exception.RPanFrameworkException;
import com.jfeng.pan.core.response.R;
import com.jfeng.pan.core.response.ResponseCode;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
public class WebExceptionHandler {
   @ExceptionHandler(value = RPanBusinessException.class)
   public R rPanBusinessExceptionHandler(RPanBusinessException e){
       return R.fail(e.getCode(), e.getMessage());
   }

   @ExceptionHandler(value = MethodArgumentNotValidException.class)
   public R methodArgumentNotValidExceptionHandler(MethodArgumentNotValidException e){
       ObjectError objectError = e.getBindingResult().getAllErrors().stream().findFirst().get();
       return R.fail(ResponseCode.ERROR_PARAM.getCode(), objectError.getDefaultMessage());
   }

   @ExceptionHandler(value = ConstraintViolationException.class)
    public R constraintViolationExceptionHandler(ConstraintViolationException e){
       ConstraintViolation<?> constraintViolation = e.getConstraintViolations().stream().findFirst().get();
       return R.fail(ResponseCode.ERROR_PARAM.getCode(), constraintViolation.getMessage());
   }

   @ExceptionHandler(value = MissingServletRequestParameterException.class)
    public R missingServletRequestParameterExceptionHandler(MissingServletRequestParameterException e){
       return R.fail(ResponseCode.ERROR_PARAM.getCode(), e.getMessage());
   }

   @ExceptionHandler(value = IllegalStateException.class)
    public R illegalStateExceptionHandler(IllegalStateException e){
       return R.fail(ResponseCode.ERROR_PARAM);
   }

   @ExceptionHandler(value = BindException.class)
    public R bindExceptionHandler(BindException e){
       ObjectError objectError = e.getBindingResult().getAllErrors().stream().findFirst().get();
       return R.fail(ResponseCode.ERROR_PARAM.getCode(), objectError.getDefaultMessage());
   }

    /**
     * 服务器存在异常，返回通用异常ERROR
     * @param e
     * @return
     */
   @ExceptionHandler(value = RuntimeException.class)
    public R runtimeExceptionHandler(RuntimeException e){
       return R.fail(ResponseCode.ERROR.getCode(), e.getMessage());
   }

    @ExceptionHandler(value = RPanFrameworkException.class)
    public R runtimeExceptionHandler(RPanFrameworkException e){
        return R.fail(ResponseCode.ERROR.getCode(), e.getMessage());
    }
}
