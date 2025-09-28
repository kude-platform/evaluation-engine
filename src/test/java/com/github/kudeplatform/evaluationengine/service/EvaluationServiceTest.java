package com.github.kudeplatform.evaluationengine.service;

import com.github.kudeplatform.evaluationengine.domain.ResultsEvaluation;
import com.github.kudeplatform.evaluationengine.persistence.EvaluationResultEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * @author timo.buechert
 */
@ExtendWith(MockitoExtension.class)
class EvaluationServiceTest {

    @Mock
    SettingsService settingsService;

    EvaluationService evaluationService;

    @BeforeEach
    void setUp() {
        evaluationService = new EvaluationService(null, null, null,
                null, null, null, null, null,
                settingsService, null, null, null, List.of());
    }

    @Test
    void areResultsCorrect() {
        // given
        final String solution = """
                tpch_customer -> tpch_orders: [C_COMMENT, C_COMMENT] c [O_TOTALPRICE, O_ORDERPRIORITY]
                tpch_part -> tpch_lineitem: [P_COMMENT, P_TYPE] c [L_LINENUMBER, L_LINESTATUS]
                tpch_lineitem -> tpch_orders: [L_LINENUMBER, L_DISCOUNT] c [O_COMMENT, O_ORDERSTATUS]
                tpch_region -> tpch_customer: [R_REGIONKEY, R_NAME] c [C_NATIONKEY, C_COMMENT]
                tpch_orders -> tpch_customer: [O_ORDER, O_CLERK] c [C_ADDRESS, C_PHONE]
                tpch_customer -> tpch_customer: [C_COMMENT, C_COMMENT] c [C_NATIONKEY, C_NAME]
                tpch_customer -> tpch_nation: [C_PHONE, C_PHONE] c [N_NATIONKEY, N_NAME]
                """;

        final String results = """
                tpch_customer -> tpch_orders: [C_COMMENT, C_COMMENT] c [O_TOTALPRICE, O_ORDERPRIORITY]
                                tpch_lineitem -> tpch_orders: [L_LINENUMBER, L_DISCOUNT] c [O_COMMENT, O_ORDERSTATUS]  
                tpch_part -> tpch_lineitem: [P_COMMENT, P_TYPE] c [L_LINENUMBER, L_LINESTATUS]
                tpch_region -> tpch_customer: [R_REGIONKEY, R_NAME] c [C_NATIONKEY, C_COMMENT]
                                tpch_customer -> tpch_customer: [C_COMMENT, C_COMMENT] c [C_NATIONKEY, C_NAME]  
                tpch_orders -> tpch_customer: [O_ORDER, O_CLERK] c [C_ADDRESS, C_PHONE]  
                tpch_customer -> tpch_nation: [C_PHONE, C_PHONE] c [N_NATIONKEY, N_NAME]    
                                    
                """;
        when(settingsService.getExpectedSolution()).thenReturn(solution);


        // when
        final ResultsEvaluation resultsEvaluation = evaluationService.areResultsCorrect(results);

        // then
        assertThat(resultsEvaluation.correct()).isTrue();
        assertThat(resultsEvaluation.correctActual()).isEqualTo(7);
        assertThat(resultsEvaluation.correctExpected()).isEqualTo(7);
        assertThat(resultsEvaluation.totalActual()).isEqualTo(7);
        assertThat(resultsEvaluation.resultProportion()).isEqualTo("7/7/7");
    }

