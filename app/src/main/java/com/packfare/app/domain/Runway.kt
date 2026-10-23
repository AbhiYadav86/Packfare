package com.packfare.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class PaceStatus { NOT_STARTED, ON_TRACK, OVER, FINISHED, NO_BUDGET }

/** Everything the "Today" card needs: how fast money is going and how long it lasts. */
data class RunwayReport(
    val status: PaceStatus,
    /** 1-based day of the trip (0 before it starts). */
    val dayNumber: Int,
    val plannedDays: Int,
    val daysUntilStart: Int,
    val dailyTargetPaise: Long,
    val avgPerDayPaise: Long,
    val spentPaise: Long,
    val todaySpentPaise: Long,
    val remainingPaise: Long,
    /** What you can spend per day for the rest of the plan to land exactly on budget. */
    val allowancePerDayPaise: Long,
    /** At the current average, how many more days the money lasts. Null before any spending. */
    val runwayDays: Int?,
)

object Runway {

    fun compute(
        startDate: LocalDate?,
        plannedDays: Int,
        budgetPaise: Long,
        spentPaise: Long,
        todaySpentPaise: Long,
        today: LocalDate,
    ): RunwayReport {
        val days = plannedDays.coerceAtLeast(0)
        val target = if (budgetPaise > 0 && days > 0) budgetPaise / days else 0L
        val start = startDate ?: today
        val untilStart = ChronoUnit.DAYS.between(today, start).toInt().coerceAtLeast(0)
        val dayNumber = if (today.isBefore(start)) 0 else ChronoUnit.DAYS.between(start, today).toInt() + 1
        val elapsed = dayNumber.coerceAtLeast(1)
        val avg = if (dayNumber == 0) 0L else spentPaise / elapsed
        val remaining = budgetPaise - spentPaise
        val daysLeft = (days - dayNumber).coerceAtLeast(0) + if (dayNumber in 1..days) 1 else 0
        val allowance = if (daysLeft > 0) remaining / daysLeft else remaining
        val runway = if (avg > 0 && remaining > 0) (remaining / avg).toInt() else if (avg > 0) 0 else null

        val status = when {
            budgetPaise <= 0 -> PaceStatus.NO_BUDGET
            dayNumber == 0 -> PaceStatus.NOT_STARTED
            days in 1 until dayNumber -> PaceStatus.FINISHED
            avg <= target * 105 / 100 -> PaceStatus.ON_TRACK
            else -> PaceStatus.OVER
        }

        return RunwayReport(
            status = status,
            dayNumber = dayNumber,
            plannedDays = days,
            daysUntilStart = untilStart,
            dailyTargetPaise = target,
            avgPerDayPaise = avg,
            spentPaise = spentPaise,
            todaySpentPaise = todaySpentPaise,
            remainingPaise = remaining,
            allowancePerDayPaise = allowance,
            runwayDays = runway,
        )
    }
}

enum class CashAlert { NONE, LOW, CARRY_FOR_REMOTE }

data class CashReport(
    val cashOnHandPaise: Long,
    val alert: CashAlert,
    /** For CARRY_FOR_REMOTE: the place with few ATMs coming up. */
    val remotePlace: String? = null,
    /** Suggested cash to carry for that stretch. */
    val suggestedCashPaise: Long = 0,
)

object CashWallet {

    /**
     * Cash on hand = withdrawals − cash spent by me.
     * Warns when it drops below two days of budget, or when a few-ATM place is coming up
     * and you won't have enough cash for the nights you plan there.
     */
    fun report(
        withdrawnPaise: Long,
        cashSpentPaise: Long,
        dailyTargetPaise: Long,
        upcomingRemote: Pair<String, Int>?, // place to nights
    ): CashReport {
        val onHand = withdrawnPaise - cashSpentPaise
        val daily = if (dailyTargetPaise > 0) dailyTargetPaise else 1_500_00L
        if (upcomingRemote != null) {
            val (place, nights) = upcomingRemote
            val need = daily * nights.coerceAtLeast(1)
            if (onHand < need) return CashReport(onHand, CashAlert.CARRY_FOR_REMOTE, place, need)
        }
        val alert = if (onHand < daily * 2) CashAlert.LOW else CashAlert.NONE
        return CashReport(onHand, alert)
    }
}
