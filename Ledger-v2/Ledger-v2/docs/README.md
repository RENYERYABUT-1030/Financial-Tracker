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

Passwords are still plain text as in the original classroom project; local
files are not encrypted. Do not use real banking passwords or treat Ledger as
a secure financial vault.

## Features

| Tab | Function |
| --- | --- |
| Dashboard | Current-month totals, remaining daily budget, category warnings |
| Expenses | Add, edit, delete, undo, search by description/category/date/amount, sort, filter by month/date range/category |
| Savings Goals | Set target and deadline, add money from available monthly balance, get daily savings advice |
| Allowance | Set the current month's allowance, add extra income, plan budgets per category |
| Reports | Select a month, review income/expenses/savings/balance, view spending charts, export that month's CSV |

Under Expenses, select **Filters / history...** and leave the month blank
to see expenses from all months. Use **Savings advice** after selecting a goal.
The Reports tab includes **Export CSV...** (opens in Excel or a text editor).

New expense dates default to the computer's current local date. The date
spinner moves forward or backward one day at a time, but cannot go before
today; future dates are allowed. The minimum automatically advances as
days pass. Existing historical expenses keep their original date.

Each month is independent; unused allowance is not automatically carried
forward to the next month. Past expense records and past monthly totals stay
available. Savings deposits are recorded against the current month and
deducted from available money. Goals with saved funds cannot be deleted to
protect their history. Changing a goal name does not rewrite historical
deposit descriptions.

```text
Remaining balance = allowance + extra income - expenses - savings deposits
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
calculations, savings, budgets, undo, sorting, search, validation and CSV.
It uses a temporary directory rather than real user files.
