package com.example.spinach

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

// Listeners keep calling onResult when data changes. Call remove() on the
// returned registration when the screen goes away.
class ReservationRepository(
    db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private val reservations = db.collection("reservations")

    // US 02.07.01 and US 02.07.02
    fun listenForOwner(
        ownerId: String,
        onResult: (List<Reservation>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration =
        listen(reservations.whereEqualTo("ownerId", ownerId), onResult, onError)

    // US 02.06.01
    fun listenForCampsite(
        campsiteId: String,
        onResult: (List<Reservation>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration =
        listen(reservations.whereEqualTo("campsiteId", campsiteId), onResult, onError)

    // US 02.08.01: only the owner of the reservation can reject it. Rejecting frees the dates,
    // because only ACTIVE reservations count as booked.
    fun reject(reservation: Reservation, ownerId: String, onDone: (Result<Unit>) -> Unit) {
        if (reservation.id.isEmpty() || reservation.ownerId != ownerId) {
            onDone(Result.failure(Exception("Only the campsite owner can reject a reservation")))
            return
        }
        if (!ReservationQueries.canReject(reservation, ReservationQueries.todayUtcMidnight())) {
            onDone(Result.failure(Exception("Only an upcoming active reservation can be rejected")))
            return
        }
        reservations.document(reservation.id).update("status", ReservationStatus.REJECTED.name)
            .addOnSuccessListener { onDone(Result.success(Unit)) }
            .addOnFailureListener { onDone(Result.failure(it)) }
    }

    // Creates the reservation, or overwrites it when it already has an id.
    fun save(reservation: Reservation, onDone: (Result<String>) -> Unit) {
        val document = if (reservation.id.isEmpty()) {
            reservations.document()
        } else {
            reservations.document(reservation.id)
        }
        document.set(reservation)
            .addOnSuccessListener { onDone(Result.success(document.id)) }
            .addOnFailureListener { onDone(Result.failure(it)) }
    }

    private fun listen(
        query: Query,
        onResult: (List<Reservation>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration =
        query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            onResult(snapshot?.toObjects(Reservation::class.java).orEmpty())
        }
}