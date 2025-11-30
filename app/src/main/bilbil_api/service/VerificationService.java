package com.longtoast.bilbil_api.service;

import com.longtoast.bilbil_api.domain.User;
import com.longtoast.bilbil_api.dto.VerificationCache;
import com.longtoast.bilbil_api.dto.VerificationResponse;
import com.longtoast.bilbil_api.exception.PhoneAlreadyUsedException;
import com.longtoast.bilbil_api.repository.UserRepository;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.search.SubjectTerm;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationService {

    private final UserRepository userRepository;
    private final MemberService memberService;

    // 💡 [핵심] In-Memory 캐시: Key=User ID, Value=인증 정보 DTO
    private final Map<Integer, VerificationCache> verificationCache = new ConcurrentHashMap<>();

    // application.properties에서 주입받는 설정값
    @Value("${verification.recipient-email}")
    private String recipientEmail;
    @Value("${spring.mail.username}")
    private String mailUsername;
    @Value("${spring.mail.password}")
    private String mailPassword;

    /**
     * [1단계] 인증 코드 생성 및 SMS URL 반환
     */
    public VerificationResponse requestVerification(Integer userId, String phoneNumber) {
        // 🔥 0. 전화번호 중복 체크 (핵심)
        if (userRepository.findByPhoneNumber(phoneNumber).isPresent()) {
            throw new PhoneAlreadyUsedException("이미 다른 소셜로그인으로 가입된 사용자입니다");
        }
        // 1. 기존 인증 정보 삭제 (재인증 요청 시)
        verificationCache.remove(userId);

        // 2. 6자리 랜덤 인증 코드 생성
        String code = String.valueOf(new Random().nextInt(900000) + 100000);
        LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(5); // 5분 유효

        // 3. In-Memory Cache에 저장
        VerificationCache cache = VerificationCache.builder()
                .phoneNumber(phoneNumber)
                .code(code)
                .expiryTime(expiryTime)
                .build();
        verificationCache.put(userId, cache);

        // 4. SMS URL 생성 (클라이언트가 문자를 보내도록 유도)
        String encodedBody = String.format("[빌빌] 인증번호: %s", code);
        String smsUrl = String.format("sms:%s?body=%s", recipientEmail, encodedBody);

        log.info("인증 요청 완료 (Cache): User={}, Code={}, SMS URL={}", userId, code, smsUrl);

        return VerificationResponse.builder()
                .smsUrl(smsUrl)
                .recipientEmail(recipientEmail)
                .verificationCode(code)
                .build();
    }

    /**
     * [2단계] 메일함 확인 및 인증 처리
     */
    @Transactional
    public void confirmVerification(Integer userId, String phoneNumber) {

        // 1. In-Memory Cache에서 인증 정보 조회
        VerificationCache verification = Optional.ofNullable(verificationCache.get(userId))
                .orElseThrow(() -> new IllegalArgumentException("유효한 인증 요청 정보를 찾을 수 없습니다. (재요청 필요)"));

        // 2. 시간 만료 체크
        if (verification.getExpiryTime().isBefore(LocalDateTime.now())) {
            verificationCache.remove(userId);
            throw new IllegalArgumentException("인증 시간이 만료되었습니다. 다시 요청해주세요.");
        }

        // 3. 전화번호 일치 체크 (클라이언트가 요청한 번호)
        if (!verification.getPhoneNumber().equals(phoneNumber)) {
            throw new IllegalArgumentException("요청된 전화번호와 현재 인증하려는 번호가 일치하지 않습니다.");
        }

        // 4. IMAP을 통해 이메일 수신 여부 확인 (실제 로직 적용)
        String expectedCode = verification.getCode();
        String senderPhoneNumber = checkEmailForVerificationCode(expectedCode); // 🟢 실제 IMAP 호출

        // 5. 수신된 전화번호와 요청 번호가 일치하는지 확인
        if (senderPhoneNumber == null || !senderPhoneNumber.equals(phoneNumber)) {
            log.error("인증 실패: 보낸 전화번호 불일치 또는 메일 미수신. Expected={}, Received={}", phoneNumber, senderPhoneNumber);
            throw new IllegalArgumentException("인증 코드가 포함된 문자가 정상적으로 수신되지 않았습니다.");
        }

        // 6. 인증 성공 처리: User 테이블에 전화번호 업데이트
        memberService.updatePhoneNumber(userId, phoneNumber);

        // 7. Cache에서 성공적으로 사용된 코드 삭제
        verificationCache.remove(userId);

        log.info("User {} 전화번호 인증 성공 및 DB 저장 완료", userId);
    }

    /**
     * 💡 [핵심 구현] IMAP을 사용하여 인증 코드가 포함된 이메일을 확인하고 발신자 전화번호를 추출합니다.
     * @param expectedCode 예상되는 인증 코드
     * @return 문자를 보낸 사람의 전화번호 (예: 01012345678) 또는 실패 시 null
     */
    private String checkEmailForVerificationCode(String expectedCode) {
        // IMAP 설정 (Gmail 기준)
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", "imap.gmail.com");

        Session session = Session.getDefaultInstance(props, null);
        Store store = null;
        Folder inbox = null;

        try {
            // 1. IMAP 서버에 연결 (spring.mail.username과 password 사용)
            store = session.getStore("imaps");
            store.connect("imap.gmail.com", mailUsername, mailPassword);

            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);

            // 2. 검색 조건 설정: 제목에 인증 코드를 포함한 문자 메일 검색
            // 💡 문자 메일은 보통 제목이 없거나 짧고, 'From' 주소가 전화번호 기반입니다.
            // 여기서는 5분 이내 메일 중 발신자를 기준으로 확인합니다.

            // 3. 최근 10개의 메시지만 확인 (성능 최적화)
            int messageCount = inbox.getMessageCount();
            Message[] messages = inbox.getMessages(Math.max(1, messageCount - 9), messageCount);

            // 4. 메시지 순회하며 인증 코드 및 전화번호 추출
            for (int i = messages.length - 1; i >= 0; i--) { // 최신 메일부터 역순으로 확인
                Message message = messages[i];

                // 5분 이내 메일만 확인 (캐시 만료 시간과 유사하게)
                if (message.getReceivedDate().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().isBefore(LocalDateTime.now().minusMinutes(5))) {
                    continue; // 5분 지난 메일은 무시
                }

                // 5. [핵심] 메시지 내용에서 인증 코드가 포함된 문자를 확인
                if (isContentMatching(message, expectedCode)) {
                    // 6. 발신자 주소(From) 추출 및 전화번호 파싱
                    Address[] fromAddresses = message.getFrom();
                    if (fromAddresses != null && fromAddresses.length > 0) {
                        String from = ((InternetAddress) fromAddresses[0]).getAddress();
                        String parsedPhoneNumber = parsePhoneNumberFromEmail(from);

                        if (parsedPhoneNumber != null) {
                            log.info("IMAP 성공: 코드 포함 메일 확인, 추출된 번호: {}", parsedPhoneNumber);
                            return parsedPhoneNumber; // 🟢 추출된 실제 전화번호 반환
                        }
                    }
                }
            }

            log.warn("IMAP 실패: 인증 코드 {}를 포함한 메일을 찾을 수 없음.", expectedCode);
            return null;

        } catch (Exception e) {
            log.error("IMAP 연결 또는 이메일 검색 중 오류 발생. 계정, 비밀번호(앱 비밀번호) 및 IMAP 설정을 확인하세요.", e);
            return null;
        } finally {
            try {
                if (inbox != null && inbox.isOpen()) inbox.close(false);
                if (store != null && store.isConnected()) store.close();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Helper 함수: 이메일 주소에서 전화번호 추출 (통신사 게이트웨이 형식 기반)
     */
    private String parsePhoneNumberFromEmail(String emailAddress) {
        // 한국 통신사 SMS to Email 게이트웨이 주소의 일반적인 형태: 010xxxxxxxx@...
        // 이메일 주소에서 @ 앞부분을 가져와서 숫자만 남기는 정규식
        // 예: "01012345678@vtext.com" -> "01012345678"

        // 10자리(010XXXXXX) 또는 11자리(010XXXXXXXX) 숫자를 추출하는 패턴
        Pattern pattern = Pattern.compile("(\\d{10,11})@");
        Matcher matcher = pattern.matcher(emailAddress);

        if (matcher.find()) {
            // 정규식 그룹 1 (전화번호) 반환
            return matcher.group(1);
        }

        log.warn("전화번호 추출 실패: From 주소 패턴 불일치 {}", emailAddress);
        return null;
    }

    /**
     * Helper 함수: 메시지 내용에 인증 코드가 포함되어 있는지 확인 (간단화된 로직)
     * 🚨 주의: 이 로직은 멀티파트 메시지(HTML, 텍스트)를 완벽하게 처리하지 못할 수 있습니다.
     */
    private boolean isContentMatching(Message message, String expectedCode) {
        try {
            String targetText = "[빌빌] 인증번호: " + expectedCode;

            // 텍스트 부분만 추출하여 확인
            if (message.isMimeType("text/plain")) {
                String content = (String) message.getContent();
                return content.contains(targetText);
            }
            // 멀티파트 메시지일 경우
            else if (message.isMimeType("multipart/*")) {
                Multipart multipart = (Multipart) message.getContent();
                for (int i = 0; i < multipart.getCount(); i++) {
                    BodyPart bodyPart = multipart.getBodyPart(i);
                    // 텍스트 부분만 확인
                    if (bodyPart.isMimeType("text/plain")) {
                        String content = (String) bodyPart.getContent();
                        if (content.contains(targetText)) return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            log.error("메시지 내용 확인 중 오류 발생", e);
            return false;
        }
    }

    /**
     * 소셜 로그인 시, 해당 전화번호가 이미 다른 계정에 연결되어 있는지 확인
     */
    @Transactional(readOnly = true)
    public boolean existsByPhoneNumber(String phoneNumber) {
        return userRepository.findByPhoneNumber(phoneNumber).isPresent();
    }
}