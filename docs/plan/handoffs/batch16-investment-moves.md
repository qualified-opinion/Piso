# Batch 16: investments bought or sold from an account (U13)

Jan picked this on 2026-11-06 as the next "Later ideas" item after credit card purchases. Buying an investment takes money out of an account and selling puts it back, and neither counts as spending or income. It needs a database change (version 7) and builds on B15 (version 6).

- **Repo:** `qualified-opinion/Piso`
- **Branch:** `claude/project-thread-vkg33n`, restarted from `main` at `1c6ab40` (B15).

## Scope
- Each investment on the Invest page gets **Buy more** and **Sell** buttons. Both ask for an amount, an account, a date and an optional note.
- **Buy:** the account goes down and the investment's value goes up by the same amount. It's saved as a one-sided transfer (category "Investment", note "Bought <name>", `investmentId` set, `accountId` set).
- **Sell:** the account goes up and the value goes down by the same amount (`transferToId` set). A sale can't be more than the current value. If the investment has grown, Jan updates the value first, so deleting the sale later restores it exactly.
- **Add Investment** gets a "Paid from" choice for a new investment, which defaults to "Not from an account (just track it)". With an account picked, the value entered is the amount paid. The account goes down and a buy is saved on the chosen date.
- Neither a buy nor a sale is income or spending (Home, Budgets, Budget history, Year overview). Net worth doesn't change on a buy, or on a sale at the recorded value.
- Deleting a buy or sale in Activity puts both the account and the value back. A value never goes below zero.
- Activity shows "Investment bought" or "Investment sold" with the account. Tapping it says to delete it and record it again from Invest.
- Deleting an investment detaches its buys and sales. They stay as transfers and accounts don't change.
- Backups and CSV: `investmentId` is exported and restored, and older backups read with no link. CSV "Linked To" says "Investment".

## Database
Version 6 to 7 (`MIGRATION_6_7`): `transactions.investmentId INTEGER`. Schema `app/schemas/.../7.json`.

## Tests
- `DatabaseMigrationTest`: 6 to 7 keeps accounts, investments and transactions with no link; 1 to 7 across all migrations.
- `InvestmentMoveTest`: a buy moves money into the investment without spending and leaves net worth unchanged; a sale moves money back without income and can't exceed the value; deleting a buy or sale restores both sides; a new investment paid from an account starts at its cost; a missing investment or account, or a zero amount, is refused; deleting the investment keeps its moves as transfers; backup and CSV keep the link.

## Phone checks
On Invest, tap Buy more on a fund, pay ₱1,000 from the wallet: the wallet drops ₱1,000, the fund rises ₱1,000, net worth and this month's Spent don't change. Sell ₱500 into the wallet: both move back by ₱500 and Income doesn't change. Try to sell more than the value and see the message. Add a new investment paid from the wallet and see the wallet go down. Delete the buy in Activity and see both amounts go back.

## Out of scope
Cost basis and gain or loss, dividends, fees, and selling for more than the recorded value in one step.
