package com.dtrps.modules.media.service;

import com.dtrps.exception.ApiException;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Accepts only real jpg / png / webp files (checked by their bytes, not just the name). */
@Service
public class ImageUploadService {
  public static final long MAX_BYTES = 5L * 1024 * 1024;
  static final String EMPTY = "Vui lòng chọn ảnh";
  static final String TOO_LARGE = "Ảnh tối đa 5MB";
  static final String UNSUPPORTED = "Chỉ nhận ảnh jpg, png hoặc webp";

  private static final Map<String, String> EXTENSIONS =
      Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");

  private final ImageStorage storage;

  public ImageUploadService(ImageStorage storage) {
    this.storage = storage;
  }

  public String upload(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, EMPTY);
    }
    if (file.getSize() > MAX_BYTES) {
      throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, TOO_LARGE);
    }
    byte[] content;
    try {
      content = file.getBytes();
    } catch (IOException ex) {
      throw new ApiException(HttpStatus.BAD_REQUEST, EMPTY);
    }
    String contentType = detect(content);
    if (contentType == null) {
      throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, UNSUPPORTED);
    }
    return storage.store(content, contentType, "upload." + EXTENSIONS.get(contentType));
  }

  /** Content type from the file signature; null when it is not jpg / png / webp. */
  static String detect(byte[] bytes) {
    if (startsWith(bytes, 0, 0xFF, 0xD8, 0xFF)) {
      return "image/jpeg";
    }
    if (startsWith(bytes, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
      return "image/png";
    }
    if (startsWith(bytes, 0, 'R', 'I', 'F', 'F') && startsWith(bytes, 8, 'W', 'E', 'B', 'P')) {
      return "image/webp";
    }
    return null;
  }

  private static boolean startsWith(byte[] bytes, int offset, int... signature) {
    if (bytes.length < offset + signature.length) {
      return false;
    }
    for (int i = 0; i < signature.length; i++) {
      if ((bytes[offset + i] & 0xFF) != signature[i]) {
        return false;
      }
    }
    return true;
  }
}
