package com.github.kudeplatform.evaluationengine.config;

import com.github.kudeplatform.evaluationengine.async.EvaluationAsyncExceptionHandler;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;

/**
 * @author timo.buechert
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new EvaluationAsyncExceptionHandler();
    }

}