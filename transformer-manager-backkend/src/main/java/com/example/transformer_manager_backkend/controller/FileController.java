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
@RequestMapping("/api/files")
@CrossOrigin(origins = "http://localhost:3000")
public class FileController {

    @GetMapping("/uploads/{filename:.+}")
    public ResponseEntity<Resource> serveUploadedFile(@PathVariable String filename) {
        try {
            Path file;

            // Handle analysis subdirectory
            if (filename.startsWith("analysis/")) {
                // Security check for analysis files
                String analysisFile = filename.substring("analysis/".length());
                if (analysisFile.contains("..") || analysisFile.contains("\\")) {
                    return ResponseEntity.badRequest().build();
                }
                file = Paths.get("uploads", "analysis").resolve(analysisFile);
            } else {
                // Security check - prevent directory traversal for regular uploads
                if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                    return ResponseEntity.badRequest().build();
                }
                file = Paths.get("uploads").resolve(filename);
            }

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

    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> serveAnalysisFileAlternate(@PathVariable String filename) {
        try {
            // Handle analysis subdirectory with leading slash format
            if (filename.startsWith("analysis/")) {
                // Security check for analysis files
                String analysisFile = filename.substring("analysis/".length());
                if (analysisFile.contains("..") || analysisFile.contains("\\")) {
                    return ResponseEntity.badRequest().build();
                }
                Path file = Paths.get("uploads", "analysis").resolve(analysisFile);
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
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/analysis/{subpath}/{filename:.+}")
    public ResponseEntity<Resource> serveAnalysisFile(@PathVariable String subpath, @PathVariable String filename) {
        try {
            // Security check - prevent directory traversal
            if (filename.contains("..") || subpath.contains("..") ||
                    filename.contains("/") || filename.contains("\\") ||
                    subpath.contains("/") || subpath.contains("\\")) {
                return ResponseEntity.badRequest().build();
            }

            Path file = Paths.get("uploads", "analysis", subpath).resolve(filename);
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