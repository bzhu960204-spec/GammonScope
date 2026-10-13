package com.gammonscope.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.gammonscope.dto.MatchMetadata;
import com.gammonscope.dto.MatchSummary;
import com.gammonscope.model.BgMatch;
import com.gammonscope.parser.ImportException;
import com.gammonscope.service.ExportService;
import com.gammonscope.service.ImportService;
import com.gammonscope.service.MatchService;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final ImportService importService;
    private final MatchService matchService;
    private final ExportService exportService;

    public MatchController(ImportService importService, MatchService matchService, ExportService exportService) {
        this.importService = importService;
        this.matchService = matchService;
        this.exportService = exportService;
    }

    @PostMapping("/import")
    public ResponseEntity<?> importMatch(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "The uploaded file is empty."));
        }
        BgMatch saved = importService.importFile(file.getOriginalFilename(), file.getBytes());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", saved.getId(),
                "games", saved.getGames().size(),
                "sourceFormat", saved.getSourceFormat()));
    }

    @GetMapping
    public List<MatchSummary> list() {
        return matchService.listSummaries();
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody MatchMetadata meta) {
        BgMatch saved = matchService.createMatch(meta);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", saved.getId(),
                "games", saved.getGames().size(),
                "sourceFormat", saved.getSourceFormat()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BgMatch> get(@PathVariable Long id) {
        return matchService.getMatch(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> updateMetadata(@PathVariable Long id, @RequestBody MatchMetadata meta) {
        return matchService.updateMetadata(id, meta)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return matchService.deleteMatch(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @PatchMapping("/{id}/games/{gameIndex}/moves/{seq}/comment")
    public ResponseEntity<Void> updateComment(
            @PathVariable Long id,
            @PathVariable int gameIndex,
            @PathVariable int seq,
            @RequestBody Map<String, String> body) {
        return matchService.updateMoveComment(id, gameIndex, seq, body.get("comment"))
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @GetMapping("/{id}/export/mat")
    public ResponseEntity<String> exportMat(@PathVariable Long id) {
        return matchService.getMatch(id)
                .map(m -> textAttachment(exportService.toMat(m), fileName(m, "mat")))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/export/sgf")
    public ResponseEntity<String> exportSgf(@PathVariable Long id) {
        return matchService.getMatch(id)
                .map(m -> textAttachment(exportService.toSgf(m), fileName(m, "sgf")))
                .orElse(ResponseEntity.notFound().build());
    }

    private ResponseEntity<String> textAttachment(String body, String fileName) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8))
                .body(body);
    }

    private String fileName(BgMatch match, String ext) {
        String base = (safe(match.getPlayer0()) + "-vs-" + safe(match.getPlayer1()));
        return base + "." + ext;
    }

    private String safe(String s) {
        return s == null ? "player" : s.replaceAll("[^A-Za-z0-9_-]+", "_");
    }

    @ExceptionHandler(ImportException.class)
    public ResponseEntity<Map<String, String>> handleImport(ImportException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}
