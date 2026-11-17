package com.liuwenli.litelist.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.regex.Pattern

/** One validation entry point for UI commands and imported JSON. */
object TaskRules {
    private val grapheme = Pattern.compile("\\X")

    fun graphemeCount(value: String): Int {
        val matcher = grapheme.matcher(value)
        var count = 0
        while (matcher.find()) count++
        return count
    }

    fun normalizedTitle(value: String): String {
        val title = value.trim()
        require(title.isNotEmpty()) { "标题不能为空" }
        require(graphemeCount(title) <= 100) { "标题不能超过 100 个字素簇" }
        return title
    }

    fun validateNote(value: String?) {
        require(value == null || graphemeCount(value) <= 2_000) {
            "备注不能超过 2000 个字素簇"
        }
    }

    fun validateDeadline(plannedDate: LocalDate, deadline: LocalDateTime?) {
        require(deadline == null || !deadline.toLocalDate().isBefore(plannedDate)) {
            "DDL 日期不能早于计划日期"
        }
    }

    /** Resolve DST gaps to the transition's first valid time, overlaps to the earlier offset. */
    fun resolve(local: LocalDateTime, zone: ZoneId): Instant {
        val offsets = zone.rules.getValidOffsets(local)
        val offset: ZoneOffset = when (offsets.size) {
            0 -> return zone.rules.getTransition(local).instant
            else -> offsets.first()
        }
        return local.toInstant(offset)
    }

    fun isOverdue(status: TaskStatus, deadline: LocalDateTime?, zone: ZoneId, now: Instant): Boolean =
        status == TaskStatus.PENDING && deadline != null && now.isAfter(resolve(deadline, zone))

    fun validateTask(title: String, note: String?, plannedDate: LocalDate, deadline: LocalDateTime?): String {
        val normalized = normalizedTitle(title)
        validateNote(note)
        validateDeadline(plannedDate, deadline)
        return normalized
    }
}

enum class TaskStatus { PENDING, COMPLETED, DELETED }
