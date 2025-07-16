package com.lifesup.bizstore.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import java.time.Duration;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class FileService {
    private final S3Client s3Client;

    @Value("${bizfly.s3.bucket}")
    private String bucket;

    // Danh sách extension và MIME type nguy hiểm
    private static final Set<String> DANGEROUS_EXTENSIONS = new HashSet<>(Arrays.asList(
        "exe", "sh", "bat", "msi", "jar", "py", "php", "pl", "cgi", "com", "scr", "dll", "apk", "bin", "cmd", "vbs", "js", "ps1", "wsf", "gadget", "pif", "vb", "cpl", "msc", "msp", "hta", "vbscript", "ws", "jse", "lnk", "app", "command", "run", "msu", "reg"
    ));
    private static final Set<String> DANGEROUS_MIME_TYPES = new HashSet<>(Arrays.asList(
        "application/x-msdownload", "application/x-executable", "application/x-sh", "application/x-bat", "application/x-msdos-program", "application/x-msinstaller", "application/x-java-archive", "application/javascript", "application/x-python-code", "application/x-php", "application/x-perl", "application/x-cgi", "application/x-com", "application/x-scr", "application/x-dosexec", "application/x-msdos-windows", "application/x-msdos-program", "application/x-msdos-batch", "application/x-msdos-windows", "application/x-msdos-program"
    ));
    private static final long MULTIPART_THRESHOLD = 100L * 1024 * 1024; // 100MB
    private static final Logger logger = LoggerFactory.getLogger(FileService.class);

    public FileService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public void uploadFile(MultipartFile file, String remotePath) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new IOException("Tên file không hợp lệ");
        }
        // Loại bỏ file ẩn và đường dẫn độc hại
        if (originalFilename.startsWith(".") || originalFilename.contains("..") || originalFilename.contains("/")) {
            throw new IOException("File ẩn hoặc đường dẫn không hợp lệ");
        }
        // Kiểm tra extension
        String ext = "";
        int dotIdx = originalFilename.lastIndexOf('.');
        if (dotIdx > 0 && dotIdx < originalFilename.length() - 1) {
            ext = originalFilename.substring(dotIdx + 1).toLowerCase();
        }
        if (DANGEROUS_EXTENSIONS.contains(ext)) {
            throw new IOException("Định dạng file không được phép: ." + ext);
        }
        // Kiểm tra MIME type
        String mimeType = file.getContentType();
        if (mimeType != null && DANGEROUS_MIME_TYPES.contains(mimeType)) {
            throw new IOException("MIME type không được phép: " + mimeType);
        }
        // Kiểm tra kích thước file
        long size = file.getSize();
        String key = (remotePath != null && !remotePath.isEmpty()) ? remotePath + "/" + originalFilename : originalFilename;
        if (size > MULTIPART_THRESHOLD) {
            multipartUpload(file, key);
        } else {
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(mimeType)
                    .build(),
                software.amazon.awssdk.core.sync.RequestBody.fromBytes(file.getBytes())
            );
        }
    }

    // Multi-part upload cho file lớn
    private void multipartUpload(MultipartFile file, String key) throws IOException {
        // Sử dụng multipart upload API của S3 SDK
        CreateMultipartUploadRequest createRequest = CreateMultipartUploadRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(file.getContentType())
                .build();
        CreateMultipartUploadResponse createResponse = s3Client.createMultipartUpload(createRequest);
        String uploadId = createResponse.uploadId();
        int partSize = 100 * 1024 * 1024; // 100MB mỗi part
        byte[] buffer = new byte[partSize];
        int partNumber = 1;
        List<CompletedPart> completedParts = new java.util.ArrayList<>();
        long totalSize = file.getSize();
        long uploaded = 0;
        try (java.io.InputStream is = file.getInputStream()) {
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .uploadId(uploadId)
                        .partNumber(partNumber)
                        .contentLength((long) bytesRead)
                        .build();
                software.amazon.awssdk.core.sync.RequestBody partBody = software.amazon.awssdk.core.sync.RequestBody.fromBytes(
                        bytesRead == partSize ? buffer : java.util.Arrays.copyOf(buffer, bytesRead)
                );
                UploadPartResponse uploadPartResponse = s3Client.uploadPart(uploadPartRequest, partBody);
                completedParts.add(CompletedPart.builder()
                        .partNumber(partNumber)
                        .eTag(uploadPartResponse.eTag())
                        .build());
                uploaded += bytesRead;
                double percent = (uploaded * 100.0) / totalSize;
                partNumber++;
            }
        } catch (Exception e) {
            s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .uploadId(uploadId)
                    .build());
            throw new IOException("Lỗi khi upload file lớn: " + e.getMessage(), e);
        }
        CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                .bucket(bucket)
                .key(key)
                .uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
                .build();
        s3Client.completeMultipartUpload(completeRequest);
    }

    public List<String> listFiles(String remotePath) {
        ListObjectsV2Request.Builder builder = ListObjectsV2Request.builder().bucket(bucket);
        if (remotePath != null && !remotePath.isEmpty()) {
            builder.prefix(remotePath + "/");
        }
        ListObjectsV2Response res = s3Client.listObjectsV2(builder.build());
        return res.contents().stream().map(S3Object::key).collect(Collectors.toList());
    }

    public byte[] downloadFile(String key, String remotePath) {
        String fullKey = (remotePath != null && !remotePath.isEmpty()) ? remotePath + "/" + key : key;
        return s3Client.getObject(
            GetObjectRequest.builder().bucket(bucket).key(fullKey).build(),
            software.amazon.awssdk.core.sync.ResponseTransformer.toBytes()
        ).asByteArray();
    }

    public List<String> listBuckets() {
        return s3Client.listBuckets().buckets().stream().map(Bucket::name).collect(Collectors.toList());
    }

    public void createBucket(String bucketName) {
        s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
    }

    public void deleteBucket(String bucketName) {
        s3Client.deleteBucket(DeleteBucketRequest.builder().bucket(bucketName).build());
    }

    public String generatePresignedUrl(String key, int expireSeconds, String remotePath) {
        String fullKey = (remotePath != null && !remotePath.isEmpty()) ? remotePath + "/" + key : key;
        S3Presigner presigner = S3Presigner.builder()
                .endpointOverride(s3Client.serviceClientConfiguration().endpointOverride().get())
                .region(s3Client.serviceClientConfiguration().region())
                .credentialsProvider(s3Client.serviceClientConfiguration().credentialsProvider())
                .build();
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(fullKey)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expireSeconds))
                .getObjectRequest(getObjectRequest)
                .build();
        String url = presigner.presignGetObject(presignRequest).url().toString();
        presigner.close();
        return url;
    }

    public void deleteFile(String key, String remotePath) {
        String fullKey = (remotePath != null && !remotePath.isEmpty()) ? remotePath + "/" + key : key;
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(fullKey).build());
    }

    public void renameFile(String oldKey, String newKey, String remotePath) {
        String oldFullKey = (remotePath != null && !remotePath.isEmpty()) ? remotePath + "/" + oldKey : oldKey;
        String newFullKey = (remotePath != null && !remotePath.isEmpty()) ? remotePath + "/" + newKey : newKey;
        s3Client.copyObject(CopyObjectRequest.builder()
                .sourceBucket(bucket)
                .sourceKey(oldFullKey)
                .destinationBucket(bucket)
                .destinationKey(newFullKey)
                .build());
        deleteFile(oldKey, remotePath);
    }
} 