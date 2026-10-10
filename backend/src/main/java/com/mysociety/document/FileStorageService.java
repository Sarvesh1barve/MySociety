package com.mysociety.document;

import java.io.IOException;
import java.nio.file.Path;

public interface FileStorageService {
  String store(byte[] bytes, String extension) throws IOException;

  Path resolve(String key);

  void discard(String key) throws IOException;
}
