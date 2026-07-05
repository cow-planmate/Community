package com.planmate.community.domain.image.controller;

import com.planmate.community.domain.image.dto.ImageUploadResponse;
import com.planmate.community.domain.image.service.ImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Image", description = "커뮤니티 이미지 업로드 API")
@RestController
@RequestMapping("/api/community/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    @Operation(summary = "이미지 업로드", description = "이미지를 업로드하고 공개 URL을 반환합니다. (BlockNote 에디터 uploadFile용)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageUploadResponse> upload(@RequestParam("file") MultipartFile file) {
        String url = imageService.upload(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ImageUploadResponse(url));
    }
}
