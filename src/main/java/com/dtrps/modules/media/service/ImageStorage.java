package com.dtrps.modules.media.service;

/** Where uploaded images end up. Returns a public https URL for the stored image. */
public interface ImageStorage {
  String store(byte[] content, String contentType, String filename);

  /** Same, into another folder (e.g. avatars); null means the default folder. */
  default String store(byte[] content, String contentType, String filename, String folder) {
    return store(content, contentType, filename);
  }
}
