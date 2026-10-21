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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class FileStorageService {

    @Value("${file.upload-dir:uploads/health-records}")
    private String uploadDir;

    private Path fileStorageLocation;
    @org.springframework.beans.factory.annotation.Autowired private UploadScanner scanner;
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png", "pdf");
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
        if(file==null || file.isEmpty() || !StringUtils.hasText(file.getOriginalFilename()))
            throw new BusinessException(400,"Tệp phải có tên và nội dung.");
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());

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
            byte[] bytes;
            try(var input=file.getInputStream()) { bytes=input.readNBytes((int)MAX_FILE_SIZE+1); }
            if(bytes.length>MAX_FILE_SIZE) throw new BusinessException(413,"Tệp vượt giới hạn 10MB.");
            validateContent(bytes,extension);
            scanner.scan(bytes);
            if(Files.getFileStore(fileStorageLocation).getUsableSpace()<100L*1024*1024+bytes.length)
                throw new BusinessException(503,"Kho lưu trữ tạm thời không đủ dung lượng.");
            String newFilename = UUID.randomUUID().toString() + "." + extension;
            Path targetLocation = safePath(newFilename);
            Files.write(targetLocation,bytes,java.nio.file.StandardOpenOption.CREATE_NEW);
            return newFilename;
        } catch (IOException ex) {
            throw new BusinessException(500, "Could not store file " + originalFilename);
        }
    }

    public Resource load(String storedPath) {
        try {
            Path filePath = safePath(storedPath);
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
            Path filePath = safePath(storedPath);
            Files.deleteIfExists(filePath);
        } catch (IOException ex) {
            throw new BusinessException(500, "Could not delete file " + storedPath);
        }
    }

    private Path safePath(String name) {
        if(name==null || !name.matches("[0-9a-fA-F-]{36}\\.(jpg|jpeg|png|pdf|doc|docx)"))
            throw new BusinessException(400,"Đường dẫn tệp không hợp lệ.");
        Path resolved=fileStorageLocation.resolve(name).normalize();
        if(!resolved.startsWith(fileStorageLocation) || Files.isSymbolicLink(resolved))
            throw new BusinessException(400,"Đường dẫn tệp không hợp lệ.");
        return resolved;
    }
    private void validateContent(byte[] bytes,String extension) throws IOException {
        if("pdf".equals(extension)) {
            if(bytes.length<5 || !new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))
                throw new BusinessException(415,"Nội dung tệp không khớp định dạng PDF.");
            return;
        }
        try(var input=javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
            var readers=javax.imageio.ImageIO.getImageReaders(input);
            if(!readers.hasNext()) throw new BusinessException(415,"Nội dung ảnh không hợp lệ.");
            var reader=readers.next();
            try {
                reader.setInput(input);String format=reader.getFormatName();
                boolean matches="png".equals(extension)?"png".equalsIgnoreCase(format):"JPEG".equalsIgnoreCase(format);
                if(!matches || (long)reader.getWidth(0)*reader.getHeight(0)>20_000_000)
                    throw new BusinessException(415,"Ảnh sai định dạng hoặc vượt giới hạn kích thước.");
            } finally { reader.dispose(); }
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
