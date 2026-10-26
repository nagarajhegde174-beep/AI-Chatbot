package com.nexaai.document.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final Path rootStorageDir;

    public StorageService(@Value("${nexa.storage.path:data/documents}") String storagePath) {
        this.rootStorageDir = Paths.get(storagePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootStorageDir);
            log.info("StorageService initialized with storage path: {}", this.rootStorageDir);
        } catch (IOException e) {
            log.error("Failed to initialize storage directory: {}", e.getMessage());
        }
    }

    public StorageResult store(MultipartFile file) throws IOException {
        String serverFileName = UUID.randomUUID() + ".bin";
        Path targetPath = rootStorageDir.resolve(serverFileName).normalize();

        // Enforce storage stays strictly inside the configured rootStorageDir
        if (!targetPath.startsWith(rootStorageDir)) {
            throw new SecurityException("Storage path traversal attempted: " + targetPath);
        }

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }

        long bytesWritten = 0;
        try (InputStream in = file.getInputStream();
             DigestInputStream dis = new DigestInputStream(in, digest);
             FileOutputStream fos = new FileOutputStream(targetPath.toFile())) {

            byte[] buffer = new byte[8192];
            int read;
            while ((read = dis.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
                bytesWritten += read;
            }
        }

        String checksum = HexFormat.of().formatHex(digest.digest());
        return new StorageResult(targetPath.toString(), checksum, bytesWritten);
    }

    public Resource loadAsResource(String storagePath) throws IOException {
        Path path = Paths.get(storagePath).toAbsolutePath().normalize();
        if (!path.startsWith(rootStorageDir) || !Files.exists(path)) {
            throw new IOException("File not found at storage path: " + storagePath);
        }
        return new InputStreamResource(new FileInputStream(path.toFile()));
    }

    public File getFile(String storagePath) {
        Path path = Paths.get(storagePath).toAbsolutePath().normalize();
        return path.toFile();
    }

    public void delete(String storagePath) {
        try {
            Path path = Paths.get(storagePath).toAbsolutePath().normalize();
            if (path.startsWith(rootStorageDir) && Files.exists(path)) {
                Files.delete(path);
                log.info("Deleted stored document file at: {}", storagePath);
            }
        } catch (Exception e) {
            log.warn("Failed to delete stored file {}: {}", storagePath, e.getMessage());
        }
    }

    public record StorageResult(
            String storagePath,
            String checksumSha256,
            long storedBytes
    ) {}
}
