package com.github.kudeplatform.evaluationengine.view;

import com.github.kudeplatform.evaluationengine.domain.SupportedModes;
import com.github.kudeplatform.evaluationengine.persistence.LogEventDefinitionEntity;
import com.github.kudeplatform.evaluationengine.persistence.LogEventDefinitionRepository;
import com.github.kudeplatform.evaluationengine.persistence.SettingsRepository;
import com.github.kudeplatform.evaluationengine.service.EvaluationService;
import com.github.kudeplatform.evaluationengine.service.KubernetesService;
import com.github.kudeplatform.evaluationengine.service.SettingsService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import io.kubernetes.client.openapi.ApiException;
import jakarta.annotation.PostConstruct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static com.github.kudeplatform.evaluationengine.service.SettingsService.DEFAULT_AKKA_EVALUATION_IMAGE;
import static com.github.kudeplatform.evaluationengine.service.SettingsService.DEFAULT_SPARK_EVALUATION_IMAGE;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@Import(SettingsViewTest.MockKubernetesConfig.class)
class SettingsViewTest extends KaribuTest {

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private SettingsRepository settingsRepository;

    @Autowired
    private LogEventDefinitionRepository logEventDefinitionRepository;

    @MockitoBean
    private KubernetesService kubernetesService;

    @MockitoBean
    private EvaluationService evaluationService;

    @TestConfiguration
    static class MockKubernetesConfig {
        @Autowired
        private KubernetesService kubernetesService;

        @PostConstruct
        void configureMocks() throws ApiException {
            // Configure the mock during Spring context initialization
            when(kubernetesService.getNumberOfNodes()).thenReturn(2);
        }
    }

    @BeforeEach
    void init() {
        settingsRepository.deleteAll();
    }

    @Test
    void testModeChangeUpdatesEvaluationImageToDefault_whenNoCustomValueSet() {
        // given
        settingsService.setMode(SupportedModes.SPARK.getMode());

        // navigate to view
        UI.getCurrent().navigate(SettingsView.class);

        final Select<String> modeSelect = _get(Select.class, spec -> spec.withLabel("Mode"));
        final TextField evaluationImageField = _get(TextField.class, spec -> spec.withLabel("Evaluation image"));

        // verify initial state - should show SPARK default
        assertThat(modeSelect.getValue()).isEqualTo(SupportedModes.SPARK.getMode());
        assertThat(evaluationImageField.getValue()).isEqualTo(DEFAULT_SPARK_EVALUATION_IMAGE);

        // when - change mode to AKKA
        modeSelect.setValue(SupportedModes.AKKA.getMode());

        // then - evaluation image should update to AKKA default
        assertThat(evaluationImageField.getValue()).isEqualTo(DEFAULT_AKKA_EVALUATION_IMAGE);

        // when - change mode back to SPARK
        modeSelect.setValue(SupportedModes.SPARK.getMode());

        // then - evaluation image should update back to SPARK default
        assertThat(evaluationImageField.getValue()).isEqualTo(DEFAULT_SPARK_EVALUATION_IMAGE);
    }

    @Test
    void testModeChangeDoesNotUpdateEvaluationImage_whenCustomValueIsSet() {
        // given - set a custom evaluation image
        final String customImage = "registry.local/custom-image:1.0.0";
        settingsService.setMode(SupportedModes.SPARK.getMode());
        settingsService.setEvaluationImage(customImage);

        // navigate to view
        UI.getCurrent().navigate(SettingsView.class);

        final Select<String> modeSelect = _get(Select.class, spec -> spec.withLabel("Mode"));
        final TextField evaluationImageField = _get(TextField.class, spec -> spec.withLabel("Evaluation image"));

        // verify initial state - should show custom value
        assertThat(modeSelect.getValue()).isEqualTo(SupportedModes.SPARK.getMode());
        assertThat(evaluationImageField.getValue()).isEqualTo(customImage);

        // when - change mode to AKKA
        modeSelect.setValue(SupportedModes.AKKA.getMode());

        // then - evaluation image should remain the custom value (NOT change to AKKA default)
        assertThat(evaluationImageField.getValue()).isEqualTo(customImage);

        // when - change mode back to SPARK
        modeSelect.setValue(SupportedModes.SPARK.getMode());

        // then - evaluation image should still remain the custom value
        assertThat(evaluationImageField.getValue()).isEqualTo(customImage);
    }

