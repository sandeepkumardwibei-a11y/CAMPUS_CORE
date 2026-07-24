package com.campuscore.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Small helper that saves uploaded files to a local "uploads" directory on disk
 * (outside the jar / classpath, so it survives restarts) and reads them back.
 * Used by the Admissions (document verification) and Fees (payment proof) modules.
 */
@Slf4j
@Service
public class FileStorageService {

    private final Path root = Paths.get("uploads").toAbsolutePath().normalize();

    public String store(String category, String subFolder, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file was provided for upload.");
        }
        try {
            Path dir = root.resolve(category).resolve(subFolder).normalize();
            Files.createDirectories(dir);

            String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
            String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')) : "";
            String storedName = UUID.randomUUID().toString().replace("-", "") + ext;

            Path target = dir.resolve(storedName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            // Return a relative path we can persist in the DB and resolve later
            return category + "/" + subFolder + "/" + storedName;
        } catch (IOException e) {
            log.error("Failed to store uploaded file", e);
            throw new RuntimeException("Failed to store uploaded file: " + e.getMessage(), e);
        }
    }

    public byte[] read(String relativePath) {
        try {
            Path path = root.resolve(relativePath).normalize();
            if (!path.startsWith(root)) {
                throw new SecurityException("Invalid file path");
            }
            return Files.readAllBytes(path);
        } catch (IOException e) {
            log.error("Failed to read stored file: {}", relativePath, e);
            throw new RuntimeException("Failed to read stored file: " + e.getMessage(), e);
        }
    }

    public String contentTypeFor(String relativePath) {
        String lower = relativePath.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".pdf")) return "application/pdf";
        return "application/octet-stream";
    }
}
