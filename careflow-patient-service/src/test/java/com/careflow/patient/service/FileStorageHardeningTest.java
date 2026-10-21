package com.careflow.patient.service;

import com.careflow.common.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileStorageHardeningTest {
    @TempDir Path directory;
    final UploadScanner scanner=mock(UploadScanner.class);
    FileStorageService storage;
    @BeforeEach void init() {
        storage=new FileStorageService();ReflectionTestUtils.setField(storage,"uploadDir",directory.toString());
        ReflectionTestUtils.setField(storage,"scanner",scanner);storage.init();
    }
    @Test void rejectsRenamedExecutableBeforeScanningOrSaving() {
        var file=new MockMultipartFile("file","report.pdf","application/pdf","executable".getBytes());
        assertThatThrownBy(()->storage.store(file)).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(415));
        verifyNoInteractions(scanner);assertThat(directory.toFile().list()).isEmpty();
    }
    @Test void scannerFailureNeverPublishesUpload() {
        var file=new MockMultipartFile("file","report.pdf","application/pdf","%PDF-safe-fixture".getBytes());
        doThrow(new BusinessException(422,"unsafe")).when(scanner).scan(any());
        assertThatThrownBy(()->storage.store(file)).isInstanceOf(BusinessException.class);
        assertThat(directory.toFile().list()).isEmpty();
    }
    @Test void rejectsTraversalAbsolutePathsAndSymlinks() throws Exception {
        assertThatThrownBy(()->storage.load("../private.pdf")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->storage.delete("/tmp/private.pdf")).isInstanceOf(BusinessException.class);
        String name="11111111-1111-1111-1111-111111111111.pdf";
        Files.createSymbolicLink(directory.resolve(name),directory.getParent());
        assertThatThrownBy(()->storage.load(name)).isInstanceOf(BusinessException.class);
    }
    @Test void productionScannerCannotSilentlySkipMissingConfiguration() {
        assertThatThrownBy(()->new UploadScanner("",3310,true).scan(new byte[]{1}))
                .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(503));
    }
    @Test void scannedPdfIsStoredUnderRandomContainedName() {
        String name=storage.store(new MockMultipartFile("file","report.pdf","text/html","%PDF-safe-fixture".getBytes()));
        assertThat(name).matches("[a-f0-9-]{36}\\.pdf");assertThat(storage.load(name).exists()).isTrue();verify(scanner).scan(any());
    }
}
