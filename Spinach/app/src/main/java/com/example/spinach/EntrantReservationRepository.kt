package com.example.spinach

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration


/**
 * EntrantReservationRepository
 * Entrant side of reservations in Firestore. The owner side is ReservationRepository.
 * Listeners keep calling onResult when data changes. Call remove() on the returned
 * registration when the screen goes away.
 *
 * To cover:
 * - US 01.06.01 As an entrant, I want to view a list of my active reservations.
 * - US 01.07.01 As an entrant, I want to cancel my campsite reservation.
 * - US 01.05.01 As an entrant, I want to receive confirmation of my campsite reservation.
 *
 * @param db The Firestore database. Can be replaced in tests.
 */
class EntrantReservationRepository(
    db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private val reservations = db.collection("reservations")

    /**
     * Listens for all reservations of one entrant (US 01.06.01).
     * This returns every status. To show only the active ones that have not ended, filter with
     * ReservationQueries.active(list, ReservationQueries.todayUtcMidnight()).
     *
     * @param entrantId The id of the entrant.
     * @param onResult Called with the entrant's reservations now and every time they change.
     * @param onError Called if Firestore could not load the reservations.
     * @return The registration to remove() when the screen goes away.
     */
    fun listenForEntrant(
        entrantId: String,
        onResult: (List<Reservation>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration =
        reservations.whereEqualTo("entrantId", entrantId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                onResult(snapshot?.toObjects(Reservation::class.java).orEmpty())
            }

    /**
     * Cancels a reservation (US 01.07.01) by setting its status to CANCELLED.
     * The owner's lists read the same status (US 02.07.03).
     * Nothing is written when EntrantReservationRules says the reservation cannot be cancelled.
     *
     * @param reservation The reservation to cancel.
     * @param entrantId The id of the entrant who is cancelling.
     * @param today Today as UTC midnight in millis (see ReservationQueries.todayUtcMidnight).
     * @param onDone Called with success, or with the reason it failed.
     */
    fun cancel(
        reservation: Reservation,
        entrantId: String,
        today: Long,
        onDone: (Result<Unit>) -> Unit,
    ) {
        // check the rules first so we do not write something that is not allowed
        // This code was produced partly by Claude Sonnet 5.5, 6/11/2026 over the prompt
        // "Help me with the argument handling and code logic".
        val error = EntrantReservationRules.cancelError(reservation, entrantId, today)
        if (error != null) {
            onDone(Result.failure(IllegalArgumentException(error)))
            return
        }
        reservations.document(reservation.id)
            .update("status", ReservationStatus.CANCELLED.name)
            .addOnSuccessListener { onDone(Result.success(Unit)) }
            .addOnFailureListener { onDone(Result.failure(it)) }
    }

    /**
     * Saves a reservation for a lottery winner (US 01.05.01).
     * It first loads the campsite's reservations to find the dates that are already taken,
     * then checks EntrantReservationRules.confirmError. Nothing is saved if a rule fails.
     * The caller must set campsiteId, campsiteName, dates and rulesAcknowledged on the
     * reservation. After onDone succeeds, show EntrantReservationRules.confirmationMessage
     * as the confirmation.
     *
     * @param reservation The reservation to save.
     * @param lottery The lottery the entrant won.
     * @param entrantId The id of the entrant who is confirming.
     * @param now The current time in millis.
     * @param onDone Called with the new reservation's id, or with the reason it failed.
     */
    fun confirm(
        reservation: Reservation,
        lottery: Lottery,
        entrantId: String,
        now: Long,
        onDone: (Result<String>) -> Unit,
    ) {
        reservations.whereEqualTo("campsiteId", reservation.campsiteId).get()
            .addOnSuccessListener { snapshot ->
                val existing = snapshot.toObjects(Reservation::class.java)
                val booked = ReservationQueries.bookedDates(
                    existing,
                    reservation.campsiteId,
                    ReservationQueries.todayUtcMidnight(),
                )

                val error = EntrantReservationRules.confirmError(reservation, lottery, entrantId, booked, now)
                if (error != null) {
                    onDone(Result.failure(IllegalArgumentException(error)))
                    return@addOnSuccessListener
                }

                // fill in who and what this reservation belongs to
                reservation.entrantId = entrantId
                reservation.lotteryId = lottery.id
                reservation.ownerId = lottery.ownerId
                reservation.status = ReservationStatus.ACTIVE
                reservation.createdAt = now

                val document = reservations.document()
                document.set(reservation)
                    .addOnSuccessListener { onDone(Result.success(document.id)) }
                    .addOnFailureListener { onDone(Result.failure(it)) }
            }
            .addOnFailureListener { onDone(Result.failure(it)) }
    }
}