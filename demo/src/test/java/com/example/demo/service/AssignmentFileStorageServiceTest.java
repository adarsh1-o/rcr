package com.example.demo.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class AssignmentFileStorageServiceTest {
    @TempDir
    Path storageRoot;

    private AssignmentFileStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new AssignmentFileStorageService(storageRoot, 10 * 1024 * 1024);
    }

    @Test
    void acceptsSupportedFilesAndStoresThemUnderGeneratedNames() throws Exception {
        var pdf = storage.store(file("C:\\fakepath\\notes.PDF", "%PDF-1.7\nbody".getBytes()));
        var text = storage.store(file("notes.txt", "plain text".getBytes()));
        var docx = storage.store(file("notes.docx", officePackage("word/document.xml")));
        var pptx = storage.store(file("slides.pptx", officePackage("ppt/presentation.xml")));

        assertEquals("notes.PDF", pdf.originalFilename());
        assertEquals("application/pdf", pdf.contentType());
        assertEquals("text/plain", text.contentType());
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx.contentType());
        assertEquals("application/vnd.openxmlformats-officedocument.presentationml.presentation", pptx.contentType());
        Path storedPath = storage.pathForDownload(pdf.storageId());
        assertTrue(storedPath.startsWith(storageRoot.toRealPath()));
        assertArrayEquals("%PDF-1.7\nbody".getBytes(), Files.readAllBytes(storedPath));
        assertNotEquals(pdf.originalFilename(), pdf.storageId());
    }

    @Test
    void rejectsUnsupportedExtensionsAndMismatchedOrInvalidContent() {
        assertThrows(IllegalArgumentException.class,
                () -> storage.store(file("payload.exe", new byte[]{'M', 'Z'})));
        assertThrows(IllegalArgumentException.class,
                () -> storage.store(file("fake.pdf", "not a pdf".getBytes())));
        assertThrows(IllegalArgumentException.class,
                () -> storage.store(file("fake.docx", new byte[]{'P', 'K', 3, 4, 1, 2})));
        assertThrows(IllegalArgumentException.class,
                () -> storage.store(file("invalid.txt", new byte[]{(byte) 0xC3, 0x28})));
    }

    @Test
    void rejectsOversizedFilesAndStoragePathTraversal() {
        AssignmentFileStorageService smallStorage = new AssignmentFileStorageService(storageRoot, 4);
        assertThrows(IllegalArgumentException.class,
                () -> smallStorage.store(file("large.txt", "12345".getBytes())));
        assertThrows(IllegalArgumentException.class, () -> storage.pathForDownload("../outside"));
        assertThrows(IllegalArgumentException.class, () -> storage.pathForDownload("C:\\outside"));
    }

    private MockMultipartFile file(String filename, byte[] content) {
        return new MockMultipartFile("file", filename, "client/type-is-ignored", content);
    }

    private byte[] officePackage(String requiredEntry) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(requiredEntry));
            zip.write("<document/>".getBytes());
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
