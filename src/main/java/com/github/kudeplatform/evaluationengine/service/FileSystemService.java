package com.github.kudeplatform.evaluationengine.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;

/**
 * @author timo.buechert
 */
@Service
@Slf4j
public class FileSystemService {

    public static final String KUDE_TMP_FOLDER_NAME = "kude-tmp";

    public static final String KUDE_TMP_FOLDER_PATH_WITH_TRAILING_SEPARATOR =
            System.getProperty("java.io.tmpdir") + File.separator + KUDE_TMP_FOLDER_NAME + File.separator;

    @PostConstruct
    public void init() {
        final File kudeTmpFolder = new File(KUDE_TMP_FOLDER_PATH_WITH_TRAILING_SEPARATOR);
        if (!kudeTmpFolder.exists() && !kudeTmpFolder.mkdirs()) {
            log.error("Failed to create KUDE tmp folder.");
            throw new RuntimeException("Failed to create KUDE tmp folder. Cannot continue.");
        }
    }
}
