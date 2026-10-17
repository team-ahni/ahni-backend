package com.ahni.backend.client;

import com.ahni.backend.exception.InquiryAttachmentStorageUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.function.Supplier;

/** 서비스 키로 비공개 버킷에 접근한다. 키와 파일 내용은 로그에 남기지 않는다. */
@Component
public class SupabaseStorageClient {
    private static final Logger log = LoggerFactory.getLogger(SupabaseStorageClient.class);

    private final RestClient restClient;
    private final String bucket;
    private final boolean configured;

    @Autowired
    public SupabaseStorageClient(
        @Value("${AHNI_SUPABASE_URL}") String supabaseUrl,
        @Value("${ahni.storage.service-role-key:}") String serviceRoleKey,
        @Value("${ahni.storage.inquiry-attachment-bucket}") String bucket
    ) {
        this(RestClient.builder(), supabaseUrl, serviceRoleKey, bucket);
    }

    SupabaseStorageClient(
        RestClient.Builder builder,
        String supabaseUrl,
        String serviceRoleKey,
        String bucket
    ) {
        this.restClient = builder
            .baseUrl(supabaseUrl + "/storage/v1/object/" + bucket + "/")
            .defaultHeader("Authorization", "Bearer " + serviceRoleKey)
            .defaultHeader("apikey", serviceRoleKey)
            .build();
        this.bucket = bucket;
        this.configured = !serviceRoleKey.isBlank();
    }

    public void upload(String key, String contentType, byte[] content) {
        call("upload", () -> restClient.post()
            .uri(key)
            .contentType(MediaType.parseMediaType(contentType))
            .header("x-upsert", "false")
            .body(content)
            .retrieve()
            .toBodilessEntity());
    }

    public byte[] download(String key) {
        byte[] content = call("download", () -> restClient.get()
            .uri(key)
            .retrieve()
            .body(byte[].class));
        if (content == null) {
            throw new InquiryAttachmentStorageUnavailableException();
        }
        return content;
    }

    public void delete(String key) {
        call("delete", () -> restClient.delete()
            .uri(key)
            .retrieve()
            .toBodilessEntity());
    }

    private <T> T call(String operation, Supplier<T> request) {
        if (!configured) {
            log.error("Supabase Storage {} 불가: AHNI_SUPABASE_SERVICE_ROLE_KEY가 설정되지 않았습니다.", operation);
            throw new InquiryAttachmentStorageUnavailableException();
        }
        try {
            return request.get();
        } catch (RestClientException exception) {
            log.warn("Supabase Storage {} 실패 (bucket={}): {}", operation, bucket, exception.getClass().getSimpleName());
            throw new InquiryAttachmentStorageUnavailableException();
        }
    }
}
