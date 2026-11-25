package com.ahni.backend.client;

import com.ahni.backend.exception.InquiryAttachmentStorageUnavailableException;
import com.ahni.backend.exception.NoticeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NoticeImageStorageClient {
    private final SupabaseStorageClient storage;

    public NoticeImageStorageClient(
        @Value("${AHNI_SUPABASE_URL}") String supabaseUrl,
        @Value("${ahni.storage.service-role-key:}") String serviceRoleKey,
        @Value("${ahni.storage.notice-image-bucket:notice-images}") String bucket
    ) {
        storage = new SupabaseStorageClient(supabaseUrl, serviceRoleKey, bucket);
    }

    public void upload(String key, String contentType, byte[] content) {
        try {
            storage.upload(key, contentType, content);
        } catch (InquiryAttachmentStorageUnavailableException exception) {
            throw NoticeException.storageUnavailable();
        }
    }

    public byte[] download(String key) {
        try {
            return storage.download(key);
        } catch (InquiryAttachmentStorageUnavailableException exception) {
            throw NoticeException.storageUnavailable();
        }
    }

    public void delete(String key) {
        try {
            storage.delete(key);
        } catch (InquiryAttachmentStorageUnavailableException exception) {
            throw NoticeException.storageUnavailable();
        }
    }
}
