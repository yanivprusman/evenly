# Evenly

The user's own Splitwise: groups, friends, every split type, settle up, activity, comments,
receipts, search, charts and CSV export — with **no limit on how many expenses you add**, and
no Pro tier.

- **Backend** (this Next.js app, dev port 3163): the ledger API described in [`API.md`](API.md).
  Data lives in database `evenly` on the shared MySQL (3308, the NUC). Credentials in the
  gitignored `.env.local` (`DB_*`, `EVENLY_DATA_DIR` for receipts).
- **Android** (`mobile/`, KMP/Compose, package `com.automatelinux.evenly`): the phone app.
  `mobile/.env` (gitignored) holds `API_BASE_URL` and the user's `API_TOKEN`.
  Build on the desktop only: `./gradlew assembleDevDebug`.

## Ledger model

Every expense has one row per person in `expense_shares` with `paid` and `owed` in minor units
(agorot); the server rejects any write where either column does not sum to the cost. A payment
("settle up") is an expense with `is_payment = 1` and exactly two shares. Balances are always
per currency. Group debts are simplified (fewest transfers) unless the group turns that off,
in which case they are the pairwise debts each expense created.

## Imported from Splitwise (2026-09-26)

The user's Splitwise history was read off his phone's Splitwise app (UI text via
`uiautomator`, no Splitwise API) and imported with `source = 'splitwise'`:
`pruguy` (326 expenses, Oct 2024 → Sep 2026), the settled group `פרוסי` (archived), and
non-group expenses with Ayelet Guy. Verified: the imported balance equals Splitwise's own,
**you owe Ayelet ₪19,951.65**, to the agora.

## Testing

A throwaway user **Test Runner** (friends Bob Test, Carol Test, group "Trip test") exists for
write tests, so the real ledger is never used as a fixture.
