package com.loanflow.document.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Component
public class LocalDocumentStorageProvider implements DocumentStorageProvider {

    private final Path storageDirectory;

    public LocalDocumentStorageProvider(@Value("${loanflow.storage.local.dir:storage/documents}") String storageDir) throws IOException {
        this.storageDirectory = Paths.get(storageDir).toAbsolutePath().normalize();
        Files.createDirectories(this.storageDirectory);
    }

    @Override
    public String storeFile(MultipartFile file, String objectKey) throws IOException {
        Path targetLocation = this.storageDirectory.resolve(objectKey).normalize();
        if (!targetLocation.getParent().equals(this.storageDirectory)) {
            throw new IOException("Cannot store file outside current directory");
        }
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
        return objectKey;
    }

    @Override
    public InputStream getFile(String objectKey) throws IOException {
        Path filePath = this.storageDirectory.resolve(objectKey).normalize();
        if (!filePath.getParent().equals(this.storageDirectory)) {
            throw new IOException("Cannot access file outside current directory");
        }
        File file = filePath.toFile();
        if (!file.exists()) {
            throw new IOException("File not found");
        }
        return new FileInputStream(file);
    }

    @Override
    public void deleteFile(String objectKey) {
        try {
            Path filePath = this.storageDirectory.resolve(objectKey).normalize();
            if (filePath.getParent().equals(this.storageDirectory)) {
                Files.deleteIfExists(filePath);
            }
        } catch (IOException e) {
            // Ignore in dev
        }
    }
}
