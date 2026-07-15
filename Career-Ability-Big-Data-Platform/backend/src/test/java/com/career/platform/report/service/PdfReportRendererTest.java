package com.career.platform.report.service;

import com.sun.net.httpserver.HttpServer;
import java.net.URI;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.fontbox.util.autodetect.FontFileFinder;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfReportRendererTest {

    @Test
    void rendersExtractablePdfTextAndAtLeastOnePage(@TempDir Path temporaryDirectory) throws Exception {
        PdfReportRenderer renderer = new PdfReportRenderer(temporaryDirectory.resolve("missing.ttf").toString(), false);
        byte[] pdf = renderer.render("<html><body><h1>Monthly employment report</h1><p>Jobs: 12</p></body></html>");

        try (PDDocument document = PDDocument.load(pdf)) {
            assertTrue(document.getNumberOfPages() >= 1);
            assertTrue(new PDFTextStripper().getText(document).contains("Monthly employment report"));
        }
    }

    @Test
    void rejectsProductionRenderingWithoutTheConfiguredChineseFont(@TempDir Path temporaryDirectory) {
        PdfReportRenderer renderer = new PdfReportRenderer(temporaryDirectory.resolve("missing.ttf").toString(), true);

        assertThrows(IOException.class, () -> renderer.render("<html><body>test</body></html>"));
    }

    @Test
    void subsetsConfiguredTrueTypeFontWhenAvailable() throws Exception {
        Path font = findInstalledTrueTypeFont();
        Assumptions.assumeTrue(font != null, "a host TrueType font is required for this renderer-path test");

        PdfReportRenderer renderer = new PdfReportRenderer(font.toString(), true);
        byte[] pdf = renderer.render("<html><head><style>body { font-family: 'Noto Sans SC'; }</style>"
                + "</head><body><p>TrueType subset probe</p></body></html>");

        try (PDDocument document = PDDocument.load(pdf)) {
            boolean containsSubsetFont = false;
            for (org.apache.pdfbox.cos.COSName name : document.getPage(0).getResources().getFontNames()) {
                PDFont pdfFont = document.getPage(0).getResources().getFont(name);
                if (pdfFont.getName().matches("[A-Z]{6}\\+.+")) {
                    containsSubsetFont = true;
                    break;
                }
            }
            assertTrue(containsSubsetFont, "the configured TrueType font should be embedded as a subset");
        }
    }

    @Test
    void rejectsHttpAndFileExternalResources(@TempDir Path temporaryDirectory) throws Exception {
        Path fileStyle = Files.writeString(temporaryDirectory.resolve("external.css"),
                ".from-file { display: none; }");
        AtomicBoolean httpRequested = new AtomicBoolean();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/external.css", exchange -> {
            httpRequested.set(true);
            byte[] body = ".from-http { display: none; }".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            String html = "<html><head>"
                    + "<link rel=\"stylesheet\" href=\"" + fileStyle.toUri() + "\" />"
                    + "<link rel=\"stylesheet\" href=\"http://127.0.0.1:" + server.getAddress().getPort()
                    + "/external.css\" />"
                    + "</head><body><p class=\"from-file\">file resource blocked</p>"
                    + "<p class=\"from-http\">http resource blocked</p></body></html>";
            PdfReportRenderer renderer = new PdfReportRenderer(temporaryDirectory.resolve("missing.ttf").toString(), false);

            byte[] pdf = renderer.render(html);

            try (PDDocument document = PDDocument.load(pdf)) {
                String text = new PDFTextStripper().getText(document);
                assertTrue(text.contains("file resource blocked"));
                assertTrue(text.contains("http resource blocked"));
            }
            assertFalse(httpRequested.get());
        } finally {
            server.stop(0);
        }
    }

    private Path findInstalledTrueTypeFont() {
        String testFont = System.getProperty("report.test.font-file");
        if (testFont != null && !testFont.isBlank()) {
            return Path.of(testFont);
        }
        for (URI fontUri : new FontFileFinder().find()) {
            Path font = Path.of(fontUri);
            String name = font.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.endsWith(".ttf") && Files.isRegularFile(font)) {
                return font;
            }
        }
        return null;
    }
}
