package com.dtrps.modules.media.service;

/** Where uploaded images end up. Returns a public https URL for the stored image. */
public interface ImageStorage {
  String store(byte[] content, String contentType, String filename);
}
