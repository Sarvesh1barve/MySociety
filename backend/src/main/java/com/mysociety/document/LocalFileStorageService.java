package com.mysociety.document;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LocalFileStorageService implements FileStorageService {
  private final Path root;

  public LocalFileStorageService(@Value("${app.upload-dir}") String directory) throws IOException {
    root = Path.of(directory).toAbsolutePath().normalize();
    Files.createDirectories(root);
  }

  public String store(byte[] bytes, String extension) throws IOException {
    String key = UUID.randomUUID() + "." + extension;
    Files.write(resolve(key), bytes, StandardOpenOption.CREATE_NEW);
    return key;
  }

  public Path resolve(String key) {
    if (key == null || !key.matches("[0-9a-f-]{36}\\.(pdf|png|jpg)"))
      throw new IllegalArgumentException("Invalid storage key");
    Path path = root.resolve(key).normalize();
    if (!path.startsWith(root)) throw new IllegalArgumentException("Invalid storage path");
    return path;
  }

  public void discard(String key) throws IOException {
    Files.deleteIfExists(resolve(key));
  }
}
