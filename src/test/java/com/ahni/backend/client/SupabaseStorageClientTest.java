package com.ahni.backend.client;

import com.ahni.backend.exception.InquiryAttachmentStorageUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SupabaseStorageClientTest {
    private static final String OBJECT_URL =
        "https://project.supabase.co/storage/v1/object/inquiry-attachments/inquiries/a/b";
    private static final byte[] CONTENT = {1, 2, 3};

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    @Test
    void 서비스_키로_비공개_버킷에_덮어쓰기_없이_업로드한다() {
        server.expect(requestTo(OBJECT_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer service-key"))
            .andExpect(header("apikey", "service-key"))
            .andExpect(header("x-upsert", "false"))
            .andExpect(header("Content-Type", "image/png"))
            .andExpect(content().bytes(CONTENT))
            .andRespond(withSuccess());

        client("service-key").upload("inquiries/a/b", "image/png", CONTENT);

        server.verify();
    }

    @Test
    void 객체를_내려받고_삭제한다() {
        server.expect(requestTo(OBJECT_URL)).andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(CONTENT, MediaType.IMAGE_PNG));
        server.expect(requestTo(OBJECT_URL)).andExpect(method(HttpMethod.DELETE))
            .andRespond(withSuccess());

        SupabaseStorageClient client = client("service-key");
        assertThat(client.download("inquiries/a/b")).isEqualTo(CONTENT);
        client.delete("inquiries/a/b");

        server.verify();
    }

    @Test
    void 저장소_오류는_세부_내용_없이_저장소_사용_불가로_바꾼다() {
        server.expect(requestTo(OBJECT_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client("service-key").download("inquiries/a/b"))
            .isInstanceOf(InquiryAttachmentStorageUnavailableException.class)
            .hasMessageNotContaining("service-key");
    }

    @Test
    void 서비스_키가_없으면_요청하지_않고_실패한다() {
        assertThatThrownBy(() -> client(" ").upload("inquiries/a/b", "image/png", CONTENT))
            .isInstanceOf(InquiryAttachmentStorageUnavailableException.class);

        server.verify();
    }

    private SupabaseStorageClient client(String serviceRoleKey) {
        return new SupabaseStorageClient(builder, "https://project.supabase.co", serviceRoleKey, "inquiry-attachments");
    }
}
