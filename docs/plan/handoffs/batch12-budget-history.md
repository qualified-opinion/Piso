# Batch 12: budget vs spending history (U7)

Jan asked on 2026-11-06 for a way to see the history of his budgets against what he actually spent, to find where he overspends. It builds on B10a (past-month budgets) and needs no database change.

- **Repo:** `qualified-opinion/Piso`
- **Branch:** `claude/project-thread-m9f8cl`, from `main` at `23daa18` (B11).
- **Why no schema change:** every `Budget` already stores its `monthKey`, and spending is worked out from transactions with the same `budgetSpending` rule the Budgets screen uses.

## Scope
- A History button in the Budgets top bar opens a "Budget history" page. Back (arrow or system back) returns to Budgets.
- The period is the last 3, 6 (default) or 12 months, ending with this month. This month is marked "(so far)".
- A summary card shows the total overspent in the period and "Went over N of M monthly budgets".
- One card per category that had a budget in at least one month of the period, worst total overspend first. Each shows total spent of total budgeted (months with a budget only), "Over in N of M", the total overspent, and a row per month with Budget, Spent and the difference in red when over.
- A month with no budget for that category shows "No budget" and never counts as over.
- Total overspent adds up only the months that went over. A month under budget doesn't offset another month's overspend.
- "Spending with no budget" lists categories with spending in the period but no budget in any of its months.
- Spending follows the Budgets rules: expenses only, so goal moves, transfers and balance adjustments don't count. Every total is rounded to centavos.

## Tests
`BudgetHistoryTest`: the period crosses the year end; per-month difference, months over and totals; an under-budget month doesn't offset overspending; a month without a budget is never over; spending exactly on budget isn't over (0.1 + 0.2 against 0.3); goal moves, transfers and income don't count; unbudgeted categories are listed, blank as "Other".

## Phone checks
Open Budgets and tap History. With last month over budget on Food, see Food at the top in red with "Over in 1 of …" and the right amount. Switch to 3 and 12 months and see the summary change. A category you never budgeted for shows under "Spending with no budget". System back returns to Budgets.

## Out of scope
Charts, export of the history, and budgets per subcategory.
