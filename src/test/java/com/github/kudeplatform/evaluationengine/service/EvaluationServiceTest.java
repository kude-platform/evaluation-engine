package com.github.kudeplatform.evaluationengine.service;

import com.github.kudeplatform.evaluationengine.domain.ResultsEvaluation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Test
    void areResultsCorrect() {
        // given
        final EvaluationService evaluationService = new EvaluationService(null, null, null, null, null, null, null, settingsService, null, null, null, List.of());
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
        final EvaluationService evaluationService = new EvaluationService(null, null, null, null, null, null, null, settingsService, null, null, null, List.of());
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
}