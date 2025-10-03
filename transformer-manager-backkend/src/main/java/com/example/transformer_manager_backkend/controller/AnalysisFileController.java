package com.example.transformer_manager_backkend.controller;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/analysis")
@CrossOrigin(origins = "http://localhost:3000")
public class AnalysisFileController {

    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> serveAnalysisFile(@PathVariable String filename) {
        try {
            // Security check - prevent directory traversal
            if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                return ResponseEntity.badRequest().build();
            }

            Path file = Paths.get("uploads", "analysis").resolve(filename);
            Resource resource = new UrlResource(file.toUri());

            if (resource.exists() && resource.isReadable()) {
                MediaType mediaType = getMediaTypeForFile(filename);

                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                        .contentType(mediaType)
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    private MediaType getMediaTypeForFile(String filename) {
        String lowerName = filename.toLowerCase();
        if (lowerName.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        } else if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        } else if (lowerName.endsWith(".json")) {
            return MediaType.APPLICATION_JSON;
        } else {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}