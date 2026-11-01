package com.messagekeeper.ui.screens.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.messagekeeper.data.db.entity.Conversation
import com.messagekeeper.data.models.ContactStat
import com.messagekeeper.data.models.DailyStat
import com.messagekeeper.data.models.HourlyStat
import com.messagekeeper.data.models.TrendPoint
import com.messagekeeper.data.models.TrendRange
import com.messagekeeper.data.repository.MessageRepository
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AnalyticsUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val conversations: List<Conversation> = emptyList(),
    /** null 表示查看全部联系人，非 null 表示查看指定联系人会话 */
    val selectedConversationId: Long? = null,
    val selectedTrendRange: TrendRange = TrendRange.DAY_1,
    val trendPoints: List<TrendPoint> = emptyList(),
    val totalMessages: Int = 0,
    val topContacts: List<ContactStat> = emptyList(),
    val hourlyStats: List<HourlyStat> = emptyList(),
    val dailyStats: List<DailyStat> = emptyList()
) {
    val selectedConversationName: String
        get() = selectedConversationId?.let { id ->
            conversations.find { it.id == id }?.display_name
        } ?: "全部联系人"
}

class AnalyticsViewModel(
    private val repository: MessageRepository = MessageRepository.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun selectConversation(conversationId: Long?) {
        if (_uiState.value.selectedConversationId == conversationId) return
        _uiState.value = _uiState.value.copy(selectedConversationId = conversationId)
        loadData()
    }

    fun selectTrendRange(range: TrendRange) {
        if (_uiState.value.selectedTrendRange == range) return
        _uiState.value = _uiState.value.copy(selectedTrendRange = range)
        viewModelScope.launch {
            try {
                val convId = _uiState.value.selectedConversationId
                val points = computeTrendPoints(range, convId)
                _uiState.value = _uiState.value.copy(trendPoints = points)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            val currentConvId = _uiState.value.selectedConversationId
            val currentRange = _uiState.value.selectedTrendRange
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val since = LocalDate.now()
                    .minusDays(6)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()

                val result = coroutineScope {
                    val convsDeferred = async {
                        repository.getAllConversationsSync()
                            .filter { !it.is_system && it.platform != "qqmusic" }
                    }
                    val total = async { repository.getTotalMessageCount(currentConvId) }
                    val top = async { repository.getTopContacts(5, currentConvId) }
                    val hourly = async { repository.getMessageCountByHour(currentConvId) }
                    val daily = async { repository.getMessageCountByDay(since, currentConvId) }
                    val trend = async { computeTrendPoints(currentRange, currentConvId) }

                    val convs = convsDeferred.await()
                    val validConvId = currentConvId?.takeIf { id -> convs.any { it.id == id } }

                    AnalyticsUiState(
                        isLoading = false,
                        conversations = convs,
                        selectedConversationId = validConvId,
                        selectedTrendRange = currentRange,
                        trendPoints = trend.await(),
                        totalMessages = total.await(),
                        topContacts = top.await(),
                        hourlyStats = hourly.await(),
                        dailyStats = daily.await()
                    )
                }
                _uiState.value = result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "统计数据暂时无法读取"
                )
            }
        }
    }

    private suspend fun computeTrendPoints(range: TrendRange, conversationId: Long?): List<TrendPoint> {
        val zone = ZoneId.systemDefault()
        val nowZdt = ZonedDateTime.now(zone)

        return when (range) {
            TrendRange.HOURS_5, TrendRange.HOURS_12, TrendRange.DAY_1 -> {
                val hoursCount = when (range) {
                    TrendRange.HOURS_5 -> 5
                    TrendRange.HOURS_12 -> 12
                    else -> 24
                }
                val currentHourStart = nowZdt.truncatedTo(ChronoUnit.HOURS)
                val firstBucketStart = currentHourStart.minusHours((hoursCount - 1).toLong())
                val sinceMs = firstBucketStart.toInstant().toEpochMilli()
                val timestamps = repository.getMessageTimestampsSince(sinceMs, conversationId)

                val counts = IntArray(hoursCount)
                val bucketMs = 3600_000L
                for (ts in timestamps) {
                    val idx = ((ts - sinceMs) / bucketMs).toInt()
                    if (idx in 0 until hoursCount) {
                        counts[idx]++
                    }
                }
                val fmt = DateTimeFormatter.ofPattern("HH:00", Locale.getDefault())
                (0 until hoursCount).map { i ->
                    val bucketTime = firstBucketStart.plusHours(i.toLong())
                    TrendPoint(label = bucketTime.format(fmt), count = counts[i])
                }
            }

            TrendRange.DAYS_7, TrendRange.MONTH_1 -> {
                val daysCount = if (range == TrendRange.DAYS_7) 7 else 30
                val today = LocalDate.now(zone)
                val startDay = today.minusDays((daysCount - 1).toLong())
                val sinceMs = startDay.atStartOfDay(zone).toInstant().toEpochMilli()
                val timestamps = repository.getMessageTimestampsSince(sinceMs, conversationId)

                val countsByDay = HashMap<LocalDate, Int>()
                for (ts in timestamps) {
                    val date = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
                    countsByDay[date] = (countsByDay[date] ?: 0) + 1
                }
                val fmt = DateTimeFormatter.ofPattern("M/d", Locale.getDefault())
                (0 until daysCount).map { i ->
                    val d = startDay.plusDays(i.toLong())
                    TrendPoint(label = d.format(fmt), count = countsByDay[d] ?: 0)
                }
            }

            TrendRange.MONTHS_6 -> {
                val monthsCount = 6
                val currentMonth = YearMonth.now(zone)
                val startMonth = currentMonth.minusMonths((monthsCount - 1).toLong())
                val sinceMs = startMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val timestamps = repository.getMessageTimestampsSince(sinceMs, conversationId)

                val countsByMonth = HashMap<YearMonth, Int>()
                for (ts in timestamps) {
                    val ym = YearMonth.from(Instant.ofEpochMilli(ts).atZone(zone))
                    countsByMonth[ym] = (countsByMonth[ym] ?: 0) + 1
                }
                val fmt = DateTimeFormatter.ofPattern("M月", Locale.getDefault())
                (0 until monthsCount).map { i ->
                    val ym = startMonth.plusMonths(i.toLong())
                    TrendPoint(label = ym.format(fmt), count = countsByMonth[ym] ?: 0)
                }
            }

            TrendRange.YEAR_1 -> {
                val monthsCount = 12
                val currentMonth = YearMonth.now(zone)
                val startMonth = currentMonth.minusMonths((monthsCount - 1).toLong())
                val sinceMs = startMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val timestamps = repository.getMessageTimestampsSince(sinceMs, conversationId)

                val countsByMonth = HashMap<YearMonth, Int>()
                for (ts in timestamps) {
                    val ym = YearMonth.from(Instant.ofEpochMilli(ts).atZone(zone))
                    countsByMonth[ym] = (countsByMonth[ym] ?: 0) + 1
                }
                val fmt = DateTimeFormatter.ofPattern("yy/M", Locale.getDefault())
                (0 until monthsCount).map { i ->
                    val ym = startMonth.plusMonths(i.toLong())
                    TrendPoint(label = ym.format(fmt), count = countsByMonth[ym] ?: 0)
                }
            }
        }
    }
}
