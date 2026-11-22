# Ledger - Student Financial Tracker

A simple student financial tracker built for a Data Structures class.
Uses ArrayList, HashMap, and Stack.

## Features
- Sidebar navigation
- Dashboard, Expenses, Allowance, Savings Goals, Reports
- Dark mode (checkbox)
- Show password on login
- Local file saving

## How to run
```bash
cd src
javac -encoding UTF-8 *.java
java FinancialTracker
```

Demo login: **student / 1234**

## Files
- `FinancialTracker.java` - main window
- `LoginDialog.java` - login screen
- `LedgerData.java` - data storage
- `Expense.java`, `Goal.java`, `MoneyEntry.java`, `User.java` - data models
- `DashboardTotals.java`, `Slice.java`, `ReportBar.java` - UI helpers
