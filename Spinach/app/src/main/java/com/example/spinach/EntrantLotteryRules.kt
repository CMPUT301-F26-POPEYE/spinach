package com.example.spinach



/** What happened to one entrant in one lottery. */
enum class LotteryResult { WAITING, WON, LOST, DECLINED }

/**
 * EntrantLotteryRules
 * Plain functions (no Firebase, no Android) for the entrant side of a lottery,
 * so JUnit can test them (see EntrantLotteryRulesTest).
 *
 * To cover:
 * - US 01.02.02 As an entrant, I want to leave the waiting list for a campsite lottery.
 * - US 01.03.01 As an entrant, I want to receive a notification when I win the campsite lottery.
 * - US 01.03.02 As an entrant, I want to receive a notification when I lose the campsite lottery.
 *
 * Lottery.draw() moves everyone off the waitlist into winners or losers, so the result of the
 * draw is already stored on the lottery. We read the result from there instead of storing a
 * separate notification.
 */
object EntrantLotteryRules {

    /**
     * Checks if an entrant can leave the waiting list of a lottery (US 01.02.02).
     * They can only leave before the draw, and only if they are on the list.
     *
     * @param lottery The lottery the entrant wants to leave.
     * @param entrantId The id of the entrant who wants to leave.
     * @return `null` if the entrant can leave, otherwise the reason they cannot.
     */
    fun leaveError(lottery: Lottery, entrantId: String): String? {
        // after the draw the waitlist is empty and the entrant is a winner or a loser
        if (lottery.drawn) return "The draw already happened"
        if (!lottery.waitlist.contains(entrantId)) return "You are not on this waiting list"
        return null
    }


    /**
     * Checks if an entrant is allowed to decline a lottery win.
     *
     */
    fun declineError(lottery: Lottery, entrantId: String): String? {
        if (!lottery.drawn) return "The lottery has not been drawn yet"
        if (!lottery.winners.contains(entrantId)) return "You did not win this lottery"
        if (lottery.declined.contains(entrantId)) return "You already declined this win"

        return null
    }


    /**
     * Finds out what happened to an entrant in a lottery (US 01.03.01, US 01.03.02).
     *
     * @param lottery The lottery to look at.
     * @param entrantId The id of the entrant.
     * @return WON if they are a winner, LOST if they are a loser, otherwise WAITING.
     */
    fun resultFor(lottery: Lottery, entrantId: String): LotteryResult {
        if (lottery.declined.contains(entrantId)) return LotteryResult.DECLINED//US 1.04.03
        if (lottery.winners.contains(entrantId)) return LotteryResult.WON
        if (lottery.losers.contains(entrantId)) return LotteryResult.LOST
        return LotteryResult.WAITING
    }

    /**
     * Title of the notification for a result (US 01.03.01, US 01.03.02).
     * @return The title, or `null` while the entrant is still waiting (nothing to show).
     */
    fun resultTitle(result: LotteryResult): String? = when (result) {
        LotteryResult.DECLINED -> "Win declined" //US 1.04.03
        LotteryResult.WON -> "You won!"
        LotteryResult.LOST -> "Lottery result"
        LotteryResult.WAITING -> null
    }

    /**
     * Text of the notification for a result (US 01.03.01, US 01.03.02).
     * @return The text, or `null` while the entrant is still waiting (nothing to show).
     */
    fun resultMessage(result: LotteryResult): String? = when (result) {
        LotteryResult.WON -> "You won the lottery! Pick your dates and reserve your campsite."
        LotteryResult.LOST -> "Sorry, you were not selected in this lottery."
        LotteryResult.DECLINED -> "You declined this lottery win and can no longer reserve."//US 1.04.03
        LotteryResult.WAITING -> null
    }
}