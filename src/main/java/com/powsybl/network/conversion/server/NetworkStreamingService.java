package com.powsybl.network.conversion.server;

import com.powsybl.commons.datasource.DirectoryDataSource;
import com.powsybl.iidm.network.Network;
import com.powsybl.network.conversion.server.dto.ExportNetworkInfos;
import com.powsybl.network.conversion.server.dto.TempFileManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class NetworkStreamingService {

    @Autowired
    private TempFileManager tempFileManager;

    public ExportNetworkInfos getExportNetworkInfos(Network network, String format, String fileOrNetworkName, Properties exportProperties, long networkSize) throws IOException {
        return tempFileManager.withTempDirectory("network-export-", tempDir -> {
            try {
                DirectoryDataSource directoryDataSource = new DirectoryDataSource(tempDir, fileOrNetworkName);

                network.write(format, exportProperties, directoryDataSource);

                Set<String> listNames = directoryDataSource.listNames(".*");

                if (listNames.isEmpty()) {
                    throw new IOException("No files were created during export");
                }

                Path finalFile;
                String finalFileOrNetworkName = fileOrNetworkName;

                if (listNames.size() == 1) {
                    String extension = listNames.iterator().next();
                    Path sourceFile = tempDir.resolve(extension);
                    finalFile = copyToTempFile(sourceFile, extension);
                } else {
                    finalFile = createStreamedZipFile(tempDir, listNames, finalFileOrNetworkName);
                }

                long fileSize = Files.size(finalFile);
                return new ExportNetworkInfos(finalFileOrNetworkName + "." + format.toLowerCase(), finalFile, networkSize, fileSize);
            } catch (IOException e) {
                throw new RuntimeException("Failed to export network to file", e);
            }
        });
    }

    public Path streamNetworkToFile(Network network, String format, Properties exportProperties, String fileName) {
        return tempFileManager.withTempDirectory("network-export-", tempDir -> {
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
                throw new RuntimeException("Failed to stream network to file", e);
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
}
