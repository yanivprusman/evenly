# Evenly API contract

The Next.js backend in this repo is the single source of truth for the ledger. The Android
app (`mobile/`) and any web page are clients of this API. Nothing here is capped: there is no
daily expense limit, no paged-out history, no "Pro" gate.

## Conventions

- **Auth**: every `/api/*` route (except `/api/join/*`) requires `Authorization: Bearer <token>`.
  A token identifies one user (`users.token`). 401 on a missing/unknown token.
- **Money is integer minor units** (`amount` = agorot/cents) plus an ISO-4217 `currency`
  string. Never floats. `12.50 ₪` is `{ "amount": 1250, "currency": "ILS" }`.
- **Dates** are `YYYY-MM-DD` (the day the expense happened). Timestamps are ISO-8601 UTC.
- **Ids** are integers.
- Errors: non-2xx with `{ "error": "human readable message" }`.
- A **balance** is always from the point of view of the caller: positive = they owe *you*,
  negative = *you* owe them. Balances are per currency, never summed across currencies.

## Types

```ts
User     { id, name, email|null, phone|null, color: "#RRGGBB", registered: boolean }
Money    { amount: int, currency: string }
Share    { userId, paid: int, owed: int, input: number|null }
            // input = the value the user typed for the split type:
            //   percent → 33.33, shares → 2, adjustment → +/- minor units, exact → minor units
Expense  { id, groupId|null, description, cost: int, currency, date, category, notes|null,
           isPayment: boolean, splitType: "equal"|"exact"|"percent"|"shares"|"adjustment",
           repeat: "none"|"weekly"|"biweekly"|"monthly"|"yearly",
           shares: Share[], receiptUrl|null, createdBy, createdAt, updatedBy|null,
           updatedAt|null, deletedAt|null, commentCount: int }
Group    { id, name, type: "home"|"trip"|"couple"|"other", defaultCurrency,
           simplifyDebts: boolean, members: User[], createdAt, archived: boolean }
Debt     { from: userId, to: userId, amount: int, currency }   // from owes to
Activity { id, actor: User, type, groupId|null, expenseId|null, text, createdAt,
           amountForMe: Money|null }   // signed effect on the caller, if any
Comment  { id, expenseId, user: User, body, createdAt }
```

`category` is a dotted key: `general`, `food.groceries`, `food.dining`, `food.liquor`,
`home.rent`, `home.mortgage`, `home.household`, `home.furniture`, `home.maintenance`,
`home.electronics`, `home.pets`, `home.services`, `utilities.electricity`, `utilities.water`,
`utilities.gas`, `utilities.internet`, `utilities.trash`, `utilities.cleaning`,
`transport.car`, `transport.fuel`, `transport.parking`, `transport.taxi`, `transport.bus`,
`transport.plane`, `transport.hotel`, `transport.bicycle`, `life.childcare`, `life.clothing`,
`life.education`, `life.gifts`, `life.insurance`, `life.medical`, `life.taxes`,
`fun.games`, `fun.movies`, `fun.music`, `fun.sports`, `fun.other`.

Invariant enforced by the server on every write: `Σ shares.paid == cost` and
`Σ shares.owed == cost`, all non-negative, every share user is a group member (group expense)
or the caller's friend (non-group expense), and the caller is one of the shares.
A payment (`isPayment: true`, "settle up") has exactly two shares: payer `paid=cost, owed=0`
and receiver `paid=0, owed=cost`.

## Endpoints

| Method & path | Body / query | Returns |
|---|---|---|
| `GET /api/me` | | `User & { defaultCurrency }` |
| `PATCH /api/me` | `{ name?, email?, phone?, defaultCurrency? }` | same |
| `GET /api/dashboard` | | `{ me, total: Money[], groups: GroupSummary[], friends: FriendSummary[] }` |
| `GET /api/groups` | | `GroupSummary[]` |
| `POST /api/groups` | `{ name, type, memberIds: int[], defaultCurrency?, simplifyDebts? }` | `Group` |
| `GET /api/groups/:id` | | `{ group, balances: {userId, net: Money[]}[], debts: Debt[], myNet: Money[], expenses: Expense[] }` |
| `PATCH /api/groups/:id` | `{ name?, type?, defaultCurrency?, simplifyDebts?, archived? }` | `Group` |
| `DELETE /api/groups/:id` | | `{ ok }` (only when every balance is zero) |
| `POST /api/groups/:id/members` | `{ userId }` or `{ name, email?, phone? }` (creates a friend) | `Group` |
| `DELETE /api/groups/:id/members/:userId` | | `Group` (refused while that member has a balance) |
| `GET /api/friends` | | `FriendSummary[]` |
| `POST /api/friends` | `{ name, email?, phone? }` | `User` (a placeholder user unless the email/phone matches one) |
| `GET /api/friends/:id` | | `{ friend: User, net: Money[], byGroup: {groupId|null, name, net: Money[]}[], expenses: Expense[] }` |
| `DELETE /api/friends/:id` | | `{ ok }` (only when settled) |
| `GET /api/expenses/:id` | | `Expense & { comments: Comment[], history: Activity[] }` |
| `POST /api/expenses` | `Expense` fields minus ids/timestamps | `Expense` |
| `PATCH /api/expenses/:id` | same fields | `Expense` |
| `DELETE /api/expenses/:id` | | `{ ok }` (soft delete; shows in activity, restorable) |
| `POST /api/expenses/:id/restore` | | `Expense` |
| `POST /api/expenses/:id/comments` | `{ body }` | `Comment` |
| `POST /api/expenses/:id/receipt` | multipart `file` | `Expense` |
| `GET /api/activity` | `?before=<activityId>&limit=50` | `Activity[]` |
| `GET /api/search` | `?q=` | `Expense[]` (description, notes, comments) |
| `GET /api/stats` | `?groupId=&from=&to=&currency=&scope=group` | `{ currency, byCategory: {category, amount}[], byMonth: {month:"YYYY-MM", amount}[], total }` — the caller's owed share; with `scope=group` (needs `groupId`) the whole group's spending |
| `GET /api/export.csv` | `?groupId=` or `?friendId=` | CSV |
| `GET /api/currencies` | | `{ code, symbol, name }[]` |

```ts
GroupSummary  { group: Group, myNet: Money[], debts: Debt[] /* involving me */, lastActivityAt }
FriendSummary { friend: User, net: Money[], byGroup: {groupId|null, name, net: Money[]}[] }
```

Receipts: `receiptUrl` is a path relative to the API base (`/r/<32-hex>.jpg`) and is served
**without** auth — the random name is the capability, so image loaders need no header.

Recurring expenses: when `repeat != "none"`, the server creates the next occurrence (a copy
with the next date) the first time any request arrives on or after that date.
