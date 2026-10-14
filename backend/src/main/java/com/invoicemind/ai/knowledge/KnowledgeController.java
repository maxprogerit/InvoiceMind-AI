package com.invoicemind.ai.knowledge;

import com.invoicemind.ai.knowledge.KnowledgeDtos.AnswerDto;
import com.invoicemind.ai.knowledge.KnowledgeDtos.AskRequest;
import com.invoicemind.ai.knowledge.KnowledgeDtos.CreateTextRequest;
import com.invoicemind.ai.knowledge.KnowledgeDtos.DocumentDto;
import com.invoicemind.ai.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Knowledge")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class KnowledgeController {
  private final KnowledgeService service;

  @GetMapping("/knowledge/documents")
  @PreAuthorize("hasAnyRole('APPROVER','ADMIN')")
  public List<DocumentDto> list() {
    return service.list(CurrentUser.get());
  }

  @PostMapping("/knowledge/documents")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  public DocumentDto addText(@Valid @RequestBody CreateTextRequest request) {
    return service.addText(request.title(), null, request.content(), CurrentUser.get());
  }

  @PostMapping(value = "/knowledge/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  public DocumentDto upload(@RequestPart("file") MultipartFile file, @RequestParam(required = false) String title) {
    return service.addFile(file, title, CurrentUser.get());
  }

  @DeleteMapping("/knowledge/documents/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id, CurrentUser.get());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/knowledge/ask")
  @PreAuthorize("hasAnyRole('APPROVER','ADMIN')")
  public AnswerDto ask(@Valid @RequestBody AskRequest request) {
    return service.ask(request.question(), CurrentUser.get());
  }

  /** Why was this invoice flagged? Available to everyone who can see the invoice. */
  @PostMapping("/invoices/{id}/explain")
  public AnswerDto explain(@PathVariable UUID id, @Valid @RequestBody(required = false) AskRequest request) {
    return service.explainInvoice(id, request == null ? null : request.question(), CurrentUser.get());
  }
}
