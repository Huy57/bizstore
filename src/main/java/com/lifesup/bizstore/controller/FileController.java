package com.lifesup.bizstore.controller;

import com.lifesup.bizstore.util.CommonResponse;
import com.lifesup.bizstore.service.FileService;
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
    public ResponseEntity<CommonResponse<String>> uploadFile(@RequestParam("file") MultipartFile file) throws IOException {
        fileService.uploadFile(file);
        return ResponseEntity.ok(new CommonResponse<>("200", "File uploaded successfully", null));
    }

    @GetMapping
    public ResponseEntity<CommonResponse<List<String>>> listFiles() {
        List<String> files = fileService.listFiles();
        return ResponseEntity.ok(new CommonResponse<>("200", "Success", files));
    }

    @GetMapping("/download/{filename}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String filename) {
        byte[] data = fileService.downloadFile(filename);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data);
    }

    // API tạo presigned URL download tạm thời
    @GetMapping("/presign")
    public ResponseEntity<CommonResponse<String>> getPresignedUrl(@RequestParam String filename, @RequestParam(defaultValue = "300") int expireSeconds) {
        String url = fileService.generatePresignedUrl(filename, expireSeconds);
        return ResponseEntity.ok(new CommonResponse<>("200", "Success", url));
    }

    // API xóa file
    @DeleteMapping("/{filename}")
    public ResponseEntity<CommonResponse<String>> deleteFile(@PathVariable String filename) {
        fileService.deleteFile(filename);
        return ResponseEntity.ok(new CommonResponse<>("200", "File deleted successfully", null));
    }

    // API đổi tên file
    @PostMapping("/rename")
    public ResponseEntity<CommonResponse<String>> renameFile(@RequestParam String oldName, @RequestParam String newName) {
        fileService.renameFile(oldName, newName);
        return ResponseEntity.ok(new CommonResponse<>("200", "File renamed successfully", null));
    }

    // API lấy danh sách bucket
    @GetMapping("/buckets")
    public ResponseEntity<CommonResponse<List<String>>> listBuckets() {
        List<String> buckets = fileService.listBuckets();
        return ResponseEntity.ok(new CommonResponse<>("200", "Success", buckets));
    }

    // API tạo mới bucket
    @PostMapping("/buckets")
    public ResponseEntity<CommonResponse<String>> createBucket(@RequestParam String bucketName) {
        fileService.createBucket(bucketName);
        return ResponseEntity.ok(new CommonResponse<>("200", "Bucket created successfully", null));
    }

    // API xoá bucket
    @DeleteMapping("/buckets/{bucketName}")
    public ResponseEntity<CommonResponse<String>> deleteBucket(@PathVariable String bucketName) {
        fileService.deleteBucket(bucketName);
        return ResponseEntity.ok(new CommonResponse<>("200", "Bucket deleted successfully", null));
    }

    // API kiểm tra access-key và secret-key
    @GetMapping("/check-credentials")
    public ResponseEntity<CommonResponse<String>> checkCredentials() {
        try {
            fileService.listBuckets();
            return ResponseEntity.ok(new CommonResponse<>("200", "Credentials are valid", null));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(new CommonResponse<>("401", "Invalid credentials: " + e.getMessage(), null));
        }
    }
} 