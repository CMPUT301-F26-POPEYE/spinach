package com.example.spinach

// Plain functions, no Firebase, so JUnit can test them.
object NotificationRules {

    fun messageError(message: String): String? =
        if (message.isBlank()) "Write a message first" else null

    // (lotteryId, entrantId) for everyone still waiting on a lottery that has not been drawn
    fun waitlistedPairs(lotteries: List<Lottery>): List<Pair<String, String>> =
        lotteries
            .filter { !it.drawn }
            .flatMap { lottery -> lottery.waitlist.distinct().map { lottery.id to it } }

    // drops entrants who turned notifications off
    fun recipients(
        pairs: List<Pair<String, String>>,
        optedOut: Set<String>,
    ): List<Pair<String, String>> = pairs.filter { it.second !in optedOut }

    // US 02.10.02 and US 03.08.01
    fun newestFirst(notifications: List<Notification>): List<Notification> =
        notifications.sortedByDescending { it.createdAt }
}
