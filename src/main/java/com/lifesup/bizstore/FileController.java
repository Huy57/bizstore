package com.lifesup.bizstore;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/files")
public class FileController {
    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) throws IOException {
        fileService.uploadFile(file);
        return ResponseEntity.ok("File uploaded successfully");
    }

    @GetMapping
    public List<String> listFiles() {
        return fileService.listFiles();
    }

    @GetMapping("/download/{filename}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String filename) {
        byte[] data = fileService.downloadFile(filename);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data);
    }

    // API lấy danh sách bucket
    @GetMapping("/buckets")
    public List<String> listBuckets() {
        return fileService.listBuckets();
    }

    // API tạo mới bucket
    @PostMapping("/buckets")
    public ResponseEntity<String> createBucket(@RequestParam String bucketName) {
        fileService.createBucket(bucketName);
        return ResponseEntity.ok("Bucket created successfully");
    }

    // API xoá bucket
    @DeleteMapping("/buckets/{bucketName}")
    public ResponseEntity<String> deleteBucket(@PathVariable String bucketName) {
        fileService.deleteBucket(bucketName);
        return ResponseEntity.ok("Bucket deleted successfully");
    }

    // API kiểm tra access-key và secret-key
    @GetMapping("/check-credentials")
    public ResponseEntity<String> checkCredentials() {
        try {
            fileService.listBuckets();
            return ResponseEntity.ok("Credentials are valid");
        } catch (Exception e) {
            return ResponseEntity.status(401).body("Invalid credentials: " + e.getMessage());
        }
    }

    // API tạo presigned URL download tạm thời
    @GetMapping("/file")
    public ResponseEntity<String> getPresignedUrl(@RequestParam String filename, @RequestParam(defaultValue = "300") int expireSeconds) {
        String url = fileService.generatePresignedUrl(filename, expireSeconds);
        return ResponseEntity.ok(url);
    }

    // API xóa file
    @DeleteMapping("/files/{filename}")
    public ResponseEntity<String> deleteFile(@PathVariable String filename) {
        fileService.deleteFile(filename);
        return ResponseEntity.ok("File deleted successfully");
    }

    // API đổi tên file
    @PostMapping("/files/rename")
    public ResponseEntity<String> renameFile(@RequestParam String oldName, @RequestParam String newName) {
        fileService.renameFile(oldName, newName);
        return ResponseEntity.ok("File renamed successfully");
    }
} 