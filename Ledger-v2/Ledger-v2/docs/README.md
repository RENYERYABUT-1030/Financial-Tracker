# Ledger - Student Financial Tracker

Ledger is an offline Java Swing application for a Data Structures class project.
It manages monthly allowances, additional income, expenses, savings goals and
category budgets without a database.

## Run on Windows

1. Install a Java JDK (8 or newer) and add its bin directory to PATH.
2. Open the Ledger application directory (the parent of the docs folder),
   then double-click `run.bat` next to Ledger.jar.
3. Alternatively, open Command Prompt in that directory and run:

```bat
if not exist out mkdir out
javac -encoding UTF-8 -d out src\FinancialTracker.java
java -cp out FinancialTracker
```

To launch the packaged application on Windows, macOS or Linux with Java installed:

```sh
java -jar Ledger.jar
```

To compile changes from source, use a JDK and the commands above (replace the
Windows path separator with / on Unix).
 
## Login and storage

Existing demo accounts: student / 1234 and admin / admin. You can register a
separate account from the login screen.

Accounts remain in `ledger-users.txt` in your operating system's home folder.
Financial data is saved automatically to a separate UTF-8 properties file for
each account inside `LedgerData` in the same home folder. Filenames are encoded
from the account name; the application restores the matching file upon login.
No MySQL, XAMPP or internet connection is required.

Every successful modification (including undo) writes a complete snapshot to
a temporary file before replacing the saved file. If a write fails, Ledger
shows an error and retries before closing or logging out. Keep only one open
instance per account to avoid simultaneous edits. Use the app's normal login
and do not edit the save files directly.

Passwords are still plain text as in the original classroom project; local
files are not encrypted. Do not use real banking passwords or treat Ledger as
a secure financial vault.

## Features

| Tab | Function |
| --- | --- |
| Dashboard | Monthly totals, remaining daily budget, category warnings, reset all financial data with backup |
| Expenses | Add, clear input, edit, delete, undo, search, sort, clear filters, filter by month/date range/category |
| Savings Goals | Set target and deadline, clear inputs, edit/delete goals, reset progress, add money, get daily savings advice |
| Allowance | Set/clear/reset the current month's allowance, add/edit/delete income, set/edit/delete/reset category plans |
| Reports | Select a month, review income/expenses/savings/balance, view spending charts, export that month's CSV |

Under Expenses, select **Filters / history...** and leave the month blank
to see expenses from all months. Use **Savings advice** after selecting a goal.
The Reports tab includes **Export CSV...** (opens in Excel or a text editor).

The month filter and monthly selectors use **MM/YYYY**, while expense dates,
date-range filters, savings deadlines, income history and daily report labels
use **MM/DD/YYYY**. Date-range filters allow past dates, and blank dates are
optional. The financial data files and CSV exports retain ISO date values for
compatibility. The category chart assigns a stable color to each category,
including custom categories, regardless of the spending order.

Savings deadlines use **MM/DD/YYYY** (for example, `05/15/2028`). The input
requires a valid calendar date on or after today. Savings advice uses the
exact deadline instead of the end of the month. Older month-only deadlines
are automatically interpreted as the last day of their saved month, matching
the previous savings-advice calculation. Existing overdue goals can retain
their original deadline when edited.

New expense dates default to the computer's current local date. The date
spinner moves forward or backward one day at a time, but cannot go before
today; future dates are allowed. The minimum automatically advances as
days pass. Existing historical expenses keep their original date.

Each month is independent; unused allowance is not automatically carried
forward to the next month. Past expense records and past monthly totals stay
available. Savings deposits are recorded against the current month and
deducted from available money. Resetting a goal's progress or deleting a goal
returns its saved funds to the current month's available balance. The original
deposit history remains, and a dated savings-return entry records the refund.
Changing a goal name does not rewrite historical deposit descriptions.

## Clear, edit, delete and reset

- **Clear** or **Clear input** only empties the relevant form without modifying
  saved records. In Expenses, Clear also restores today's date. **Clear filters**
  restores the current-month expense view and default sort.
- **Edit** changes a selected saved record; **Delete** removes it after a
  confirmation. Deleted expenses can still be restored with **Undo delete**.
- In Savings Goals, select a goal and choose **Reset progress...** to return
  its savings and set its saved progress to zero while keeping the goal.
- In Allowance, **Reset allowance...** sets only the current month's allowance
  to zero. **Manage income...** opens saved extra income records from all months;
  select one to edit or delete it. Editing keeps the original date.
- Select a budget category row and change the amount with **Set plan**.
  **Delete selected plan...** removes that category's current-month plan;
  **Reset monthly plan...** removes all current-month category plans, not expenses.
- **Reset all data...** on Dashboard requires typing `RESET`. It clears all months
  of financial records for the signed-in account only, including undo history and
  custom categories. Login credentials and other users are untouched. A backup is
  saved first under `LedgerData/backups`, and its path is shown after a successful
  reset. To restore, close Ledger and copy that backup over the matching account's
  original properties file (keep another copy of both files first).

This revision is based on the earlier Ledger v2 (before the Personal Finance
Edition). It reads existing v2 files, including files without savings-return
records. Keep backup copies and use the revised app after updating; older app
versions do not understand the savings-return records. The revised app can also
import files from the earlier Personal Finance Edition after asking for confirmation.
It makes an exact backup in `LedgerData/backups` before replacing the file.
Scheduled expenses become regular dated expenses; recurring schedules and
automatic monthly rollover are not used in this edition. Deleted expenses can
still be restored, and the original edition's full undo history and settings
remain in the backup. Existing password hashes are recognized. If a file cannot
be opened, the error message now identifies the reason without resetting records.

The Dashboard's **Show month** selector controls all four summary cards and
the category breakdown together. It initially opens the current month if
there are records, or the latest saved month containing activity. Adding or
editing an expense switches to its month and reveals the saved record in the
Expenses table. Saving an allowance or adding extra income switches the
Dashboard back to the current month. Press **Save allowance** (or Enter) to
record the amount; typing alone is not a saved transaction. Historical months
show a dash for the daily budget because no days remain.

```text
Remaining balance = allowance + extra income - expenses - savings deposits + savings returned
Daily budget = max(remaining balance, 0) / remaining days (including today)
```

## Data structures and algorithms

| Data structure | Purpose |
| --- | --- |
| Array | Default expense categories |
| ArrayList | Expenses, goals, categories, accounts, dated income and savings entries |
| HashMap | Allowances and category budgets by month |
| Stack | Undo deleted expenses, last in first out |
| Bubble sort | Expense ordering, monthly report selection and category charts |
| Linear search / traversal | Login, expense search, filters, totals |

The data structures operate in memory. File handling preserves them after
closing the application. The existing five-tab Swing interface is retained,
with small controls added for the new functions.

## Tests

In the directory containing Ledger.jar:

```sh
mkdir -p out
javac -encoding UTF-8 -d out src/FinancialTracker.java tests/LedgerDataTest.java
java -cp out LedgerDataTest
```

The test exercises persistence and restoration, username isolation, monthly
calculations, savings returns, income edits/deletion, budget/allowance reset,
full reset with backup, undo, sorting, search, validation and CSV.
It uses a temporary directory rather than real user files.
