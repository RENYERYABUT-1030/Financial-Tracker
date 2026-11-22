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
        LocalDate reference = LocalDate.of(2026, 11, 21);
        check("2028-05-15".equals(FinancialTracker.readDeadline("05/15/2028", reference)), "Exact deadline input");
        check("2026-11-21".equals(FinancialTracker.readDeadline("11/21/2026", reference)), "Allow today's deadline");
        check("2028-02-29".equals(FinancialTracker.readDeadline("02/29/2028", reference)), "Leap day accepted");
        for (String invalid : Arrays.asList("02/29/2027", "04/31/2027", "13/01/2027", "2028-05", "11/20/2026", "5/01/2028"))
            check(FinancialTracker.readDeadline(invalid, reference) == null, "Reject invalid or past deadline " + invalid);
        check("05/31/2028".equals(FinancialTracker.formatDeadline("2028-05")), "Legacy month deadline moves to last day");
        check(FinancialTracker.daysUntilDeadline(reference, reference.plusDays(2)) == 3, "Savings advice uses exact days including today");
        check(FinancialTracker.parseFilterMonth("11/2026").equals(YearMonth.of(2026, 11)), "Filter month MM/YYYY");
        check(FinancialTracker.parseFilterMonth("") == null && FinancialTracker.parseFilterDate("") == null, "Blank filters allowed");
        check(FinancialTracker.parseFilterDate("11/01/2025").equals(LocalDate.of(2025, 11, 1)), "Historical dates allowed in filters");
        for (String invalid : Arrays.asList("13/2026", "2026-11", "11/0000")) {
            boolean rejected = false;
            try { FinancialTracker.parseFilterMonth(invalid); } catch (RuntimeException e) { rejected = true; }
            check(rejected, "Reject invalid month " + invalid);
        }
        for (String invalid : Arrays.asList("2026-11-21", "02/29/2027", "04/31/2027")) {
            boolean rejected = false;
            try { FinancialTracker.parseFilterDate(invalid); } catch (RuntimeException e) { rejected = true; }
            check(rejected, "Reject invalid filter date " + invalid);
        }
        java.util.HashSet<java.awt.Color> colors = new java.util.HashSet<java.awt.Color>();
        for (int i = 0; i < 15; i++) check(colors.add(FinancialTracker.categoryColor(i)), "Distinct category color " + i);
        java.awt.Color sample = FinancialTracker.categoryColor(1);
        FinancialTracker.ReportBar bar = new FinancialTracker.ReportBar(50, 100, sample);
        bar.setSize(100, 14);
        java.awt.image.BufferedImage rendered = new java.awt.image.BufferedImage(100, 14, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics graphics = rendered.getGraphics();
        bar.paint(graphics); graphics.dispose();
        check(rendered.getRGB(10, 7) == sample.getRGB(), "Bar actually paints its category color");
        check(rendered.getRGB(90, 7) == bar.getBackground().getRGB(), "Unfilled portion remains background");
        check(FinancialTracker.matches(new FinancialTracker.Expense(reference, "Test", "Food", 10), "11/21/2026"), "Search by displayed date");
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
        LocalDate exactDeadline = month.plusMonths(4).atDay(15);
        data.goals.add(new FinancialTracker.Goal("Laptop", 5000, 250, exactDeadline.toString()));
        data.deposits.add(new FinancialTracker.MoneyEntry(today, "Laptop", 250));
        data.deleted.push(new FinancialTracker.Expense(today, "Undo me", "Other", 15));
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
        check(restored.goals.get(0).deadline.equals(exactDeadline.toString()), "Exact goal deadline persists");
        check(restored.categories.contains("School supplies"), "Custom category saved");
        check(restored.deleted.peek().description.equals("Undo me"), "Undo stack saved");
        check(restored.expenses.get(0).description.equals("Lunch, \"rice\" ₱"), "Unicode and quoting round trip");
        FinancialTracker.LedgerData other = new FinancialTracker.LedgerData();
        other.load(admin);
        equal(0, other.balance(month), "Other account starts empty");
        check(other.expenses.isEmpty() && other.goals.isEmpty(), "No cross-account data");
        restored.expenses.add(restored.deleted.pop());
        restored.save(student);
        FinancialTracker.LedgerData afterUndo = new FinancialTracker.LedgerData();
        afterUndo.load(student);
        check(afterUndo.deleted.isEmpty() && afterUndo.expenses.size() == 4, "Undo persists");
        equal(2035, afterUndo.balance(month), "Balance after undo");

        ArrayList<FinancialTracker.Expense> list = new ArrayList<FinancialTracker.Expense>(afterUndo.expenses);
        FinancialTracker.sortExpenses(list, 2);
        check(list.get(0).amount == 150 && list.get(list.size() - 1).amount == 15, "Bubble sort descending");
        FinancialTracker.sortExpenses(list, 1);
        check(list.get(0).date.equals(prior.atDay(1)), "Bubble sort by date");
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

        // Simulate an older saved file that predates savings returns.
        java.util.Properties legacy = new java.util.Properties();
        try (java.io.Reader reader = Files.newBufferedReader(student, StandardCharsets.UTF_8)) { legacy.load(reader); }
        legacy.remove("savingsReturn.count");
        legacy.setProperty("goal.0.deadline", month.plusMonths(4).toString());
        try (java.io.Writer writer = Files.newBufferedWriter(student, StandardCharsets.UTF_8)) { legacy.store(writer, "legacy"); }
        afterUndo.load(student);
        check(afterUndo.goals.get(0).deadline.equals(month.plusMonths(4).atEndOfMonth().toString()), "Legacy goal deadline migrated on load");
        equal(2035, afterUndo.balance(month), "Legacy file compatibility");
        FinancialTracker.Goal goal = afterUndo.goals.get(0);
        goal.name = "Renamed laptop";
        afterUndo.resetGoal(goal, today);
        equal(0, goal.saved, "Goal progress reset");
        equal(250, afterUndo.savingsReturned(month), "Savings return recorded");
        equal(250, afterUndo.savings(month), "Deposit history preserved");
        equal(2285, afterUndo.balance(month), "Returned savings restore balance");
        afterUndo.resetGoal(goal, today);
        equal(250, afterUndo.savingsReturned(month), "Cannot refund twice");
        equal(800, afterUndo.balance(prior), "Old month unaffected");
        afterUndo.goals.remove(goal);
        afterUndo.incomes.set(0, new FinancialTracker.MoneyEntry(today, "Corrected income", 300));
        equal(2085, afterUndo.balance(month), "Income edit recalculates balance");
        afterUndo.incomes.remove(0);
        equal(1785, afterUndo.balance(month), "Income delete recalculates balance");
        afterUndo.plan(month).remove("Food");
        check(afterUndo.plan(month).isEmpty() && afterUndo.plan(prior).containsKey("Food"), "Selected month plan deletion");
        afterUndo.allowances.remove(month);
        equal(-215, afterUndo.balance(month), "Allowance reset retains expenses");
        afterUndo.save(student);
        FinancialTracker.LedgerData revised = new FinancialTracker.LedgerData();
        revised.load(student);
        equal(250, revised.savingsReturned(month), "Savings return persists");
        equal(-215, revised.balance(month), "Corrected balance persists");
        revised.exportCsv(csv, month);
        check(new String(Files.readAllBytes(csv), StandardCharsets.UTF_8).contains("Savings return"), "Savings return in CSV");
        other.allowances.put(month, 1234.0);
        other.save(admin);
        Path backup = revised.backupAndReset(student);
        check(Files.isRegularFile(backup), "Pre-reset backup created");
        FinancialTracker.LedgerData empty = new FinancialTracker.LedgerData();
        empty.load(student);
        check(empty.expenses.isEmpty() && empty.goals.isEmpty() && empty.deleted.isEmpty()
                && empty.incomes.isEmpty() && empty.deposits.isEmpty() && empty.savingsReturns.isEmpty(), "All financial records reset");
        equal(0, empty.balance(month), "Reset balance");
        equal(0, empty.balance(prior), "Reset old months");
        check(empty.categories.size() == 5 && !empty.categories.contains("School supplies"), "Default categories restored");
        FinancialTracker.LedgerData backupData = new FinancialTracker.LedgerData();
        backupData.load(backup);
        equal(-215, backupData.balance(month), "Backup retains original records");
        other.load(admin);
        equal(1234, other.balance(month), "Full reset does not affect another user");

        // Recreate the actual earlier Personal Finance Edition's format (version 2).
        Path oldEdition = FinancialTracker.dataPath(home, "legacy");
        data.save(oldEdition);
        java.util.Properties old = new java.util.Properties();
        try (java.io.Reader r = Files.newBufferedReader(oldEdition, StandardCharsets.UTF_8)) { old.load(r); }
        old.setProperty("version", "2"); old.setProperty("owner", "legacy"); old.setProperty("rollover", "true");
        old.setProperty("scheduled.count", "1");
        old.setProperty("scheduled.0.date", today.plusDays(2).toString());
        old.setProperty("scheduled.0.amount", "25.0");
        old.setProperty("scheduled.0.description", "Scheduled lunch");
        old.setProperty("scheduled.0.category", "Food");
        old.setProperty("scheduled.0.repeat", "0");
        old.setProperty("change.count", "1");
        for (String key : Arrays.asList("date", "amount", "description", "category")) {
            old.setProperty("change.0.before." + key, old.getProperty("undo.0." + key));
            old.remove("undo.0." + key);
        }
        old.remove("undo.count");
        old.setProperty("goal.0.deadline", "05/31/2028");
        try (java.io.Writer w = Files.newBufferedWriter(oldEdition, StandardCharsets.UTF_8)) { old.store(w, "legacy edition"); }
        byte[] original = Files.readAllBytes(oldEdition);
        FinancialTracker.LedgerData imported = new FinancialTracker.LedgerData();
        boolean ownerRejected = false;
        try { imported.load(oldEdition, "another"); } catch (java.io.IOException e) { ownerRejected = true; }
        check(ownerRejected, "Reject other account's saved file");
        imported.load(oldEdition, "Legacy");
        check(imported.loadedLegacyEdition && imported.expenses.size() == 4 && imported.deleted.size() == 1, "Load older edition and scheduled expenses");
        check(imported.goals.get(0).deadline.equals("2028-05-31") && imported.incomes.size() == 1, "Preserve goals and income");
        check(Arrays.equals(original, Files.readAllBytes(oldEdition)), "Loading never overwrites original");
        Path originalBackup = imported.migrateLegacyEdition(oldEdition);
        check(Arrays.equals(original, Files.readAllBytes(originalBackup)), "Exact pre-import backup");
        FinancialTracker.LedgerData reopened = new FinancialTracker.LedgerData();
        reopened.load(oldEdition);
        check(!reopened.loadedLegacyEdition && reopened.expenses.size() == 4 && reopened.deleted.size() == 1, "Migrated data reloads");
        check(reopened.allowances.equals(imported.allowances) && reopened.incomes.size() == 1 && reopened.goals.size() == 1, "Financial records retained");
        check(FinancialTracker.storedDate("11/21/2026").equals(reference), "Read valid slash-format saved date");
        check(FinancialTracker.storedMonth("11/2026").equals(YearMonth.of(2026, 11)), "Read valid slash-format saved month");
        check(new FinancialTracker.User("student", "1234").verify("1234"), "Existing plaintext account works");
        byte[] salt = new byte[16];
        javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec("test-pass".toCharArray(), salt, 100000, 256);
        byte[] hash = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        spec.clearPassword();
        String hashed = "pbkdf2$100000$" + java.util.Base64.getEncoder().encodeToString(salt) + "$" + java.util.Base64.getEncoder().encodeToString(hash);
        FinancialTracker.User hashedUser = new FinancialTracker.User("legacy", hashed);
        check(hashedUser.verify("test-pass") && !hashedUser.verify("wrong") && !hashedUser.verify(hashed), "Legacy hashed login works without accepting hash as password");
        System.out.println("PASS: dashboard, dates, persistence, account isolation, monthly totals, savings returns, edit/delete income, plan/allowance reset, full reset and backup, undo, sort/search, validation, CSV.");
    }
}
