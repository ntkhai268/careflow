package com.careflow.patient.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.common.exception.ResourceNotFoundException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class FileStorageService {

    @Value("${file.upload-dir:uploads/health-records}")
    private String uploadDir;

    private Path fileStorageLocation;
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png", "pdf", "doc", "docx");
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    @PostConstruct
    public void init() {
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    public String store(MultipartFile file) {
        String originalFilename = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));

        // Validate size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(400, "File size exceeds 10MB limit");
        }

        // Validate extension
        String extension = getFileExtension(originalFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(400, "File type not allowed: " + extension + ". Allowed: " + ALLOWED_EXTENSIONS);
        }

        try {
            String newFilename = UUID.randomUUID().toString() + "." + extension;
            Path targetLocation = this.fileStorageLocation.resolve(newFilename);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            return newFilename;
        } catch (IOException ex) {
            throw new BusinessException(500, "Could not store file " + originalFilename);
        }
    }

    public Resource load(String storedPath) {
        try {
            Path filePath = this.fileStorageLocation.resolve(storedPath).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists()) {
                return resource;
            } else {
                throw new ResourceNotFoundException("HealthRecordFile", "storedPath", storedPath);
            }
        } catch (MalformedURLException ex) {
            throw new ResourceNotFoundException("HealthRecordFile", "storedPath", storedPath);
        }
    }

    public void delete(String storedPath) {
        try {
            Path filePath = this.fileStorageLocation.resolve(storedPath).normalize();
            Files.deleteIfExists(filePath);
        } catch (IOException ex) {
            throw new BusinessException(500, "Could not delete file " + storedPath);
        }
    }

    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf(".");
        if (dotIndex > 0 && dotIndex < filename.length() - 1) {
            return filename.substring(dotIndex + 1);
        }
        return "";
    }
}
