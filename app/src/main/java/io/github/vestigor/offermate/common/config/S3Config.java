package io.github.vestigor.offermate.common.config;

import io.github.vestigor.offermate.common.properties.StorageConfigProperties;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/**
 * S3客户端配置（用于RustFS）
 */
@Configuration
@RequiredArgsConstructor
public class S3Config {

    private final StorageConfigProperties storageConfigProperties;

    @Bean
    public S3Client s3Client() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                storageConfigProperties.getAccessKey(),
                storageConfigProperties.getSecretKey()
        );

        return S3Client.builder()
                .endpointOverride(URI.create(storageConfigProperties.getEndpoint()))
                .region(Region.of(storageConfigProperties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .forcePathStyle(true) // 关键配置：使用路径风格访问，否则 SDK 会使用虚拟主机风格（`bucket.endpoint`）导致 DNS 解析失败
                .build();
    }
}
