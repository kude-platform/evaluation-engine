package com.github.kudeplatform.evaluationengine.service;

import com.github.kudeplatform.evaluationengine.domain.Repository;
import com.github.kudeplatform.evaluationengine.util.TextUtil;
import com.github.kudeplatform.evaluationengine.view.NotifiableComponent;
import de.jplag.JPlag;
import de.jplag.JPlagResult;
import de.jplag.Language;
import de.jplag.java.JavaLanguage;
import de.jplag.options.JPlagOptions;
import de.jplag.reporting.reportobject.ReportObjectFactory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import static com.github.kudeplatform.evaluationengine.service.FileSystemService.KUDE_PLAGIARISM_RESULTS_PATH;

@Slf4j
@Service
public class PlagiarismService implements ApplicationContextAware {

    final EvaluationService evaluationService;

    final FileSystemService fileSystemService;

    final SettingsService settingsService;

    final List<NotifiableComponent> activePlagiarismViewComponents;

    final ReentrantLock lock = new ReentrantLock();

    private ApplicationContext context;

    @Getter
    private String currentStatus = "Idle";

    public PlagiarismService(final EvaluationService evaluationService, final FileSystemService fileSystemService, final SettingsService settingsService,
                             @Qualifier(value = "activePlagiarismViewComponents") final List<NotifiableComponent> activePlagiarismViewComponents) {
        this.evaluationService = evaluationService;
        this.fileSystemService = fileSystemService;
        this.settingsService = settingsService;
        this.activePlagiarismViewComponents = activePlagiarismViewComponents;
    }

    @Override
    public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }

    public void checkPlagiarism(final String baseCodeRepository, final String repositoriesToCheckAsCsv) {
        if (!this.evaluationService.isNoJobRunning()) {
            log.error("Cannot check plagiarism while jobs are running");
            throw new IllegalStateException("Cannot check plagiarism while jobs are running");
        }

        if (baseCodeRepository == null || baseCodeRepository.isEmpty()) {
            throw new IllegalArgumentException("Base code repository must not be empty");
        }

        if (repositoriesToCheckAsCsv == null || repositoriesToCheckAsCsv.isEmpty()) {
            throw new IllegalArgumentException("Repositories to check must not be empty");
        }

        if (this.lock.isLocked()) {
            log.error("Cannot check plagiarism while another check is running");
            throw new IllegalStateException("Cannot check plagiarism while another check is running");
        }

        final Repository baseCode = new Repository("baseCode", baseCodeRepository, "", "");
        final List<Repository> repositoriesToCheck = TextUtil.parseRepositoriesFromMassInput(repositoriesToCheckAsCsv,
                settingsService.getGitUsername(), settingsService.getGitToken());

        // the asynchronous call must happen through the context to enable the @Async annotation
        // using this.checkPlagiarismAsync(baseCode, repositoriesToCheck) would bypass the proxy and thus the @Async annotation
        this.context.getBean(PlagiarismService.class).checkPlagiarismAsync(baseCode, repositoriesToCheck);
    }

    @Async
    public void checkPlagiarismAsync(final Repository baseCodeRepository, final List<Repository> repositoriesToCheck) {
        lock.lock();
        try {
            this.currentStatus = "Cloning repositories";

            this.fileSystemService.deleteAllRepositories();
            this.fileSystemService.deletePlagiarismResultIfExists();

            this.fileSystemService.cloneRepositories(Stream.concat(Stream.of(baseCodeRepository), repositoriesToCheck.stream()).toList(), this::setCurrentStatus);

            this.currentStatus = "Checking for plagiarism";
            final Language language = new JavaLanguage();
            final Set<File> submissionDirectories = Set.of(new File(FileSystemService.KUDE_SUBMISSIONS_PATH));
            final File baseCode = new File(FileSystemService.KUDE_SUBMISSIONS_PATH + File.separator + baseCodeRepository.name());
            final JPlagOptions options = new JPlagOptions(language, submissionDirectories, Set.of())
                    .withBaseCodeSubmissionDirectory(baseCode)
                    .withMaximumNumberOfComparisons(-1)
                    .withFileSuffixes(List.of(".java"));

            final JPlagResult result = JPlag.run(options);
            final ReportObjectFactory reportObjectFactory = new ReportObjectFactory(new File(KUDE_PLAGIARISM_RESULTS_PATH));
            reportObjectFactory.createAndSaveReport(result);

            this.setCurrentStatus("Plagiarism check completed");
            this.fileSystemService.deleteAllRepositories();
        } catch (final Exception e) {
            log.error("An error occurred while checking for plagiarism", e);
            this.setCurrentStatus("An error occurred while checking for plagiarism: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    public synchronized void setCurrentStatus(final String status) {
        this.currentStatus = status;
        this.activePlagiarismViewComponents.forEach(NotifiableComponent::dataChanged);
    }


}
