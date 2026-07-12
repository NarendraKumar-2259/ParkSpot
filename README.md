# ParkSpot 🅿️

A real-time parking slot reservation app for Android, built with **Jetpack Compose**, **Firebase**, and a clean **MVVM** architecture.

Users pick a time window, see live availability across a pool of 20 parking slots, reserve one with a tap, and manage their bookings — with **double-booking made impossible** at the database level, even when multiple users race for the same slot.


https://github.com/user-attachments/assets/bcea3fef-d2f4-4d53-b249-b8a95da3c424


https://github.com/user-attachments/assets/07a7c88c-613b-49d7-9f2c-32e3d779b9bf





## Features

- **Live availability dashboard** — all 20 slots update in real time via Firestore snapshot listeners; when another user books a slot, your screen reflects it instantly.
- **Time-window booking** — pick any start/end time; availability is computed per slot against the selected window, with the first conflicting booking shown so users know *when* the slot frees up.
- **Race-condition-proof reservations** — bookings are committed inside a Firestore transaction. If two users try to reserve the same slot for overlapping times simultaneously, exactly one succeeds; the other gets a clear conflict message.
- **My bookings** — view upcoming and past reservations, cancel with per-item progress indicators; ownership is verified inside the transaction, so users can only cancel their own bookings.
- **Frictionless onboarding** — anonymous Firebase authentication; no sign-up form, the app just works.
- **Resilient UX** — loading, error, and retry states on every screen; input validation (no past start times, end must be after start); snackbar feedback for every outcome.

## Tech Stack

| Layer | Technology |
|---|---|
| UI | Jetpack Compose, Material 3 |
| State management | Kotlin `StateFlow` + `combine`, unidirectional data flow |
| Dependency injection | Hilt |
| Backend | Firebase — Cloud Firestore + Anonymous Auth |
| Async | Kotlin Coroutines & Flow (`callbackFlow` bridges Firestore listeners into cold Flows) |
| Build | Gradle Kotlin DSL, version catalogs, KSP |

## Architecture

```
┌─────────────────────────────────────────────────┐
│  presentation/                                  │
│  Compose screens ← StateFlow<UiState> ←        │
│  DashboardViewModel · MyReservationsViewModel   │
├─────────────────────────────────────────────────┤
│  data/                                          │
│  Repository (single source of truth)            │
│  sealed ReserveResult / CancelResult            │
├─────────────────────────────────────────────────┤
│  Firebase                                       │
│  Firestore (slots, reservations) · Anonymous    │
│  Auth — transactions guarantee consistency      │
└─────────────────────────────────────────────────┘
```

- **ViewModels expose a single immutable `UiState`** built by `combine`-ing independent state flows (data, in-flight operations, errors, messages) — the UI is a pure function of that state.
- **The Repository exposes cold, live-updating Flows** and suspend functions that return **sealed result types** instead of throwing, so every failure mode is handled explicitly at the call site.
- **Domain logic is pure and separated** — interval overlap (`overlaps`, `conflictsFor`, `isAvailableFor`) lives in plain Kotlin functions on the model layer, independent of Android or Firebase.

## How double-booking is prevented

The interesting engineering problem in this app is concurrency: two users tapping *Reserve* on the same slot at the same moment.

**Data model:** each slot document carries a denormalized `intervals` map (`reservationId → {userId, startTime, endTime}`) of its active bookings. That means a *single transactional read of one document* is enough to decide availability — no queries inside the transaction, no composite indexes.

**Reservation flow (inside one Firestore transaction):**

1. Read the slot document.
2. Drop intervals that have already ended (documents self-clean and never grow unbounded).
3. Check the requested window against every live interval using **half-open interval overlap** (`[start, end)`), so back-to-back bookings — one ending exactly when the next starts — are allowed.
4. If clear, atomically write the updated interval map **and** the reservation document in one commit.

Firestore's optimistic concurrency does the rest: if two clients race, the loser's transaction re-runs against the winner's committed data, sees the new interval, and fails with a typed `Conflict` result — surfaced to the user as *"Slot 7 was just booked for an overlapping time."* Overlapping reservations can never be created, without any server-side code.

Cancellation is symmetric: one transaction verifies ownership, then removes the reservation document and its interval entry on the slot atomically.

## Firestore schema

```
slots/{slotId}
  name: "Slot 7"
  intervals: {
    {reservationId}: { userId, startTime, endTime }
  }

reservations/{reservationId}
  userId, slotId, slotName, startTime, endTime, createdAt (server timestamp)
```

The slot pool (20 slots) is seeded idempotently on first launch via a batched write.

## Project structure

```
app/src/main/java/.../parkspotassignment/
├── data/
│   ├── model/          # Slot, Reservation, BookedInterval + pure interval logic
│   └── Repository.kt   # Firestore access, transactions, live Flows
├── di/                 # Hilt module (Auth, Firestore)
├── presentation/       # Compose screens, ViewModels, UiState
└── ui/theme/           # Material 3 theming
```
