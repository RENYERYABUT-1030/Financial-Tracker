import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;

/** Run with java -cp out LedgerDataTest. No database or GUI is required. */
public class LedgerDataTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void equal(double expected, double actual, String message) {
        check(Math.abs(expected - actual) < 0.00001, message + ": " + actual);
    }
    public static void main(String[] args) throws Exception {
        Path home = Files.createTempDirectory("ledger-test-");
        Path student = FinancialTracker.dataPath(home, "student");
        Path admin = FinancialTracker.dataPath(home, "admin");
        check(!student.equals(admin), "Separate user files");
        check(student.equals(FinancialTracker.dataPath(home, "STUDENT")), "Case-insensitive login");
        check(FinancialTracker.dataPath(home, "../unsafe").getParent().equals(home.resolve("LedgerData")),
                "File name cannot escape data directory");
        YearMonth month = YearMonth.now();
        YearMonth prior = month.minusMonths(1);
        LocalDate today = LocalDate.now();
        LocalDate monthEnd = LocalDate.of(2026, 11, 30);
        javax.swing.SpinnerDateModel dates = FinancialTracker.expenseDateModel(monthEnd);
        check(dates.getValue().equals(FinancialTracker.spinnerDate(monthEnd)), "Defaults to present date");
        check(dates.getPreviousValue() == null, "Cannot step into old dates");
        dates.setCalendarField(java.util.Calendar.MONTH);
        check(dates.getNextValue().equals(FinancialTracker.spinnerDate(monthEnd.plusDays(1))), "Continues one day into next month");
        check(!FinancialTracker.allowedExpenseDate(today.minusDays(1), today), "Reject manually typed past date");
        check(FinancialTracker.allowedExpenseDate(today, today), "Allow today");
        check(FinancialTracker.allowedExpenseDate(today.plusDays(1), today), "Allow tomorrow");
        FinancialTracker.LedgerData data = new FinancialTracker.LedgerData();
        data.allowances.put(month, 2000.0);
        data.allowances.put(prior, 900.0);
        data.incomes.add(new FinancialTracker.MoneyEntry(today, "Part-time, work", 500));
        data.expenses.add(new FinancialTracker.Expense(today, "Lunch, \"rice\" ₱", "Food", 150));
        data.expenses.add(new FinancialTracker.Expense(today, "Jeep", "Transport", 50));
        data.expenses.add(new FinancialTracker.Expense(prior.atDay(1), "Old lunch", "Food", 100));
        data.categories.add("School supplies");
        data.plan(month).put("Food", 300.0);
        data.plan(prior).put("Food", 200.0);
        data.goals.add(new FinancialTracker.Goal("Laptop", 5000, 250, month.plusMonths(4).toString()));
        data.deposits.add(new FinancialTracker.MoneyEntry(today, "Laptop", 250));
        data.undoHistory.push(new FinancialTracker.ExpenseChange(new FinancialTracker.Expense(today, "Undo me", "Other", 15), null));
        data.save(student);

        FinancialTracker.LedgerData restored = new FinancialTracker.LedgerData();
        restored.load(student);
        equal(2100 - 50, restored.balance(month), "This month's balance");
        equal(800, restored.balance(prior), "Previous month balance");
        FinancialTracker.DashboardTotals cards = restored.dashboardTotals(month, today);
        equal(2000, cards.allowance, "Dashboard monthly allowance");
        equal(200, cards.spent, "Dashboard total spent");
        equal(2050, cards.balance, "Dashboard remaining money");
        int remainingDays = month.lengthOfMonth() - today.getDayOfMonth() + 1;
        equal(FinancialTracker.LedgerData.cents(2050.0 / remainingDays), cards.dailyBudget, "Dashboard daily budget");
        check(cards.expenseCount == 2, "Dashboard count after restart");
        check(restored.dashboardTotals(prior, today).remainingDays == 0, "Past month has no remaining days");
        FinancialTracker.LedgerData historic = new FinancialTracker.LedgerData();
        historic.expenses.add(new FinancialTracker.Expense(prior.atDay(1), "Old entry", "Food", 75));
        historic.allowances.put(prior, 500.0);
        check(historic.initialDashboardMonth(month).equals(prior), "Dashboard finds existing historical data");
        equal(425, historic.dashboardTotals(prior, today).balance, "Historic dashboard balance");
        historic.allowances.put(month, 1000.0);
        check(historic.initialDashboardMonth(month).equals(month), "Current activity takes priority");
        check(restored.dashboardTotals(month.plusMonths(1), today).remainingDays == month.plusMonths(1).lengthOfMonth(), "Future month day calculation");
        equal(150, restored.spent(month, "Food"), "Category total");
        equal(250, restored.savings(month), "Savings deduction");
        equal(500, restored.extraIncome(month), "Additional income");
        equal(300, restored.plan(month).get("Food"), "Current plan");
        equal(200, restored.plan(prior).get("Food"), "Past plan");
        check(restored.expenses.size() == 3 && restored.expenseCount(month) == 2, "History saved");
        check(restored.goals.size() == 1 && restored.goals.get(0).saved == 250, "Goal saved");
        check(restored.categories.contains("School supplies"), "Custom category saved");
        check(restored.undoHistory.peek().before.description.equals("Undo me"), "Undo stack saved");
        check(restored.expenses.get(0).description.equals("Lunch, \"rice\" ₱"), "Unicode and quoting round trip");
        FinancialTracker.LedgerData other = new FinancialTracker.LedgerData();
        other.load(admin);
        equal(0, other.balance(month), "Other account starts empty");
        check(other.expenses.isEmpty() && other.goals.isEmpty(), "No cross-account data");
        restored.undoExpense(today);
        restored.save(student);
        FinancialTracker.LedgerData afterUndo = new FinancialTracker.LedgerData();
        afterUndo.load(student);
        check(afterUndo.undoHistory.isEmpty() && afterUndo.expenses.size() == 4, "Undo persists");
        equal(2035, afterUndo.balance(month), "Balance after undo");

        ArrayList<FinancialTracker.Expense> list = new ArrayList<FinancialTracker.Expense>(afterUndo.expenses);
        FinancialTracker.sortExpenses(list, 2);
        check(list.get(0).amount == 150 && list.get(list.size() - 1).amount == 15, "Merge sort descending");
        FinancialTracker.sortExpenses(list, 1);
        check(list.get(0).date.equals(prior.atDay(1)), "Merge sort by date");
        check(FinancialTracker.matches(list.get(0), "old") &&
                FinancialTracker.matches(list.get(0), "100.00"), "Linear search");
        for (String invalid : Arrays.asList("NaN", "Infinity", "-1", "1,2", "0.001", "abc"))
            equal(0, FinancialTracker.readAmount(invalid), "Invalid amount " + invalid);
        equal(1250.50, FinancialTracker.readAmount("1,250.50"), "Valid formatted amount");
        Path csv = home.resolve("report.csv");
        restored.exportCsv(csv, month);
        String report = new String(Files.readAllBytes(csv), StandardCharsets.UTF_8);
        check(report.contains("\"Lunch, \"\"rice\"\" ₱\"") && report.contains("2035.00")
                && !report.contains("Old lunch"), "CSV selection, totals and escaping");
        equal(150, afterUndo.categoryTotals(month).get("Food"), "HashMap category totals");
        equal(215, afterUndo.yearlySpending(month.getYear())[month.getMonthValue() - 1], "12-month array");
        afterUndo.rolloverEnabled = true;
        equal(800, afterUndo.rollover(month), "Positive money carries forward");
        equal(2835, afterUndo.balance(month), "Balance includes rollover");
        afterUndo.rolloverEnabled = false;
        FinancialTracker.LedgerData queue = new FinancialTracker.LedgerData();
        LocalDate jan31 = LocalDate.of(2027, 1, 31);
        FinancialTracker.Expense future = new FinancialTracker.Expense(jan31.plusDays(1), "Bus", "Transport", 40);
        queue.addExpense(future, jan31);
        check(queue.expenses.isEmpty() && queue.scheduled.size() == 1, "Future expense not counted early");
        FinancialTracker.Expense edited = future.copy();
        edited.amount = 60;
        queue.editExpense(future, edited, jan31);
        queue.undoExpense(jan31);
        equal(40, queue.scheduled.peek().amount, "Undo edit");
        check(queue.postDue(jan31.plusDays(1)) == 1, "Post due expense");
        queue.undoExpense(jan31.plusDays(1));
        check(queue.expenses.isEmpty(), "Undo add even after schedule posts");
        queue.scheduleMonthly(new FinancialTracker.Expense(jan31, "Subscription", "Other", 100), jan31);
        check(queue.scheduled.peek().date.equals(LocalDate.of(2027, 2, 28)), "Monthly end-of-month recurrence");
        check(queue.postDue(LocalDate.of(2027, 3, 31)) == 2, "Catch up on missed recurring entries");
        check(queue.scheduled.peek().date.equals(LocalDate.of(2027, 4, 30)) && queue.expenses.size() == 3, "Monthly anchor restored");
        check(queue.postDue(LocalDate.of(2027, 3, 31)) == 0, "No double posting");
        queue.owner = "student";
        Path backup = home.resolve("backup.properties");
        queue.save(backup);
        FinancialTracker.LedgerData snapshot = new FinancialTracker.LedgerData();
        snapshot.owner = "student";
        snapshot.load(backup);
        check(snapshot.scheduled.size() == 1 && snapshot.expenses.size() == 3, "Schedule saved in backup");
        java.util.Properties legacy = new java.util.Properties();
        try (java.io.Reader reader = Files.newBufferedReader(backup, StandardCharsets.UTF_8)) { legacy.load(reader); }
        legacy.setProperty("version", "1");
        legacy.setProperty("undo.count", "0");
        legacy.remove("owner");
        Path oldFile = home.resolve("old-version.properties");
        try (java.io.Writer writer = Files.newBufferedWriter(oldFile, StandardCharsets.UTF_8)) { legacy.store(writer, "Migration fixture"); }
        FinancialTracker.LedgerData migrated = new FinancialTracker.LedgerData();
        migrated.load(oldFile);
        check(migrated.expenses.size() + migrated.scheduled.size() == 3, "Legacy records survive migration");
        FinancialTracker.LedgerData wrong = new FinancialTracker.LedgerData();
        wrong.owner = "admin";
        try { wrong.load(backup); throw new AssertionError("Cross-account backup accepted"); }
        catch (java.io.IOException expected) { }
        FinancialTracker.User user = new FinancialTracker.User("test", "personalPassword");
        FinancialTracker.User user2 = new FinancialTracker.User("test", "personalPassword");
        check(!user.passwordHash.equals(user2.passwordHash), "Unique salts");
        check(!user.passwordHash.contains("personalPassword"), "Passwords not stored plaintext");
        check(FinancialTracker.User.fromStored("test", user.passwordHash).verify("personalPassword"), "Hash verification after load");
        check(!user.verify("wrongPassword"), "Wrong password rejected");
        System.out.println("PASS: dashboard, persistence, month selection, dates, account isolation, totals, savings, income, budgets, undo, merge sort, search, validation, CSV, HashMap, yearly array, rollover, schedules, backups and password hashing.");
    }
}
