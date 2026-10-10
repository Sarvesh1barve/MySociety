package com.mysociety.document;

import com.mysociety.audit.*;
import com.mysociety.common.*;
import com.mysociety.security.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
@Transactional
public class DocumentController {
  private final Store db;
  private final Access access;
  private final Visibility visibility;
  private final AuditService audit;
  private final FileStorageService storage;

  public DocumentController(
      Store db,
      Access access,
      Visibility visibility,
      AuditService audit,
      FileStorageService storage) {
    this.db = db;
    this.access = access;
    this.visibility = visibility;
    this.audit = audit;
    this.storage = storage;
  }

  @GetMapping
  public List<Map<String, Object>> list(@RequestHeader("X-Society-Id") UUID s) {
    access.require(s, "DOCUMENT_READ");
    return db.list(Document.class, s).stream().filter(visibility::document).map(Views::of).toList();
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Map<String, Object> upload(
      @RequestHeader("X-Society-Id") UUID s,
      @RequestParam MultipartFile file,
      @RequestParam(defaultValue = "SOCIETY") String visibility,
      @RequestParam(required = false) UUID previousVersionId)
      throws Exception {
    access.require(s, "DOCUMENT_UPLOAD");
    if (!Set.of("SOCIETY", "COMMITTEE", "PRIVATE").contains(visibility))
      throw ApiException.bad("Invalid document visibility.");
    if ("COMMITTEE".equals(visibility)) access.require(s, "DOCUMENT_COMMITTEE");
    if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024)
      throw ApiException.bad("Choose a file between 1 byte and 10 MB.");
    byte[] bytes = file.getBytes();
    String type, ext;
    if (bytes.length >= 8
        && bytes[0] == (byte) 0x89
        && bytes[1] == 0x50
        && bytes[2] == 0x4e
        && bytes[3] == 0x47
        && bytes[4] == 13
        && bytes[5] == 10
        && bytes[6] == 26
        && bytes[7] == 10) {
      type = "image/png";
      ext = "png";
    } else if (bytes.length >= 3
        && bytes[0] == (byte) 0xff
        && bytes[1] == (byte) 0xd8
        && bytes[2] == (byte) 0xff) {
      type = "image/jpeg";
      ext = "jpg";
    } else if (bytes.length >= 5
        && new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
      type = "application/pdf";
      ext = "pdf";
    } else throw ApiException.bad("Only genuine PDF, PNG and JPEG files are accepted.");
    if (!type.equals(file.getContentType()))
      throw ApiException.bad("File content does not match the declared MIME type.");
    Document d = new Document();
    d.societyId = s;
    d.fileName =
        Optional.ofNullable(file.getOriginalFilename())
            .orElse("document." + ext)
            .replaceAll("[^\\p{L}\\p{N}._ -]", "_");
    if (d.fileName.length() > 180) d.fileName = d.fileName.substring(d.fileName.length() - 180);
    d.visibility = visibility;
    d.contentType = type;
    d.size = bytes.length;
    d.uploadedBy = access.user().id;
    d.revision = 1;
    if (previousVersionId != null) {
      Document p = db.tenant(Document.class, s, previousVersionId);
      this.visibility.require(this.visibility.document(p));
      if (!p.uploadedBy.equals(access.user().id) && !access.can(s, "DOCUMENT_COMMITTEE"))
        throw ApiException.forbidden();
      d.previousVersionId = p.id;
      d.revision = p.revision + 1;
    }
    d.storageKey = storage.store(bytes, ext);
    String key = d.storageKey;
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED)
              try {
                storage.discard(key);
              } catch (Exception ignored) {
              }
          }
        });
    db.save(d);
    audit.log(s, "DOCUMENT_UPLOADED", d, null);
    return Views.of(d);
  }

  @GetMapping("/{id}/download")
  public ResponseEntity<Resource> download(
      @RequestHeader("X-Society-Id") UUID s, @PathVariable UUID id) {
    access.require(s, "DOCUMENT_READ");
    Document d = db.tenant(Document.class, s, id);
    visibility.require(visibility.document(d));
    var path = storage.resolve(d.storageKey);
    if (!Files.isRegularFile(path)) throw ApiException.missing();
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(d.contentType))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(d.fileName, StandardCharsets.UTF_8)
                .build()
                .toString())
        .header("X-Content-Type-Options", "nosniff")
        .body(new FileSystemResource(path));
  }
}
