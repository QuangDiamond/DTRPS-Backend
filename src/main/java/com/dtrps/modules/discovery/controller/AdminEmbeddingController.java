
package com.dtrps.modules.discovery.controller;
import org.springframework.web.bind.annotation.RequestParam;
import com.dtrps.modules.discovery.service.PlaceEmbeddingService;
import com.dtrps.modules.discovery.service.PlaceEmbeddingService.RebuildResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/embeddings")
@PreAuthorize("hasRole('ADMIN')")
public class AdminEmbeddingController {

    private final PlaceEmbeddingService embeddingService;

    public AdminEmbeddingController(
            PlaceEmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

  @PostMapping("/rebuild")
public RebuildResult rebuild(
        @RequestParam(defaultValue = "1") int limit) {

    return embeddingService.rebuild(limit);
}
}
