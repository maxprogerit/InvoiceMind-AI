package com.invoicemind.ai.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
  String store(MultipartFile file);
}
