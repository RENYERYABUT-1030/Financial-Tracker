# Ledger - Personal Finance Edition

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

On macOS, Ledger.app is also available. To compile changes from source, use a JDK
and the commands above (replace the Windows path separator with / on Unix).
 
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

Passwords are stored as salted PBKDF2-HMAC-SHA256 hashes. Existing account
passwords are migrated automatically when opening the new login screen.
Your existing login still works. New accounts require at least eight
characters. Financial records and backups are local plaintext files, not
encrypted storage. Use a personal account rather than the demo accounts and
do not reuse banking passwords. Older app versions cannot read hashed logins.

## Features

| Tab | Function |
| --- | --- |
| Dashboard | Monthly totals, daily budget, category warnings, spending insights, upcoming-expense projection |
| Expenses | Add, edit, delete, undo, searching, merge sort, filters and upcoming/recurring expenses |
| Savings Goals | Set target and deadline, add money from available monthly balance, get daily savings advice |
| Allowance | Set allowance, extra income, category budgets, optional positive-balance rollover |
| Reports | Monthly charts, annual spending summary, CSV export |

Under Expenses, select **Filters / history...** and leave the month blank
to see expenses from all months. Use **Savings advice** after selecting a goal.
The Reports tab includes **Export CSV...** (opens in Excel or a text editor).

New expense dates default to the computer's current local date. The date
spinner moves forward or backward one day at a time, but cannot go before
today; future dates are allowed. The minimum automatically advances as
days pass. Existing historical expenses keep their original date.

Future expenses are scheduled, not deducted until their due date. Fill in
the expense form and select **Repeat monthly...** to schedule a monthly bill.
The app records due expenses automatically while open and catches up when
you reopen it. This records expected expenses locally; it does not make
actual payments. Use **Upcoming...** to cancel future schedules. Monthly
recurrence uses the original day or the last day of shorter months.

Undo reverses the last ordinary expense addition, edit or deletion,
including one-time scheduled expenses. Recurring rule cancellations are
not undoable; create a new rule if needed.

By default each month is independent. To carry positive unused funds into
the next month, enable **Carry unused money into next month** in Allowance.
Past expense records and past monthly totals stay
available. Savings deposits are recorded against the current month and
deducted from available money. Goals with saved funds cannot be deleted to
protect their history. Changing a goal name does not rewrite historical
deposit descriptions.

Use **Backup / restore...** in the header to save or restore a snapshot of
the current account. Restoring replaces the current records only after
confirmation and creates a safety copy first. Older data snapshots are
supported. Do not share backups publicly.

The Dashboard's **Show month** selector controls all four summary cards and
the category breakdown together. It initially opens the current month if
there are records, or the latest saved month containing activity. Adding or
editing an expense switches to its month and reveals the saved record in the
Expenses table. Saving an allowance or adding extra income switches the
Dashboard back to the current month. Press **Save allowance** (or Enter) to
record the amount; typing alone is not a saved transaction. Historical months
show a dash for the daily budget because no days remain.

```text
Remaining balance = allowance + extra income + optional rollover - expenses - savings deposits
Daily budget = max(remaining balance, 0) / remaining days (including today)
```

## Data structures and algorithms

| Data structure | Purpose |
| --- | --- |
| Array | Default categories and 12 monthly spending totals |
| ArrayList | Expenses, goals, categories, accounts, dated income and savings entries |
| HashMap | Allowances, category budgets, single-traversal spending aggregation |
| Stack | Undo added, edited, and deleted expenses, last in first out |
| PriorityQueue | Upcoming expenses processed by earliest due date |
| Merge sort | Stable expense ordering, O(n log n) comparisons |
| Bubble sort | Monthly report selection and category charts |
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
calculations, savings, budgets, undo, sorting, search, validation and CSV.
It uses a temporary directory rather than real user files.
