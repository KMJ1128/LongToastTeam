// com.longtoast.bilbil.TimeAgoUtils.kt
package com.longtoast.bilbil

import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object TimeAgoUtils {

    // 백엔드에서 내려오는 형식: "yyyy-MM-dd'T'HH:mm:ss"
    private val SERVER_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    private val ZONE_KOREA: ZoneId = ZoneId.of("Asia/Seoul")

    fun formatKorean(createdAt: String?): String {
        if (createdAt.isNullOrBlank()) return "방금 전"

        return try {
            // 혹시 뒤에 .SSS 같은게 붙어있으면 앞 19자리만 사용
            val base = if (createdAt.length >= 19) createdAt.substring(0, 19) else createdAt
            val ldt = LocalDateTime.parse(base, SERVER_FORMATTER)

            val created = ldt.atZone(ZONE_KOREA)
            val now = ZonedDateTime.now(ZONE_KOREA)

            // 미래값 방어
            if (created.isAfter(now)) return "방금 전"

            val duration = Duration.between(created, now)
            val seconds = duration.seconds
            val minutes = seconds / 60
            val hours = seconds / 3600

            // 1시간 이내는 Duration 기반
            when {
                seconds < 60 -> return "${seconds}초 전"
                minutes < 60 -> return "${minutes}분 전"
                hours < 24 -> return "${hours}시간 전"
            }

            // 날짜 단위부터는 “캘린더 날짜 차이” 기준으로 계산
            val createdDate = created.toLocalDate()
            val today = now.toLocalDate()
            val days = ChronoUnit.DAYS.between(createdDate, today)

            if (days < 7) return "${days}일 전"

            val weeks = days / 7
            if (weeks < 5) return "${weeks}주 전"

            val months = ChronoUnit.MONTHS.between(
                createdDate.withDayOfMonth(1),
                today.withDayOfMonth(1)
            )
            if (months < 12) return "${months}개월 전"

            val years = ChronoUnit.YEARS.between(
                createdDate.withDayOfYear(1),
                today.withDayOfYear(1)
            )
            "${years}년 전"

        } catch (e: Exception) {
            // 파싱 실패하면 일단 "방금 전"으로 처리
            "방금 전"
        }
    }
}
