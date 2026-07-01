package com.smsweb.sms.controllers;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.File;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.*;

@Controller
@RequestMapping("/universal/download-docs")
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_TEACHER','ROLE_ACCOUNTENT','ROLE_STAFF')")
public class DownloadDocsController {

    private static final Logger log = LoggerFactory.getLogger(DownloadDocsController.class);

    @Value("${app.download.docs.path}")
    private String downloadDocsPath;

    @PostConstruct
    public void init() {
        File folder = new File(downloadDocsPath);
        if (!folder.exists()) {
            boolean created = folder.mkdirs();
            if (created) {
                log.info("Created download_docs folder at: {}", downloadDocsPath);
            } else {
                log.warn("Could not create download_docs folder at: {}. Check permissions.", downloadDocsPath);
            }
        }
    }

    @GetMapping
    public String listDocs(Model model) {
        log.info("Inside listDocs");
        List<Map<String, Object>> files = new ArrayList<>();
        File folder = new File(downloadDocsPath);

        if (folder.exists() && folder.isDirectory()) {
            File[] pdfFiles = folder.listFiles(f -> f.isFile() && f.getName().toLowerCase().endsWith(".pdf"));
            if (pdfFiles != null) {
                Arrays.sort(pdfFiles, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
                int index = 1;
                for (File f : pdfFiles) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("index", index++);
                    entry.put("name", f.getName());
                    entry.put("displayName", stripExtension(f.getName()));
                    entry.put("size", formatSize(f.length()));
                    entry.put("sizeBytes", f.length());
                    files.add(entry);
                }
            }
        }

        model.addAttribute("files", files);
        model.addAttribute("hasFiles", !files.isEmpty());
        model.addAttribute("page", "datatable");
        return "universal/downloadDocs";
    }

    @GetMapping("/file/{filename:.+}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename) {
        log.info("Inside downloadFile");
        // Security: only allow PDF, no path traversal
        if (!filename.toLowerCase().endsWith(".pdf") || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }

        Path filePath = Paths.get(downloadDocsPath).resolve(filename).normalize();
        if (!filePath.startsWith(Paths.get(downloadDocsPath).normalize())) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(resource);
        } catch (MalformedURLException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        DecimalFormat df = new DecimalFormat("#.##");
        if (bytes < 1024 * 1024) return df.format(bytes / 1024.0) + " KB";
        if (bytes < 1024L * 1024 * 1024) return df.format(bytes / (1024.0 * 1024)) + " MB";
        return df.format(bytes / (1024.0 * 1024 * 1024)) + " GB";
    }
}
