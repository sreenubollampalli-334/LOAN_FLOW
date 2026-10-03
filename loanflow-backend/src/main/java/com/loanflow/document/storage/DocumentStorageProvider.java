package com.loanflow.document.storage;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;

public interface DocumentStorageProvider {
    String storeFile(MultipartFile file, String objectKey) throws IOException;
    InputStream getFile(String objectKey) throws IOException;
    void deleteFile(String objectKey);
}
