# Batch 15: credit card purchases (U12, D6)

Jan asked on 2026-11-06 to keep working through "Later ideas", starting with credit card purchases: buying on a card counts as spending and raises what's owed on the card. It needs a database change (version 6).

- **Repo:** `qualified-opinion/Piso`
- **Branch:** `claude/project-thread-vkg33n`, restarted from `main` at `628b8eb` (B14).
- **D6, when a purchase counts as spending.** Settled 2026-11-06: Jan chose "when bought, per card switch". The other option was to keep counting spending only when the card is paid.

## Scope
- A Credit Card debt gets an "I record purchases on this card" switch in its Add or Edit dialog, off by default. Existing cards stay off, so nothing already recorded changes.
- A card with the switch on shows "Add purchase" on the Debts page. A purchase has an amount, an expense category, an optional subcategory, a date and a note. It's saved as an expense with no account and `debtFlow = "CHARGE"`. It counts as spending in its category and month (Home, Budgets, Budget history, Year overview) and adds to what's owed on the card. No account changes.
- Paying a card with the switch on saves a one-sided transfer ("Card payment") from the account, so the payment isn't counted as spending a second time. Paying a card with the switch off, or any other debt, stays a "Debt" expense, as before.
- Deleting a purchase takes it off what's owed, never below zero. Deleting a payment adds it back and returns the money to the account. Editing a purchase's amount isn't capped at what's owed (a payment is).
- Net worth falls by a purchase, as the card balance rises, and doesn't change on a payment.
- The card shows "₱X owed" (or "Nothing owed") in place of "% paid", because a card balance goes up and down. The Home debts list shows "owed" in place of "of ₱original".
- Activity shows a purchase with "· Credit card" and a payment as "Card payment" with its account. Tapping either says to delete it and record it again from Debts, as for other debt entries.
- Deleting the card detaches its entries: purchases stay as spending.
- Backups and CSV: `tracksPurchases` on debts and `debtFlow` on transactions are exported and restored. Older backups read as before (no tracking, every debt entry a payment). CSV "Linked To" says "Card purchase".

## Database
Version 5 to 6 (`MIGRATION_5_6`): `debts.tracksPurchases INTEGER NOT NULL DEFAULT 0` and `transactions.debtFlow TEXT`. Schema `app/schemas/.../6.json`.

## Tests
- `DatabaseMigrationTest`: 5 to 6 keeps debts, balances and an old card payment unchanged, with the switch off and no flow; 1 to 6 across all migrations.
- `CardPurchaseTest`: a purchase is spending in its month and category and raises what's owed without touching accounts; paying a tracking card is a transfer, not spending, and leaves net worth unchanged; paying a non-tracking card is still spending, and a purchase on it is refused; deleting a purchase or payment restores the card and account, and a double delete reverts once; deleting a purchase after the card was paid never goes below zero; editing a purchase amount isn't capped; deleting the card keeps purchases as spending; backups keep the switch and the flow, and old backups default to payments; CSV marks purchases.

## Phone checks
Edit your credit card and turn on "I record purchases on this card". Add a ₱1,000 Food purchase dated last month: the card owes ₱1,000 more, no wallet changes, and last month's Food budget and the Year overview show it. Pay ₱1,000 from the wallet: the wallet drops, the card goes back down, and this month's Spent on Home doesn't change. Delete the purchase in Activity and see the card owe ₱1,000 less. A loan payment still counts as spending.

## Out of scope
Paying with the card from the Add transaction dialog, card statements and due dates, credit limit warnings, interest and fees, and recurring card purchases.
