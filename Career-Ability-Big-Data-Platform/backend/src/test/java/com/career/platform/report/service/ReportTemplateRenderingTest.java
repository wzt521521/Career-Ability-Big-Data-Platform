package com.career.platform.report.service;

import freemarker.template.Configuration;
import freemarker.template.Template;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportTemplateRenderingTest {

    @Test
    void monthlyTemplateEscapesTitleAndContainsTheEmptyDataFallback() throws Exception {
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_31);
        configuration.setClassLoaderForTemplateLoading(getClass().getClassLoader(), "templates");
        configuration.setDefaultEncoding("UTF-8");
        Template template = configuration.getTemplate("monthly_report.ftl");
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("reportTitle", "<script>window.bad=true</script>");
        model.put("generateTime", "2026-07-31 12:00:00");
        model.put("timeRangeStart", "2026-07-01");
        model.put("timeRangeEnd", "2026-07-31");
        model.put("emptyData", true);
        model.put("overview", Map.of());

        StringWriter output = new StringWriter();
        template.process(model, output);
        String html = output.toString();

        assertTrue(html.contains("&lt;script&gt;window.bad=true&lt;/script&gt;"));
        assertFalse(html.contains("<script>window.bad=true</script>"));
        assertTrue(html.contains("当前筛选范围暂无岗位数据"));
        assertTrue(html.contains("热门岗位排行"));
    }

    @Test
    void everyReportTemplateRendersAsXhtmlThatOpenHtmlToPdfCanParse() throws Exception {
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_31);
        configuration.setClassLoaderForTemplateLoading(getClass().getClassLoader(), "templates");
        configuration.setDefaultEncoding("UTF-8");
        PdfReportRenderer renderer = new PdfReportRenderer(Path.of("missing-font.ttf").toString(), false);

        for (String templateName : List.of("monthly_report.ftl", "yearly_report.ftl", "skill_report.ftl")) {
            Template template = configuration.getTemplate(templateName);
            Map<String, Object> model = new LinkedHashMap<>();
            model.put("reportTitle", "Template PDF " + templateName);
            model.put("generateTime", "2026-07-31 12:00:00");
            model.put("timeRangeStart", "2026-07-01");
            model.put("timeRangeEnd", "2026-07-31");
            model.put("emptyData", true);
            model.put("overview", Map.of());

            StringWriter output = new StringWriter();
            template.process(model, output);
            String html = output.toString();

            assertTrue(html.contains("<meta charset=\"UTF-8\" />"));
            try (PDDocument document = PDDocument.load(renderer.render(html))) {
                assertTrue(document.getNumberOfPages() >= 1);
            }
        }
    }
}
