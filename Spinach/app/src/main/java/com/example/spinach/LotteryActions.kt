package com.example.spinach

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await


/**
 * The id that identifies this entrant in a lottery waitlist.
 * The entrant is identified by the device, with no login (US 01.09.01).
 * Change it here if the app later uses profile ids.
 *
 * @param context The app's context.
 * @return The device's unique ID.
 */
@SuppressLint("HardwareIds")
fun deviceEntrantId(context: Context): String =
    Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

/**
 * Loads the lottery that is accepting entrants for a campsite.
 * Only lotteries that have not been drawn are read. The rules are findActiveLottery in SearchLogic.kt.
 *
 * @param campsiteId The id of the campsite.
 * @return The lottery whose registration is open, or `null` if there is none.
 */
suspend fun getActiveLottery(campsiteId: String): Lottery? {
    val lotteries = FirebaseFirestore.getInstance().collection("lotteries")
        .whereEqualTo("drawn", false)
        .get().await()
        .toObjects(Lottery::class.java)
    return findActiveLottery(lotteries, campsiteId, System.currentTimeMillis())
}

/**
 * Adds the entrant's id to the lottery's waitlist (US 01.01.06, US 01.02.01).
 * This runs in a transaction so the waitlist capacity (US 02.04.07) can't be exceeded.
 * The checks are joinProblem in SearchLogic.kt.
 *
 * @param lotteryId The id of the lottery to join.
 * @param entrantId The id of the entrant who is joining.
 * @throws IllegalStateException with a readable message if the entrant can't join.
 */
suspend fun joinLottery(lotteryId: String, entrantId: String) {
    val db = FirebaseFirestore.getInstance()
    val ref = db.collection("lotteries").document(lotteryId)

    // The transaction returns an error message, or null when the join worked.
    val problem = db.runTransaction<String?> { tx ->
        val lottery = tx.get(ref).toObject(Lottery::class.java)
        val issue = joinProblem(lottery, entrantId, System.currentTimeMillis())
        if (issue == null && lottery != null && entrantId !in lottery.waitlist) {
            tx.update(ref, "waitlist", FieldValue.arrayUnion(entrantId))
        }
        issue
    }.await()

    if (problem != null) throw IllegalStateException(problem)
}

/**
 * Removes the entrant's id from the lottery's waitlist (US 01.01.06, US 01.02.02).
 * Same pattern as [joinLottery]: the checks are leaveProblem in SearchLogic.kt, run inside a transaction
 * so the entrant cannot be removed while the draw is happening.
 *
 * @param lotteryId The id of the lottery to leave.
 * @param entrantId The id of the entrant who is leaving.
 * @throws IllegalStateException with a readable message if the entrant can't leave.
 */
suspend fun leaveLottery(lotteryId: String, entrantId: String) {
    val db = FirebaseFirestore.getInstance()
    val ref = db.collection("lotteries").document(lotteryId)

    // The transaction returns an error message, or null when the leave worked.
    val problem = db.runTransaction<String?> { tx ->
        val lottery = tx.get(ref).toObject(Lottery::class.java)
        val issue = leaveProblem(lottery, entrantId)
        if (issue == null) {
            tx.update(ref, "waitlist", FieldValue.arrayRemove(entrantId))
        }
        issue
    }.await()

    if (problem != null) throw IllegalStateException(problem)
}