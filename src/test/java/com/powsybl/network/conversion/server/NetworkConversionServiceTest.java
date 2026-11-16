/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.network.conversion.server;

import com.powsybl.commons.datasource.DirectoryDataSource;
import com.powsybl.iidm.network.Network;
import com.powsybl.network.conversion.server.dto.CompressionType;
import com.powsybl.network.conversion.server.dto.ExportNetworkInfos;
import com.powsybl.network.conversion.server.elasticsearch.EquipmentInfosService;
import com.powsybl.network.store.client.NetworkStoreService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ContextConfigurationWithTestChannel
class NetworkConversionServiceTest {

    @Autowired
    private NetworkConversionService service;

    @MockitoBean
    private NetworkStoreService networkStoreService;

    @MockitoBean
    private EquipmentInfosService equipmentInfosService;

    @MockitoBean
    private NetworkConversionExecutionService networkConversionExecutionService;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private NetworkConversionObserver networkConversionObserver;

    @MockitoBean
    private ImportExportExecutionService importExportExecutionService;

    @MockitoBean
    private S3Client s3Client;

    private final List<Path> tempDirectories = new ArrayList<>();

    @BeforeEach
    void setUp() {
        tempDirectories.clear();
    }

    @AfterEach
    void tearDown() throws IOException {
        for (Path tempDirectory : tempDirectories) {
            deleteTempDirectory(tempDirectory);
        }
    }

    @Test
    void testGetMainFileNameWithExtensionFindsNameWithExtension() {
        assertEquals("network.unknown", NetworkConversionService.getMainFileNameWithExtension("network", Set.of("network.unknown", "network_data")).get());
    }

    @Test
    void testGetMainFileNameWithExtensionStaysEmptyIfNot() {
        assertTrue(NetworkConversionService.getMainFileNameWithExtension("network", Set.of("network_data", "other.xml")).isEmpty());
    }

    @Test
    void testGetExportNetworkInfosCreatesZipWithMainAndCompanionFiles() throws Exception {
        Network network = mock(Network.class);
        mockNetworkWrite(network, "network.xiidm", "network_anonymized.xiidm");

        ExportNetworkInfos infos = service.getExportNetworkInfos(network, "XIIDM", CompressionType.ZIP, "network", new Properties(), 12);
        tempDirectories.add(infos.getTempFilePath().getParent());

        assertEquals("network.xiidm.zip", infos.getNetworkName());
        assertEquals(12, infos.getNumberBuses());
        assertEquals(Set.of("network.xiidm", "network_anonymized.xiidm"), readZipEntryNames(infos.getTempFilePath()));
    }

    @Test
    void testGetExportNetworkInfosCreatesGzipFromMatchingFile() throws Exception {
        Network network = mock(Network.class);
        mockNetworkWrite(network, "network.xiidm");

        ExportNetworkInfos infos = service.getExportNetworkInfos(network, "XIIDM", CompressionType.GZIP, "network", new Properties(), 1);
        tempDirectories.add(infos.getTempFilePath().getParent());

        assertEquals("network.xiidm.gz", infos.getNetworkName());
        try (GZIPInputStream input = new GZIPInputStream(Files.newInputStream(infos.getTempFilePath()))) {
            assertEquals("network.xiidm", new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void testGetExportNetworkInfosUsesNetworkNameForCgmesZipWithoutMatchingFile() throws Exception {
        Network network = mock(Network.class);
        mockNetworkWrite(network, "network_model.xml", "network_metadata.rdf");

        ExportNetworkInfos infos = service.getExportNetworkInfos(network, "CGMES", CompressionType.ZIP, "network", new Properties(), 3);
        tempDirectories.add(infos.getTempFilePath().getParent());

        assertEquals("network.zip", infos.getNetworkName());
        assertEquals(Set.of("network_model.xml", "network_metadata.rdf"), readZipEntryNames(infos.getTempFilePath()));
    }

    @Test
    void testGetExportNetworkInfosFailsWhenNoFilesAreWritten() {
        Network network = mock(Network.class);

        NetworkConversionException exception = assertThrows(NetworkConversionException.class,
                () -> service.getExportNetworkInfos(network, "XIIDM", CompressionType.ZIP, "network", new Properties(), 1));

        assertEquals("No files were created during export", exception.getCause().getMessage());
    }

    @Test
    void testGetExportNetworkInfosFailsWhenZipHasNoMatchingFileOutsideCgmes() throws Exception {
        Network network = mock(Network.class);
        mockNetworkWrite(network, "network_other.xml");

        NetworkConversionException exception = assertThrows(NetworkConversionException.class,
                () -> service.getExportNetworkInfos(network, "XIIDM", CompressionType.ZIP, "network", new Properties(), 1));

        assertInstanceOf(IllegalStateException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("network_other.xml"));
    }

    @Test
    void testGetExportNetworkInfosFailsWhenGzipHasNoMatchingFile() throws Exception {
        Network network = mock(Network.class);
        mockNetworkWrite(network, "network_other.xml");

        NetworkConversionException exception = assertThrows(NetworkConversionException.class,
                () -> service.getExportNetworkInfos(network, "XIIDM", CompressionType.GZIP, "network", new Properties(), 1));

        assertInstanceOf(IllegalStateException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("network_other.xml"));
    }

    private static void mockNetworkWrite(Network network, String... fileNames) {
        doAnswer(invocation -> {
            DirectoryDataSource dataSource = invocation.getArgument(2);
            for (String fileName : fileNames) {
                try (var output = dataSource.newOutputStream(fileName, false)) {
                    output.write(fileName.getBytes(StandardCharsets.UTF_8));
                }
            }
            return null;
        }).when(network).write(anyString(), org.mockito.ArgumentMatchers.any(Properties.class), org.mockito.ArgumentMatchers.any(DirectoryDataSource.class));
    }

    private static Set<String> readZipEntryNames(Path zipPath) throws IOException {
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(zipPath))) {
            List<String> fileNames = new ArrayList<>();
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                fileNames.add(entry.getName());
            }
            return Set.copyOf(fileNames);
        }
    }

    private static void deleteTempDirectory(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
