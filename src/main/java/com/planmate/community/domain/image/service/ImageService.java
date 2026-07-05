package com.planmate.community.domain.image.service;

import com.planmate.community.common.exception.CommunityException;
import com.planmate.community.common.exception.ErrorCode;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class ImageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp");

    private final MinioClient minioClient;
    private final String bucket;
    private final String publicUrl;

    public ImageService(
            MinioClient minioClient,
            @Value("${minio.bucket}") String bucket,
            @Value("${minio.public-url}") String publicUrl
    ) {
        this.minioClient = minioClient;
        this.bucket = bucket;
        this.publicUrl = publicUrl;
    }

    /** 버킷이 없으면 생성하고 익명 읽기 정책을 설정한다 (URL 직접 서빙). */
    @PostConstruct
    void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                        .bucket(bucket)
                        .config("""
                                {
                                  "Version": "2012-10-17",
                                  "Statement": [{
                                    "Effect": "Allow",
                                    "Principal": {"AWS": ["*"]},
                                    "Action": ["s3:GetObject"],
                                    "Resource": ["arn:aws:s3:::%s/*"]
                                  }]
                                }
                                """.formatted(bucket))
                        .build());
                log.info("MinIO 버킷 생성 및 공개 읽기 정책 설정: {}", bucket);
            }
        } catch (Exception e) {
            // 기동은 막지 않는다 — 업로드 시점에 실패로 드러남
            log.warn("MinIO 버킷 확인/생성 실패: {}", e.getMessage());
        }
    }

    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CommunityException(ErrorCode.INVALID_INPUT, "업로드할 파일이 없습니다.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new CommunityException(ErrorCode.INVALID_INPUT, "지원하지 않는 이미지 형식입니다: " + contentType);
        }

        String objectKey = buildObjectKey(file.getOriginalFilename());
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            log.error("이미지 업로드 실패 (key={}): {}", objectKey, e.getMessage());
            throw new CommunityException(ErrorCode.INTERNAL_SERVER_ERROR, "이미지 업로드에 실패했습니다.");
        }

        return publicUrl + "/" + bucket + "/" + objectKey;
    }

    private String buildObjectKey(String originalFilename) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase();
        }
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return datePath + "/" + UUID.randomUUID() + extension;
    }
}
