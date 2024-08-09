package com.smsweb.sms.controllers;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.ByteArrayOutputStream;

@Controller
public class PdfExportController {

    /*@PostMapping("/exportPdf")
    public void exportToPdf(@RequestParam("htmlContent") String htmlContent, HttpServletResponse response) throws Exception {
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=report.pdf");

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            *//*PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(htmlContent, null);
            builder.toStream(os);
            builder.run();*//*

            response.getOutputStream().write(os.toByteArray());
        }
    }*/
}
