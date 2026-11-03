package com.dtrps.modules.media.controller;

import com.dtrps.modules.media.service.ImageUploadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Uploads one image and returns its URL, which the caller then puts in a place's images. */
@RestController
@RequestMapping("/api/uploads")
@PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'ADMIN')")
public class ImageUploadController {
  public record ImageUploadResponse(String url) {}

  private final ImageUploadService uploadService;

  public ImageUploadController(ImageUploadService uploadService) {
    this.uploadService = uploadService;
  }

  @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public ImageUploadResponse upload(@RequestParam("file") MultipartFile file) {
    return new ImageUploadResponse(uploadService.upload(file));
  }
}
