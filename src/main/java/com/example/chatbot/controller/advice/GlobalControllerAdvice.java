package com.example.chatbot.controller.advice;

import com.example.chatbot.common.exception.RateLimitExceededException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class GlobalControllerAdvice {

    @ExceptionHandler(IllegalArgumentException.class)
    public ModelAndView handleIllegalArgument(IllegalArgumentException e) {
        return errorPage(HttpStatus.BAD_REQUEST, e.getMessage());
    }


    @ExceptionHandler(RateLimitExceededException.class)
    public ModelAndView handleRateLimit(RateLimitExceededException e) {
        return errorPage(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ModelAndView handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> {
                    String defaultMessage = fe.getDefaultMessage();
                    if (defaultMessage != null && !defaultMessage.isBlank()) {
                        return defaultMessage;
                    }
                    return fe.getField() + " 값이 올바르지 않습니다.";
                })
                .orElse("요청값이 올바르지 않습니다.");
        return errorPage(HttpStatus.BAD_REQUEST, msg);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ModelAndView handleMissingParam(MissingServletRequestParameterException e) {
        return errorPage(HttpStatus.BAD_REQUEST, e.getParameterName() + "은(는) 필수입니다.");
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleEtc(Exception e) {
        return errorPage(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");
    }

    private ModelAndView errorPage(HttpStatus status, String message) {
        ModelAndView mav = new ModelAndView("error/common-error");
        mav.setStatus(status);
        mav.addObject("status", status.value());
        mav.addObject("reason", status.getReasonPhrase());
        mav.addObject("message", message == null ? "" : message);
        return mav;
    }
}
