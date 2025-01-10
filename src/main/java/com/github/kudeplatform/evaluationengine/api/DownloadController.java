package com.github.kudeplatform.evaluationengine.api;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.github.kudeplatform.evaluationengine.service.FileSystemService.*;

/**
 * @author timo.buechert
 */
@RestController
@RequestMapping("/api/files")
@Slf4j
public class DownloadController {

    public static final String PLAGIARISM_RESULTS_DOWNLOAD_PATH = "/download/plagiarismResults";

    public static final String PLAGIARISM_RESULTS_DOWNLOAD_PATH_FULL = "/api/files" + PLAGIARISM_RESULTS_DOWNLOAD_PATH;

    @RequestMapping(value = "/download/single/{file_name}", method = RequestMethod.GET)
    public void getFile(
            @PathVariable("file_name") String fileName,
            HttpServletResponse response) {
        getFile(fileName, response, KUDE_TMP_FOLDER_PATH_WITH_TRAILING_SEPARATOR);
    }

    @RequestMapping(value = "/download/data/{file_name}", method = RequestMethod.GET)
    public void getDataset(
            @PathVariable("file_name") String fileName,
            HttpServletResponse response) {
        getFile(fileName, response, KUDE_DATA_PATH);
    }

    @RequestMapping(value = "/download/plagiarismResults", method = RequestMethod.GET)
    public void getPlagirismResult(final HttpServletResponse response) {
        getFile(KUDE_PLAGIARISM_RESULTS_FILE, response, KUDE_PLAGIARISM_PATH);
    }

    private void getFile(@PathVariable("file_name") String fileName, HttpServletResponse response, String dataPath) {
        try {
            final InputStream is = new FileInputStream(dataPath + fileName);
            IOUtils.copy(is, response.getOutputStream());
            response.flushBuffer();
        } catch (FileNotFoundException ex) {
            log.info("File not found. Filename was '{}'", fileName, ex);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        } catch (IOException ex) {
            log.info("Error writing file to output stream. Filename was '{}'", fileName, ex);
            throw new RuntimeException("IOError writing file to output stream");
        }
    }

    @RequestMapping(value = "/download/pattern/{file_name_pattern}", method = RequestMethod.GET, produces = "application/zip")
    public void getMultipleFilesUsingPattern(
            @PathVariable("file_name_pattern") String fileNamePattern,
            HttpServletResponse response) {
        final File folder = new File(KUDE_TMP_FOLDER_PATH_WITH_TRAILING_SEPARATOR);
        final File[] files = folder.listFiles((dir, name) -> name.contains(fileNamePattern));

        if (files == null || files.length == 0) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(byteArrayOutputStream);
             ZipOutputStream zipOutputStream = new ZipOutputStream(bufferedOutputStream)) {

            for (final File file : files) {
                try (InputStream is = new FileInputStream(file)) {
                    zipOutputStream.putNextEntry(new ZipEntry(file.getName()));
                    IOUtils.copy(is, zipOutputStream);
                    zipOutputStream.closeEntry();
                }
            }
            zipOutputStream.finish();
            zipOutputStream.flush();
            response.getOutputStream().write(byteArrayOutputStream.toByteArray());
            response.addHeader("Content-Disposition", "attachment; filename=\"" + fileNamePattern + ".zip\"");
            response.flushBuffer();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
