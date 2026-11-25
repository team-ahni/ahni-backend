package com.ahni.backend.domain;

import com.ahni.backend.exception.NoticeException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoticeDocumentTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void generatesPlainTextAndDropsUntrustedImageUrls() {
        var document = NoticeDocument.parse(json.readTree("""
            {"type":"doc","content":[
                {"type":"paragraph","content":[{"type":"text","text":"안내","marks":[{"type":"bold"}]}]},
                {"type":"image","attrs":{"imageId":"11111111-1111-4111-8111-111111111111","src":"https://external.invalid/pixel","alt":"일정"}},
                {"type":"paragraph","content":[{"type":"text","text":"신청 기간"}]}
            ]}
            """));
        assertThat(document.text()).isEqualTo("안내\n\n신청 기간");
        assertThat(document.imageIds()).hasSize(1);
        assertThat(document.json()).doesNotContain("external", "src");
    }

    @Test
    void rejectsUnsupportedEmbedsAndFormatting() {
        assertThatThrownBy(() -> NoticeDocument.parse(json.readTree("""
            {"type":"doc","content":[{"type":"iframe","attrs":{"src":"javascript:bad"}}]}
            """))).isInstanceOf(NoticeException.class);
        assertThatThrownBy(() -> NoticeDocument.parse(json.readTree("""
            {"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"bad","marks":[{"type":"link","attrs":{"href":"javascript:bad"}}]}]}]}
            """))).isInstanceOf(NoticeException.class);
    }
}
