package com.github.kudeplatform.evaluationengine.config;

import com.github.kudeplatform.evaluationengine.domain.EvaluationTask;
import com.github.kudeplatform.evaluationengine.mapper.EvaluationEventMapper;
import com.github.kudeplatform.evaluationengine.mapper.EvaluationEventMapperImpl;
import com.github.kudeplatform.evaluationengine.view.NotifiableComponent;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.apis.EventsV1Api;
import io.kubernetes.client.util.Config;
import okhttp3.Protocol;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

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
        client.setHttpClient(client
                .getHttpClient()
                .newBuilder()
                .protocols(Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1))
                .readTimeout(Duration.ZERO)
                .pingInterval(1, TimeUnit.MINUTES)
                .build());
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
    EventsV1Api eventsV1Api() throws IOException {
        return new EventsV1Api(kubernetesClient());
    }

    @Bean
    BlockingQueue<EvaluationTask> evaluationTaskQueue() {
        return new ArrayBlockingQueue<>(500);
    }

    @Bean
    EvaluationEventMapper evaluationEventMapper() {
        return new EvaluationEventMapperImpl();
    }

    @Bean(name = "activeEvaluationViewComponents")
    List<NotifiableComponent> activeEvaluationViewComponents() {
        return new ArrayList<>();
    }

    @Bean(name = "activeDatasetViewComponents")
    List<NotifiableComponent> activeDatasetViewComponents() {
        return new ArrayList<>();
    }

    @Bean
    ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(12);
        executor.setMaxPoolSize(36);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("EvaluationTaskExecutor-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "asyncEvaluatorExecutorService")
    ExecutorService executorService() {
        return Executors.newCachedThreadPool();
    }

}
