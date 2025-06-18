package com.powsybl.network.conversion.server.dto;

import com.powsybl.network.conversion.server.NetworkConversionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.Function;

@Component
public class TempFileManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TempFileManager.class);

    public <T> T withTempFile(String prefix, String suffix, Function<Path, T> function) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile(prefix, suffix);
            LOGGER.debug("Created temp file: {}", tempFile);
            return function.apply(tempFile);
        } catch (Exception e) {
            throw NetworkConversionException.failedToCreateTmpFile(e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                    LOGGER.info("Deleted temp file: {}", tempFile);
                } catch (Exception e) {
                    throw NetworkConversionException.failedToDeleteTmpFile(e);
                }
            }
        }
    }

    public <T> T withTempDirectory(String prefix, Function<Path, T> function) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory(prefix);
            LOGGER.info("Created temp directory: {}", tempDir);
            return function.apply(tempDir);
        } catch (Exception e) {
            throw NetworkConversionException.failedToCreateTmpFile(e);
        } finally {
            if (tempDir != null) {
                deleteTempDirectory(tempDir);
            }
        }
    }

    private void deleteTempDirectory(Path directory) {
        try {
            Files.walk(directory)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            LOGGER.info("Deleted temp directory: {}", directory);
        } catch (Exception e) {
            throw NetworkConversionException.failedToDeleteTmpDirectory(e);
        }
    }
}
