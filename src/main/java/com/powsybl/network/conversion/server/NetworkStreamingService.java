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

    public ExportNetworkInfos getExportNetworkInfos(Network network, String format, String fileOrNetworkName, Properties exportProperties, long networkSize) {
        return withTempDirectory("network-export-", tempDir -> {
            try {
                DirectoryDataSource directoryDataSource = new DirectoryDataSource(tempDir, fileOrNetworkName);
                network.write(format, exportProperties, directoryDataSource);
                Set<String> listNames = directoryDataSource.listNames(".*");
                if (listNames.isEmpty()) {
                    throw new IOException("No files were created during export");
                }
                Path finalFile;
                if (listNames.size() == 1) {
                    String extension = listNames.iterator().next();
                    Path sourceFile = tempDir.resolve(extension);
                    finalFile = copyToTempFile(sourceFile, extension);
                } else {
                    finalFile = createStreamedZipFile(tempDir, listNames, fileOrNetworkName);
                }
                long fileSize = Files.size(finalFile);
                return new ExportNetworkInfos(fileOrNetworkName + "." + format.toLowerCase(), finalFile, networkSize, fileSize);
            } catch (IOException e) {
                throw NetworkConversionException.failedToStreamNetworkToFile(e);
            }
        });
    }

    public Path streamNetworkToFile(Network network, String format, Properties exportProperties, String fileName) {
        return withTempDirectory("network-export-", tempDir -> {
            try {
                DirectoryDataSource directoryDataSource = new DirectoryDataSource(tempDir, fileName);
                network.write(format, exportProperties, directoryDataSource);
                Set<String> createdFiles = directoryDataSource.listNames(".*");
                if (createdFiles.isEmpty()) {
                    throw new IOException("No files were created during export");
                }
                if (createdFiles.size() == 1) {
                    String createdFileName = createdFiles.iterator().next();
                    Path sourceFile = tempDir.resolve(createdFileName);

                    if (!Files.exists(sourceFile)) {
                        throw new IOException("File reported as created but does not exist: " + sourceFile);
                    }

                    long fileSize = Files.size(sourceFile);
                    if (fileSize == 0) {
                        throw new IOException("Created file is empty: " + sourceFile);
                    }
                    return copyToTempFile(sourceFile, fileName + "." + format.toLowerCase());
                } else {
                    return createStreamedZipFile(tempDir, createdFiles, fileName);
                }
            } catch (IOException e) {
                throw NetworkConversionException.failedToStreamNetworkToFile(e);
            }
        });
    }

    private Path copyToTempFile(Path sourceFile, String fileName) throws IOException {
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"));
        Path tempFile = tempDir.resolve(fileName);

        Files.copy(sourceFile, tempFile, StandardCopyOption.REPLACE_EXISTING);
        return tempFile;
    }

    private Path createStreamedZipFile(Path sourceDir, Set<String> fileNames, String baseFileName) throws IOException {
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"));
        Path zipFile = tempDir.resolve(baseFileName + ".zip");

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            for (String fileName : fileNames) {
                Path sourceFile = sourceDir.resolve(fileName);
                if (Files.exists(sourceFile)) {
                    ZipEntry entry = new ZipEntry(baseFileName + fileName);
                    zos.putNextEntry(entry);
                    Files.copy(sourceFile, zos);
                    zos.closeEntry();
                }
            }
        }
        return zipFile;
    }

    public <T> T withTempDirectory(String prefix, Function<Path, T> function) {
        Path tempDir = null;
        try {
            FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
            tempDir = Files.createTempDirectory(prefix, attr);
            LOGGER.info("Created temp directory: {}", tempDir);
            return function.apply(tempDir);
        } catch (IOException e) {
            throw NetworkConversionException.failedToCreateTmpDirectory(e);
        } finally {
            if (tempDir != null) {
                deleteTempDirectory(tempDir);
            }
        }
    }

    private void deleteTempDirectory(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            boolean deleted = Files.deleteIfExists(path);
                            if (!deleted && Files.exists(path)) {
                                LOGGER.warn("Failed to delete: {}", path);
                            }
                        } catch (IOException e) {
                            LOGGER.error("Error deleting path: {}", path, e);
                        }
                    });
            LOGGER.info("Deleted temp directory: {}", directory);
        } catch (IOException e) {
            throw NetworkConversionException.failedToDeleteTmpDirectory(e);
        }
    }
}
