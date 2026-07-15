package com.career.platform.report.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Renders already escaped Freemarker HTML with the LGPL OpenHTMLtoPDF/PDFBox stack. */
@Component
public class PdfReportRenderer {

    private static final Logger log = LoggerFactory.getLogger(PdfReportRenderer.class);
    private static final String CHINESE_FONT_FAMILY = "Noto Sans SC";
    private static final int CHINESE_FONT_WEIGHT = 400;
    private final Path chineseFont;
    private final boolean requireChineseFont;

    public PdfReportRenderer(@Value("${app.report.font-file:${REPORT_FONT_FILE:/usr/share/fonts/noto/NotoSansSC.ttf}}")
                             String chineseFont,
                             @Value("${app.report.require-chinese-font:${REPORT_REQUIRE_CHINESE_FONT:false}}")
                             boolean requireChineseFont) {
        this.chineseFont = Paths.get(chineseFont).toAbsolutePath().normalize();
        this.requireChineseFont = requireChineseFont;
    }

    public byte[] render(String html) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            // Templates do not permit caller-supplied markup or external URLs. A null base URI
            // also prevents relative resources from escaping the application's controlled files.
            builder.withHtmlContent(html, null);
            builder.useExternalResourceAccessControl(
                    (uri, type) -> false, ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI);
            if (Files.isRegularFile(chineseFont)) {
                // The pinned Google Fonts artifact has TrueType outlines, so PDFBox can subset it.
                builder.useFont(chineseFont.toFile(), CHINESE_FONT_FAMILY, CHINESE_FONT_WEIGHT,
                        FontStyle.NORMAL, true);
            } else if (requireChineseFont) {
                throw new IOException("未找到受配置保护的中文字体: " + chineseFont);
            } else {
                log.warn("未找到中文字体 {}；仅非生产开发环境允许继续渲染", chineseFont);
            }
            builder.toStream(output);
            builder.run();
            return output.toByteArray();
        } catch (Exception exception) {
            if (exception instanceof IOException) {
                throw (IOException) exception;
            }
            throw new IOException("PDF 渲染失败", exception);
        }
    }
}