    @Test
    void testInitialLoad_displaysCorrectSettingsValues() {
        // given - set some custom settings
        settingsService.setTimeoutInSeconds("300");
        settingsService.setReplicationFactor("3");
        settingsService.setMaxJobsPerNode("2");
        settingsService.setGitUsername("testuser");
        settingsService.setMode(SupportedModes.AKKA.getMode());
        settingsService.setCpuRequest("2");

        // when - navigate to view
        UI.getCurrent().navigate(SettingsView.class);

        // then - fields should display the saved values
        final TextField timeoutField = _get(TextField.class, spec -> spec.withLabel("Timeout in seconds"));
        final TextField replicationField = _get(TextField.class, spec -> spec.withLabel("Replication factor"));
        final TextField maxJobsField = _get(TextField.class, spec -> spec.withLabel("Max jobs per node"));
        final TextField gitUsernameField = _get(TextField.class, spec -> spec.withLabel("Git username"));
        final Select<String> modeSelect = _get(Select.class, spec -> spec.withLabel("Mode"));
        final TextField cpuRequestField = _get(TextField.class, spec -> spec.withLabel("CPU request"));

        assertThat(timeoutField.getValue()).isEqualTo("300");
        assertThat(replicationField.getValue()).isEqualTo("3");
        assertThat(maxJobsField.getValue()).isEqualTo("2");
        assertThat(gitUsernameField.getValue()).isEqualTo("testuser");
        assertThat(modeSelect.getValue()).isEqualTo(SupportedModes.AKKA.getMode());
        assertThat(cpuRequestField.getValue()).isEqualTo("2");
    }

    @Test
    void testSaveButton_persistsChanges() {
        // given - navigate to view
        UI.getCurrent().navigate(SettingsView.class);

        final FormLayout formLayout = _get(FormLayout.class);
        final TextField timeoutField = _get(TextField.class, spec -> spec.withLabel("Timeout in seconds"));
        final TextField gitUsernameField = _get(TextField.class, spec -> spec.withLabel("Git username"));
        final PasswordField gitTokenField = _get(PasswordField.class, spec -> spec.withLabel("Git token"));
        final TextField cpuLimitField = _get(TextField.class, spec -> spec.withLabel("CPU limit"));

        // Get save button from within the form layout
        final Button saveButton = _get(formLayout, Button.class, spec -> spec.withText("Save"));

        // when - change values and save
        timeoutField.setValue("450");
        gitUsernameField.setValue("newuser");
        gitTokenField.setValue("token");
        cpuLimitField.setValue("4");
        saveButton.click();

        // then - settings should be persisted
        assertThat(settingsService.getTimeoutInSeconds()).isEqualTo(450);
        assertThat(settingsService.getGitUsername()).isEqualTo("newuser");
        assertThat(settingsService.getGitToken()).isEqualTo("token");
        assertThat(settingsService.getCpuLimit()).isEqualTo("4");
    }

    @Test
    void testValidation_preventsInvalidTimeoutValue() {
        // given - navigate to view
        UI.getCurrent().navigate(SettingsView.class);

        final FormLayout formLayout = _get(FormLayout.class);
        final TextField timeoutField = _get(TextField.class, spec -> spec.withLabel("Timeout in seconds"));
        final Button saveButton = _get(formLayout, Button.class, spec -> spec.withText("Save"));

        final String originalTimeout = String.valueOf(settingsService.getTimeoutInSeconds());

        // when - enter invalid (non-numeric) value and save
        timeoutField.setValue("invalid");
        saveButton.click();

        // then - timeout should not be updated (validation prevents save)
        assertThat(String.valueOf(settingsService.getTimeoutInSeconds())).isEqualTo(originalTimeout);
    }

    @Test
    void testReplicationFactorUpdate_callsEvaluationService() {
        // given
        doNothing().when(evaluationService).updateNumberOfParallelJobs(anyInt(), anyInt());

        UI.getCurrent().navigate(SettingsView.class);

        final FormLayout formLayout = _get(FormLayout.class);
        final TextField replicationField = _get(TextField.class, spec -> spec.withLabel("Replication factor"));
        final TextField maxJobsField = _get(TextField.class, spec -> spec.withLabel("Max jobs per node"));
        final Button saveButton = _get(formLayout, Button.class, spec -> spec.withText("Save"));

        // when - change replication factor and save
        replicationField.setValue("4");
        maxJobsField.setValue("3");
        saveButton.click();

        // then - evaluation service should be called with new values
        verify(evaluationService).updateNumberOfParallelJobs(4, 3);
        assertThat(settingsService.getReplicationFactor()).isEqualTo(4);
        assertThat(settingsService.getMaxJobsPerNode()).isEqualTo(3);
    }

