package com.github.kudeplatform.evaluationengine.async;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

import java.lang.reflect.Method;

/**
 * @author timo.buechert
 */
@Slf4j
public class EvaluationAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

    @Override
    public void handleUncaughtException(Throwable ex, Method method, Object... params) {
        log.error("Unexpected error occurred in async method: " + method.getName(), ex);
    }
    
}
