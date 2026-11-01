package com.messagekeeper.data.models

data class ContactStat(
    val name: String,
    val count: Int
)

data class HourlyStat(
    val hour: String,
    val count: Int
)

data class DailyStat(
    val day: String,
    val count: Int
)

/**
 * 消息发送趋势的时间范围选项
 */
enum class TrendRange(val label: String, val durationMillis: Long) {
    HOURS_5("5小时", 5L * 3600_000L),
    HOURS_12("12小时", 12L * 3600_000L),
    DAY_1("1天", 24L * 3600_000L),
    DAYS_7("7天", 7L * 24L * 3600_000L),
    MONTH_1("1月", 30L * 24L * 3600_000L),
    MONTHS_6("6月", 180L * 24L * 3600_000L),
    YEAR_1("1年", 365L * 24L * 3600_000L)
}

/**
 * 折线统计图的数据点
 */
data class TrendPoint(
    val label: String,
    val count: Int
)
