package com.longtoast.bilbil_api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class AwsConfig {

    // application.properties에서 AWS 자격 증명을 주입받습니다.
    @Value("${cloud.aws.credentials.access-key}")
    private String accessKey;

    @Value("${cloud.aws.credentials.secret-key}")
    private String secretKey;

    @Value("${cloud.aws.region.static}")
    private String regionStatic;


    /**

     S3Client 객체를 생성하여 Spring Bean으로 등록합니다.
     이 빈은 S3Service에서 @Autowired(또는 생성자 주입)를 통해 사용됩니다.*/@Bean
    public S3Client s3Client() {// 1. 자격 증명 설정
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

        // 2. S3Client 빌드 및 반환
        return S3Client.builder()
                .region(Region.of(regionStatic)) // 지역 설정 (예: ap-northeast-2)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }
}