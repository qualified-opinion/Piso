# Batch 19: look and feel (U16)

Jan asked on 2026-11-09 for a review of Piso's screens and for the UI to be improved. This pack lists what the review found and what this batch changes. It has no database change, and it doesn't change how any amount is worked out.

- **Repo:** `qualified-opinion/Piso`
- **Branch:** `claude/project-thread-29oc2c`, from `main` at `71d567f` (B18).

## What the review found
1. **Dark mode was hard to read.** Most screens used fixed light-mode colours. In Dark, green amounts were about 2.5:1 against the card (4.5:1 is the usual minimum for text), and some light-green boxes stayed pale with pale text on them.
2. **Text field borders were nearly invisible.** The border colour was 1.3:1 against white. Archived categories and a few labels used the same colour for their text, so they were hard to read too.
3. **Purple tints.** The theme left out several Material colour roles. Those roles fell back to Material's purple defaults, which tinted the bottom bar's highlight, dialogs, drop-down menus, the date picker and switches lavender.
4. **Red and amber were a little faint.** Spending red was 4.2:1 and the budget-warning amber 3.2:1 on white.
5. **Blank band at the top of most tabs.** Each tab has its own top bar inside the main screen, and both added the status bar space, so Activity, Accounts, Goals and the More pages started lower than Home.
6. **Long names could squeeze out the amount** on Home's Accounts and Debts cards, and Budget or Goal rows.
7. **Home's three month figures could wrap** onto two lines once an amount reached six figures.
8. **Add income and Add expense were far down Home**, below the spending breakdown, although they are the most used buttons.
9. **Activity** repeated the full date on every row, had two buttons that both added a transaction, and showed transfers and adjustments with a receipt icon.
10. **Mixed capitals** in headings ("Net Worth", "Budget Preview", "Danger Zone" next to "Spending by category"), and "View all" links that said different things.
11. **More** said "Version 1.0" whatever was installed.

## Scope
- **Colours (1 to 4):**
  - Every Material colour role is set, in Piso's greens and warm greys, for Light and Dark.
  - The field border is darker (4.7:1). Hairline dividers and card borders keep the soft colour.
  - Money colours have a light and a dark version: green, red and amber all reach at least 4.5:1 in both themes. Text on a green or red button follows the theme too.
  - `ui/theme/Color.kt` keeps the old names (`TealPrimary`, `IncomeGreen`, `ExpenseRed` and so on), but they now follow the theme, so the screens didn't need rewriting.
- **Top band (5):** the main screen hands its status-bar and navigation-bar space to the tabs once (`consumeWindowInsets` in `MainActivity`).
- **Long names (6):** names are cut short with "…" so the amount always shows in full.
- **Home (7, 8):**
  - "This month" reads like a small income statement: Income, Spent, a line, then Net.
  - Add income and Add expense sit just under Net worth.
  - Adjustments read "Balance adjustment".
- **Activity (9):**
  - The list is grouped by day under headings: "Today", "Yesterday", "Tue, Nov 3", with the year for another year.
  - Rows no longer repeat the date.
  - One add button, the round one at the bottom.
  - Chevron arrows for the month.
  - Transfers have a two-way arrow icon, and adjustments a slider icon.
- **Wording (10, 11):**
  - Headings and dialog titles use sentence case.
  - Home's links all say "View all".
  - More shows the installed version.

## Not in this batch
- The layout of the dialogs, Budgets, Goals, Debts and Invest screens, apart from the colours above.
- Larger text in Settings' small buttons. They are 12 to 13 sp, and bigger text may wrap.

## Tests
- `ThemeContrastTest` checks that green, red and amber reach 4.5:1 on cards and on the page in both themes, that text on green and red buttons does too, and that the field border reaches 3:1.
- `DateUtilTest` checks the Activity day headings.
- The existing tests still pass. No test looked up a heading that changed.

## Phone checks (once released)
- In Settings, switch to Dark: amounts are easy to read, the Home "set aside for goals" note is readable, and there is no pale box.
- In Light: text field borders are visible in Add transaction. The bottom bar's highlight, dialogs, menus and the date picker are green or grey, not lavender.
- Activity, Accounts and Goals start right under the status bar, at the same height as Home.
- On Home, Add income and Add expense are just under Net worth, and This month shows Income, Spent and Net on separate lines.
- In Activity, entries are grouped under Today, Yesterday and dates, and there is only one add button.
- An account with a very long name still shows its balance in full on Home.
- More shows the version you installed, for example 1.0.8.
