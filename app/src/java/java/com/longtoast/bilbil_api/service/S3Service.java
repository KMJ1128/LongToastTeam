//package com.longtoast.bilbil_api.service;
//
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//import org.springframework.web.multipart.MultipartFile;
//import software.amazon.awssdk.core.sync.RequestBody;
//import software.amazon.awssdk.services.s3.S3Client;
//import software.amazon.awssdk.services.s3.model.PutObjectRequest;
//import java.io.IOException;
//import java.util.UUID;
//
//@Service
//public class S3Service {
//
//    private final S3Client s3Client;
//
//    @Value("${cloud.aws.s3.bucket}")
//    private String bucketName;
//
//    // 💡 Region 값을 주입받아 URL 생성에 사용 (application.properties 필요)
//    @Value("${cloud.aws.region.static}")
//    private String region;
//
//    public S3Service(S3Client s3Client) {
//        this.s3Client = s3Client;
//    }
//
//    // 파일 업로드 핵심 메서드
//    public String uploadFile(MultipartFile multipartFile) throws IOException {
//        String originalFilename = multipartFile.getOriginalFilename();
//        String fileExtension = "";
//        if (originalFilename != null && originalFilename.lastIndexOf('.') != -1) {
//            fileExtension = originalFilename.substring(originalFilename.lastIndexOf('.'));
//        }
//
//        String uniqueFileName = UUID.randomUUID().toString() + fileExtension;
//        String key = "product_images/" + uniqueFileName; // 상품 이미지 폴더
//
//        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
//                .bucket(bucketName)
//                .key(key)
//                .contentType(multipartFile.getContentType())
//                .contentLength(multipartFile.getSize())
//                .build();
//
//        // 파일 업로드 실행
//        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(
//                multipartFile.getInputStream(), multipartFile.getSize()));
//
//        //  저장된 파일의 URL 반환
//        return String.format("https://%s.s3.%s.amazonaws.com/%s",
//                bucketName,
//                region,
//                key);
//    }
//}