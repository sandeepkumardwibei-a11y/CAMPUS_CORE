package com.campuscore.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileStorageServiceTest {

    private FileStorageService fileStorageService;

    @TempDir
    Path tempUploadsDir;

    @BeforeEach
    void setUp() {
        fileStorageService = new FileStorageService();
        // Point the 'root' path to our temporary directory so tests don't touch the actual disk
        ReflectionTestUtils.setField(fileStorageService, "root", tempUploadsDir.toAbsolutePath().normalize());
    }

    // ─────────────────────────────────────────────────────────
    // 1. STORE FILE TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void store_Success() {
        MockMultipartFile mockFile = new MockMultipartFile(
                "file",
                "receipt.pdf",
                "application/pdf",
                "Dummy PDF Content".getBytes()
        );

        String relativePath = fileStorageService.store("fee-proofs", "101", mockFile);

        assertNotNull(relativePath);
        assertTrue(relativePath.startsWith("fee-proofs/101/"));
        assertTrue(relativePath.endsWith(".pdf"));

        // Verify the file was physically created on disk inside temp directory
        Path actualFilePath = tempUploadsDir.resolve(relativePath);
        assertTrue(Files.exists(actualFilePath));
    }

    @Test
    void store_Success_WithFileWithoutExtension() {
        MockMultipartFile mockFile = new MockMultipartFile(
                "file",
                "rawdocument",
                "text/plain",
                "No extension content".getBytes()
        );

        String relativePath = fileStorageService.store("documents", "user-1", mockFile);

        assertNotNull(relativePath);
        assertTrue(relativePath.startsWith("documents/user-1/"));
        assertFalse(relativePath.contains("."));
    }

    @Test
    void store_ThrowsException_WhenFileIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                fileStorageService.store("fee-proofs", "101", null)
        );
    }

    @Test
    void store_ThrowsException_WhenFileIsEmpty() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.pdf",
                "application/pdf",
                new byte[0]
        );

        assertThrows(IllegalArgumentException.class, () ->
                fileStorageService.store("fee-proofs", "101", emptyFile)
        );
    }

    // ─────────────────────────────────────────────────────────
    // 2. READ FILE TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void read_Success() throws IOException {
        String content = "Hello, CampusCore Storage!";
        Path subDir = tempUploadsDir.resolve("admissions/50");
        Files.createDirectories(subDir);
        Path sampleFile = subDir.resolve("sample.txt");

        // 🎯 FIXED: Changed Files.writeBytes(...) -> Files.write(...)
        Files.write(sampleFile, content.getBytes());

        byte[] readBytes = fileStorageService.read("admissions/50/sample.txt");

        assertNotNull(readBytes);
        assertEquals(content, new String(readBytes));
    }

    @Test
    void read_ThrowsException_WhenFileNotFound() {
        assertThrows(RuntimeException.class, () ->
                fileStorageService.read("nonexistent/folder/file.pdf")
        );
    }

    @Test
    void read_ThrowsException_OnPathTraversalAttempt() {
        // Path traversal attempting to step outside the root upload directory
        assertThrows(SecurityException.class, () ->
                fileStorageService.read("../../etc/passwd")
        );
    }

    // ─────────────────────────────────────────────────────────
    // 3. CONTENT TYPE RESOLUTION TESTS
    // ─────────────────────────────────────────────────────────

    @Test
    void contentTypeFor_ResolvesKnownMimeTypes() {
        assertEquals("image/png", fileStorageService.contentTypeFor("proof.png"));
        assertEquals("image/jpeg", fileStorageService.contentTypeFor("photo.jpg"));
        assertEquals("image/jpeg", fileStorageService.contentTypeFor("photo.jpeg"));
        assertEquals("image/gif", fileStorageService.contentTypeFor("banner.gif"));
        assertEquals("image/webp", fileStorageService.contentTypeFor("avatar.webp"));
        assertEquals("application/pdf", fileStorageService.contentTypeFor("receipt.pdf"));
    }

    @Test
    void contentTypeFor_ReturnsOctetStream_ForUnknownExtensions() {
        assertEquals("application/octet-stream", fileStorageService.contentTypeFor("data.bin"));
        assertEquals("application/octet-stream", fileStorageService.contentTypeFor("document.docx"));
        assertEquals("application/octet-stream", fileStorageService.contentTypeFor("noextensionfile"));
    }
}