    @Test
    void testExpectedSolutionSave_persistsValue() {
        // given - navigate to view
        UI.getCurrent().navigate(SettingsView.class);

        final TextArea expectedSolutionInput = _get(TextArea.class, spec -> spec.withLabel("Expected Solution"));
        final List<Button> saveButtons = expectedSolutionInput.getParent().get()
                .getChildren()
                .filter(component -> component instanceof Button)
                .map(component -> (Button) component)
                .filter(button -> "Save".equals(button.getText()))
                .toList();

        assertThat(saveButtons).hasSize(1);
        final Button saveButton = saveButtons.get(0);

        // when - change expected solution and save
        final String newExpectedSolution = "supplier -> customer: [S_KEY] c [C_KEY]";
        expectedSolutionInput.setValue(newExpectedSolution);
        saveButton.click();

        // then - expected solution should be persisted
        assertThat(settingsService.getExpectedSolution()).isEqualTo(newExpectedSolution);
    }

    @Test
    void testLogEventDefinitions_addEditDelete() {
        // given - navigate to view
        UI.getCurrent().navigate(SettingsView.class);

        final int initialCount = logEventDefinitionRepository.findAll().size();

        // when - delete one existing log event definition
        final List<LogEventDefinitionEntity> existingDefinitions = logEventDefinitionRepository.findAll();
        assertThat(existingDefinitions).isNotEmpty();
        final LogEventDefinitionEntity toDelete = existingDefinitions.get(0);

        logEventDefinitionRepository.delete(toDelete);

        // then - count should decrease
        assertThat(logEventDefinitionRepository.findAll()).hasSize(initialCount - 1);

        // when - add a new log event definition
        final LogEventDefinitionEntity newDefinition = new LogEventDefinitionEntity();
        newDefinition.setType("TEST_ERROR");
        newDefinition.setPatterns("TestException, TestError");
        newDefinition.setLevel("ERROR");
        logEventDefinitionRepository.save(newDefinition);

        // then - count should increase
        assertThat(logEventDefinitionRepository.findAll()).hasSize(initialCount);

        // when - edit the new definition
        newDefinition.setPatterns("UpdatedTestException");
        logEventDefinitionRepository.save(newDefinition);

        // then - the definition should be updated
        final LogEventDefinitionEntity updated = logEventDefinitionRepository.findById(newDefinition.getId()).get();
        assertThat(updated).isNotNull();
        assertThat(updated.getPatterns()).isEqualTo("UpdatedTestException");
    }

    @Test
    void testLogEventDefinitionModal_addNewDefinition() {
        // given - navigate to view
        UI.getCurrent().navigate(SettingsView.class);

        final int initialCount = logEventDefinitionRepository.findAll().size();

        // when - click "New item" button to open modal
        final Button newItemButton = _get(Button.class, spec -> spec.withText("New item"));
        newItemButton.click();

        // Get the dialog and its fields
        final Dialog dialog = _get(Dialog.class);
        final TextField typeField = _get(dialog, TextField.class, spec -> spec.withLabel("Type"));
        final TextField patternsField = _get(dialog, TextField.class, spec -> spec.withLabel("Patterns"));
        final Select<String> levelSelect = _get(dialog, Select.class);

        // Fill in the form
        typeField.setValue("CUSTOM_TEST_ERROR");
        patternsField.setValue("CustomException, CustomError");
        levelSelect.setValue("FATAL");

        // Click the "Add" button in the dialog
        final Button addButton = _get(dialog, Button.class, spec -> spec.withText("Add"));
        addButton.click();

        // then - the new definition should be added to the repository
        final List<LogEventDefinitionEntity> allDefinitions = logEventDefinitionRepository.findAll();
        assertThat(allDefinitions).hasSize(initialCount + 1);

        // Verify the new definition has the correct values
        final LogEventDefinitionEntity newDefinition = allDefinitions.stream()
                .filter(def -> "CUSTOM_TEST_ERROR".equals(def.getType()))
                .findFirst()
                .orElse(null);

        assertThat(newDefinition).isNotNull();
        assertThat(newDefinition.getPatterns()).isEqualTo("CustomException, CustomError");
        assertThat(newDefinition.getLevel()).isEqualTo("FATAL");
    }

}