    @Test
    void areResultsCorrect_oneMissing() {
        // given
        final String solution = """
                tpch_customer -> tpch_orders: [C_COMMENT, C_COMMENT] c [O_TOTALPRICE, O_ORDERPRIORITY]
                tpch_part -> tpch_lineitem: [P_COMMENT, P_TYPE] c [L_LINENUMBER, L_LINESTATUS]
                tpch_lineitem -> tpch_orders: [L_LINENUMBER, L_DISCOUNT] c [O_COMMENT, O_ORDERSTATUS]
                tpch_region -> tpch_customer: [R_REGIONKEY, R_NAME] c [C_NATIONKEY, C_COMMENT]
                tpch_orders -> tpch_customer: [O_ORDER, O_CLERK] c [C_ADDRESS, C_PHONE]
                tpch_customer -> tpch_customer: [C_COMMENT, C_COMMENT] c [C_NATIONKEY, C_NAME]
                tpch_customer -> tpch_nation: [C_PHONE, C_PHONE] c [N_NATIONKEY, N_NAME]
                """;

        final String results = """
                tpch_customer -> tpch_orders: [C_COMMENT, C_COMMENT] c [O_TOTALPRICE, O_ORDERPRIORITY]
                                tpch_lineitem -> tpch_orders: [L_LINENUMBER, L_DISCOUNT] c [O_COMMENT, O_ORDERSTATUS]  
                tpch_part -> tpch_lineitem: [P_COMMENT, P_TYPE] c [L_LINENUMBER, L_LINESTATUS]
                tpch_region -> tpch_customer: [R_REGIONKEY, R_NAME] c [C_NATIONKEY, C_COMMENT]
                                tpch_customer -> tpch_customer: [C_COMMENT, C_COMMENT] c [C_NATIONKEY, C_NAME]  
                tpch_orders -> tpch_customer: [O_ORDER, O_CLERK] c [C_ADDRESS, C_PHONE]  
                                    
                """;
        when(settingsService.getExpectedSolution()).thenReturn(solution);


        // when
        final ResultsEvaluation resultsEvaluation = evaluationService.areResultsCorrect(results);

        // then
        assertThat(resultsEvaluation.correct()).isFalse();
        assertThat(resultsEvaluation.correctActual()).isEqualTo(6);
        assertThat(resultsEvaluation.correctExpected()).isEqualTo(7);
        assertThat(resultsEvaluation.totalActual()).isEqualTo(6);
        assertThat(resultsEvaluation.resultProportion()).isEqualTo("6/6/7");
    }

    @Test
    void getGrafanaLogsUrl() {
        // given
        final EvaluationResultEntity evaluationResultEntity = new EvaluationResultEntity();
        evaluationResultEntity.setTaskId("c0129853-2de1-46ce-a5e3-f50a7baf28a3");
        evaluationResultEntity.setStartTimestamp(ZonedDateTime.parse("2025-01-18T19:25:02.161551Z"));
        evaluationResultEntity.setEndTimestamp(ZonedDateTime.parse("2025-01-18T19:28:59.922278Z"));
        when(settingsService.getGrafanaHost()).thenReturn("pi14.local:32300");

        // when
        final String grafanaUrl = evaluationService.getGrafanaLogsUrl(evaluationResultEntity);

        // then
        assertThat(grafanaUrl).isEqualTo("http://pi14.local:32300/d/be2n0s0j623ggb/logs?orgId=1&from=1737228302161&to=1737228539922&timezone=browser&var-Filters=kubernetesPodName%7C%3D~%7Cddm-akka-c0129853-2de1-46ce-a5e3-f50a7baf28a3.%2A&var-Filters=index%7C%3D%7C0");
    }

    @Test
    void getGrafanaLogsUrl_endTimetampNull() {
        // given
        final EvaluationResultEntity evaluationResultEntity = new EvaluationResultEntity();
        evaluationResultEntity.setTaskId("c0129853-2de1-46ce-a5e3-f50a7baf28a3");
        evaluationResultEntity.setStartTimestamp(ZonedDateTime.parse("2025-01-18T19:25:02.161551Z"));
        when(settingsService.getGrafanaHost()).thenReturn("pi14.local:32300");

        // when
        final String grafanaUrl = evaluationService.getGrafanaLogsUrl(evaluationResultEntity);

        // then
        assertThat(grafanaUrl).isEqualTo("http://pi14.local:32300/d/be2n0s0j623ggb/logs?orgId=1&from=1737228302161&to=now&timezone=browser&var-Filters=kubernetesPodName%7C%3D~%7Cddm-akka-c0129853-2de1-46ce-a5e3-f50a7baf28a3.%2A&var-Filters=index%7C%3D%7C0");
    }

    @Test
    void getGrafanaResourcesUrl() {
        // given
        final EvaluationResultEntity evaluationResultEntity = new EvaluationResultEntity();
        evaluationResultEntity.setTaskId("c0129853-2de1-46ce-a5e3-f50a7baf28a3");
        evaluationResultEntity.setStartTimestamp(ZonedDateTime.parse("2025-01-18T19:25:02.161551Z"));
        evaluationResultEntity.setEndTimestamp(ZonedDateTime.parse("2025-01-18T19:28:59.922278Z"));
        when(settingsService.getGrafanaHost()).thenReturn("pi14.local:32300");

        // when
        final String grafanaUrl = evaluationService.getGrafanaResourcesUrl(evaluationResultEntity);

        // then
        assertThat(grafanaUrl).isEqualTo("http://pi14.local:32300/d/ee9ya50i64u80c?orgId=1&from=1737228302161&to=1737228539922&tz=Europe%2FZurich&theme=light&var-jobName=ddm-akka-c0129853-2de1-46ce-a5e3-f50a7baf28a3.*");
    }
}