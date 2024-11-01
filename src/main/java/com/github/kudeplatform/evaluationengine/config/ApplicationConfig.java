package com.github.kudeplatform.evaluationengine.config;

import com.github.kudeplatform.evaluationengine.domain.EvaluationTask;
import com.github.kudeplatform.evaluationengine.mapper.EvaluationEventMapper;
import com.github.kudeplatform.evaluationengine.mapper.EvaluationEventMapperImpl;
import com.github.kudeplatform.evaluationengine.mapper.EvaluationResultMapper;
import com.github.kudeplatform.evaluationengine.mapper.EvaluationResultMapperImpl;
import com.github.kudeplatform.evaluationengine.view.NotifiableComponent;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.util.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * @author timo.buechert
 */
@Configuration
@EnableScheduling
@EnableRetry
public class ApplicationConfig {

    @Bean
    public ApiClient kubernetesClient() throws IOException {
        final ApiClient client = Config.defaultClient();
        client.setConnectTimeout(100_000);
        io.kubernetes.client.openapi.Configuration.setDefaultApiClient(client);
        return client;
    }

    @Bean
    CoreV1Api coreV1Api() throws IOException {
        return new CoreV1Api(kubernetesClient());
    }

    @Bean
    BatchV1Api batchV1Api() throws IOException {
        return new BatchV1Api(kubernetesClient());
    }

    @Bean
    CustomObjectsApi customObjectsApi() throws IOException {
        return new CustomObjectsApi(kubernetesClient());
    }

    @Bean
    BlockingQueue<EvaluationTask> evaluationTaskQueue() {
        return new ArrayBlockingQueue<EvaluationTask>(100);
    }

    @Bean
    EvaluationEventMapper evaluationEventMapper() {
        return new EvaluationEventMapperImpl();
    }

    @Bean
    EvaluationResultMapper evaluationResultMapper() {
        return new EvaluationResultMapperImpl();
    }

    @Bean
    List<NotifiableComponent> activeViewComponents() {
        return new ArrayList<>();
    }

}
