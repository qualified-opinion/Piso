# Batch 14: year overview (U11)

Jan said on 2026-11-06 to continue with the features from "Later ideas", starting with the monthly overview: a year at a glance showing income, spending and the amount saved each month, like a monthly P&L. It needs no database change.

- **Repo:** `qualified-opinion/Piso`
- **Branch:** `claude/project-thread-vkg33n`, from `main` at `e62b9da` (B13).
- **Why no schema change:** every figure is worked out from the transactions already stored, with the same rules the Home "This Month" card and the Budgets screen use.

## Scope
- A "Year overview" page, opened from a new More item and from "See the whole year" on the Home "This Month" card. Back (arrow or system back) returns to where Jan came from.
- A year selector with arrows. The forward arrow stops at this year; the back arrow stops at the year of the oldest entry.
- A summary card: amount saved in the year ("so far" for this year), the share of income kept, total income, total spent, average spent per month, and how much of the savings went into goals.
- "Month by month": one row per month with Income, Spent and Saved, newest month first, then a Total row. This year stops at this month, marked "(so far)". A month with no entries is greyed out. Saved is green when positive and red when more went out than came in; the card says in how many months that happened.
- Tapping a month opens Activity on that month with the All filter.
- "Where the money went": the five biggest spending categories in the year, with amount and share of spending. Blank categories show as "Other".
- Income and spending follow `summarizeMonth` (Home): goal moves, transfers and balance adjustments are neither. A debt payment from an account counts as spending, as on Home. Saved is income minus spending, so money put into goals is part of what was saved, not spending. Every total is rounded to centavos.

## Tests
`YearOverviewTest`: this year stops at this month and a past year has 12; month and year totals, savings rate, average and months in the red; goal moves, transfers and adjustments are neither income nor spending; 0.1 + 0.2 spent against 0.3 earned isn't a loss; no income gives no savings rate; top categories largest first with share and blank as "Other"; only five are listed; the year arrows stop at the oldest entry and this year; an empty year shows zeroes.

## Phone checks
Open More, then Year overview. See this year's months from this month back to January, with this month marked "(so far)", and the Total row matching the summary card. Check one month's Income and Spent against the Home card for that month. Tap a month and see Activity open on it. Go back a year and see all 12 months; the back arrow is disabled at the year of your oldest entry. From Home, tap "See the whole year" and system back returns to Home.

## Out of scope
Charts, export of the overview, a custom date range (for example a fiscal year), and comparing two years side by side.
