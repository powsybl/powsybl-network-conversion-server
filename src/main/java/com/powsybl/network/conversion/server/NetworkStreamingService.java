/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.conversion.server;

import com.powsybl.commons.datasource.DirectoryDataSource;
import com.powsybl.iidm.network.Network;
import com.powsybl.network.conversion.server.dto.ExportNetworkInfos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author Rehili Ghazwa <ghazwa.rehili at rte-france.com>
 */

@Service
public class NetworkStreamingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkStreamingService.class);
    private static final String ZIP_EXTENSION = ".zip";
    private static final String TEMP_DIR_PREFIX = "network-export-";

    public ExportNetworkInfos getExportNetworkInfos(Network network, String format,
                                                    String fileOrNetworkName, Properties exportProperties,
                                                    long networkSize) {
        return withTempDirectory(tempDir -> {
            Path finalFile = exportNetworkFiles(network, format, fileOrNetworkName, exportProperties, tempDir);
            long fileSize = getFileSize(finalFile);
            return new ExportNetworkInfos(fileOrNetworkName + "." + format.toLowerCase(), finalFile, networkSize, fileSize);
        });
    }

    public Path streamNetworkToFile(Network network, String format, Properties exportProperties, String fileName) {
        return withTempDirectory(tempDir -> exportNetworkFiles(network, format, fileName, exportProperties, tempDir));
    }

    private Path exportNetworkFiles(Network network, String format, String fileName, Properties exportProperties, Path tempDir) {
        try {
            DirectoryDataSource dataSource = new DirectoryDataSource(tempDir, fileName);
            network.write(format, exportProperties, dataSource);

            Set<String> createdFiles = dataSource.listNames(".*");
            validateCreatedFiles(createdFiles);

            return createdFiles.size() == 1
                    ? createSingleFile(tempDir, createdFiles, fileName, format)
                    : createZipFile(tempDir, createdFiles, fileName);

        } catch (IOException e) {
            throw NetworkConversionException.failedToStreamNetworkToFile(e);
        }
    }

    private void validateCreatedFiles(Set<String> createdFiles) throws IOException {
        if (createdFiles.isEmpty()) {
            throw new IOException("No files were created during export");
        }
    }

    private Path createSingleFile(Path tempDir, Set<String> createdFiles, String fileName, String format) throws IOException {
        String createdFileName = createdFiles.iterator().next();
        Path sourceFile = tempDir.resolve(createdFileName);
        validateSourceFile(sourceFile);
        String finalFileName = fileName + "." + format.toLowerCase();
        return copyToTempFile(sourceFile, finalFileName);
    }

    private void validateSourceFile(Path sourceFile) throws IOException {
        if (!Files.exists(sourceFile)) {
            throw new IOException("File reported as created but does not exist: " + sourceFile);
        }

        if (Files.size(sourceFile) == 0) {
            throw new IOException("Created file is empty: " + sourceFile);
        }
    }

    private Path copyToTempFile(Path sourceFile, String fileName) throws IOException {
        Path tempFile = getTempDirectory().resolve(fileName);
        Files.copy(sourceFile, tempFile, StandardCopyOption.REPLACE_EXISTING);
        return tempFile;
    }

    private Path createZipFile(Path sourceDir, Set<String> fileNames, String baseFileName) throws IOException {
        Path zipFile = getTempDirectory().resolve(baseFileName + ZIP_EXTENSION);

        try (ZipOutputStream zos = new ZipOutputStream(
                Files.newOutputStream(zipFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {

            for (String fileName : fileNames) {
                addFileToZip(sourceDir, fileName, baseFileName, zos);
            }
        }
        return zipFile;
    }

    private void addFileToZip(Path sourceDir, String fileName, String baseFileName, ZipOutputStream zos) throws IOException {
        Path sourceFile = sourceDir.resolve(fileName);
        if (Files.exists(sourceFile)) {
            ZipEntry entry = new ZipEntry(baseFileName + fileName);
            zos.putNextEntry(entry);
            Files.copy(sourceFile, zos);
            zos.closeEntry();
        }
    }

    private Path getTempDirectory() {
        return Paths.get(System.getProperty("java.io.tmpdir"));
    }

    public <T> T withTempDirectory(Function<Path, T> function) {
        Path tempDir = null;
        try {
            tempDir = createTempDirectoryPath();
            LOGGER.debug("Created temp directory: {}", tempDir);
            return function.apply(tempDir);
        } catch (IOException e) {
            throw NetworkConversionException.failedToCreateTmpDirectory(e);
        } finally {
            cleanupTempDirectory(tempDir);
        }
    }

    private Path createTempDirectoryPath() throws IOException {
        FileAttribute<Set<PosixFilePermission>> attr =
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
        return Files.createTempDirectory(TEMP_DIR_PREFIX, attr);
    }

    private void cleanupTempDirectory(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(this::deletePath);
        } catch (IOException e) {
            throw NetworkConversionException.failedToDeleteTmpDirectory(e);
        }
    }

    private void deletePath(Path path) {
        try {
            if (!Files.deleteIfExists(path) && Files.exists(path)) {
                LOGGER.warn("Failed to delete: {}", path);
            }
        } catch (IOException e) {
            LOGGER.error("Error deleting path: {}", path, e);
        }
    }

    private long getFileSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            LOGGER.warn("Could not determine file size for: {}", file, e);
            return 0L;
        }
    }
}
