package com.invoicemind.ai.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LocalFileStorageService implements FileStorageService {
  @Override
  public String store(MultipartFile file) {
    return "/storage/" + file.getOriginalFilename();
  }
}
