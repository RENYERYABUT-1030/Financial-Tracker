import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Stack;

    public class LedgerData {
        boolean loadedLegacyEdition;
        final ArrayList<Expense> expenses = new ArrayList<Expense>();
        final ArrayList<Goal> goals = new ArrayList<Goal>();
        final ArrayList<String> categories = new ArrayList<String>();
        final Stack<Expense> deleted = new Stack<Expense>();
        final ArrayList<MoneyEntry> incomes = new ArrayList<MoneyEntry>();
        final ArrayList<MoneyEntry> deposits = new ArrayList<MoneyEntry>();
        final ArrayList<MoneyEntry> savingsReturns = new ArrayList<MoneyEntry>();
        final HashMap<YearMonth, Double> allowances = new HashMap<YearMonth, Double>();
        final HashMap<YearMonth, HashMap<String, Double>> plans =
                new HashMap<YearMonth, HashMap<String, Double>>();

        LedgerData() {
            Collections.addAll(categories, FinancialTracker.DEFAULT_CATEGORIES);
        }

        HashMap<String, Double> plan(YearMonth month) {
            if (!plans.containsKey(month)) plans.put(month, new HashMap<String, Double>());
            return plans.get(month);
        }

        double allowance(YearMonth month) {
            return allowances.getOrDefault(month, 0.0);
        }

        static double cents(double amount) {
            return Math.round(amount * 100.0) / 100.0;
        }

        // Linear traversal: filter by month (and optionally category) and sum.
        double spent(YearMonth month, String category) {
            double total = 0;
            for (Expense e : expenses) {
                if (YearMonth.from(e.date).equals(month)
                        && (category == null || category.equals(e.category))) total += e.amount;
            }
            return cents(total);
        }

        int expenseCount(YearMonth month) {
            int count = 0;
            for (Expense e : expenses) if (YearMonth.from(e.date).equals(month)) count++;
            return count;
        }

        double sumEntries(ArrayList<MoneyEntry> entries, YearMonth month) {
            double total = 0;
            for (MoneyEntry e : entries)
                if (YearMonth.from(e.date).equals(month)) total += e.amount;
            return cents(total);
        }

        double extraIncome(YearMonth month) { return sumEntries(incomes, month); }
        double savings(YearMonth month) { return sumEntries(deposits, month); }
        double savingsReturned(YearMonth month) { return sumEntries(savingsReturns, month); }
        double balance(YearMonth month) {
            return cents(allowance(month) + extraIncome(month) - spent(month, null) - savings(month)
                    + savingsReturned(month));
        }

        /** Return saved money on reset/delete without rewriting old monthly records. */
        void resetGoal(Goal goal, LocalDate date) {
            if (goal.saved > 0) {
                savingsReturns.add(new MoneyEntry(date, goal.name, goal.saved));
                goal.saved = 0;
            }
        }

        void resetFinances() {
            expenses.clear(); goals.clear(); categories.clear(); deleted.clear();
            incomes.clear(); deposits.clear(); savingsReturns.clear(); allowances.clear(); plans.clear();
            Collections.addAll(categories, FinancialTracker.DEFAULT_CATEGORIES);
        }

        /** Snapshot the current state first, then commit the empty state atomically. */
        Path backupAndReset(Path file) throws IOException {
            Path backupDir = file.toAbsolutePath().getParent().resolve("backups");
            Files.createDirectories(backupDir);
            Path backup = Files.createTempFile(backupDir, file.getFileName().toString() + "-before-reset-", ".properties");
            save(backup);
            resetFinances();
            try {
                save(file);
            } catch (IOException | RuntimeException e) {
                load(backup);
                throw e;
            }
            return backup;
        }

        boolean hasActivity(YearMonth month) {
            Map<String, Double> budget = plans.get(month);
            return allowance(month) > 0 || expenseCount(month) > 0 || extraIncome(month) > 0
                    || savings(month) > 0 || savingsReturned(month) > 0 || (budget != null && !budget.isEmpty());
        }

        /** Prefer the current month; show the latest saved month if it is empty. */
        YearMonth initialDashboardMonth(YearMonth current) {
            if (hasActivity(current)) return current;
            for (YearMonth month : months()) if (hasActivity(month)) return month;
            return current;
        }

        DashboardTotals dashboardTotals(YearMonth month, LocalDate today) {
            return new DashboardTotals(this, month, today);
        }

        ArrayList<YearMonth> months() {
            ArrayList<YearMonth> result = new ArrayList<YearMonth>();
            result.add(YearMonth.now());
            for (YearMonth m : allowances.keySet()) addMonth(result, m);
            for (YearMonth m : plans.keySet()) addMonth(result, m);
            for (Expense e : expenses) addMonth(result, YearMonth.from(e.date));
            for (MoneyEntry e : incomes) addMonth(result, YearMonth.from(e.date));
            for (MoneyEntry e : deposits) addMonth(result, YearMonth.from(e.date));
            for (MoneyEntry e : savingsReturns) addMonth(result, YearMonth.from(e.date));
            // Bubble sort the month list, newest first.
            for (int i = 0; i < result.size() - 1; i++)
                for (int j = 0; j < result.size() - 1 - i; j++)
                    if (result.get(j).isBefore(result.get(j + 1)))
                        Collections.swap(result, j, j + 1);
            return result;
        }

        private static void addMonth(ArrayList<YearMonth> list, YearMonth month) {
            if (!list.contains(month)) list.add(month);
        }

        private static void put(Properties p, String key, Object value) {
            p.setProperty(key, String.valueOf(value));
        }
        private static String required(Properties p, String key) throws IOException {
            String value = p.getProperty(key);
            if (value == null) throw new IOException("Missing saved field: " + key);
            return value;
        }
        private static int count(Properties p, String key) throws IOException {
            int value = Integer.parseInt(required(p, key));
            if (value < 0 || value > 1000000) throw new IOException("Invalid record count.");
            return value;
        }
        private static double amount(Properties p, String key) throws IOException {
            double value = Double.parseDouble(required(p, key));
            if (!Double.isFinite(value) || value < 0) throw new IOException("Invalid saved amount.");
            return value;
        }
        private static void writeExpenses(Properties p, String prefix, java.util.List<Expense> list) {
            put(p, prefix + ".count", list.size());
            for (int i = 0; i < list.size(); i++) {
                Expense e = list.get(i);
                String k = prefix + "." + i + ".";
                put(p, k + "date", e.date); put(p, k + "description", e.description);
                put(p, k + "category", e.category); put(p, k + "amount", e.amount);
            }
        }
        private static void readExpenses(Properties p, String prefix, java.util.List<Expense> list)
                throws IOException {
            int n = count(p, prefix + ".count");
            for (int i = 0; i < n; i++) {
                String k = prefix + "." + i + ".";
                list.add(readExpense(p, k));
            }
        }

        private static Expense readExpense(Properties p, String k) throws IOException {
            return new Expense(FinancialTracker.storedDate(required(p, k + "date")),
                    required(p, k + "description"), required(p, k + "category"), amount(p, k + "amount"));
        }
        private static void writeEntries(Properties p, String prefix, ArrayList<MoneyEntry> list) {
            put(p, prefix + ".count", list.size());
            for (int i = 0; i < list.size(); i++) {
                MoneyEntry e = list.get(i);
                String k = prefix + "." + i + ".";
                put(p, k + "date", e.date); put(p, k + "description", e.description);
                put(p, k + "amount", e.amount);
            }
        }
        private static void readEntries(Properties p, String prefix, ArrayList<MoneyEntry> list)
                throws IOException {
            int n = count(p, prefix + ".count");
            for (int i = 0; i < n; i++) {
                String k = prefix + "." + i + ".";
                list.add(new MoneyEntry(FinancialTracker.storedDate(required(p, k + "date")),
                        required(p, k + "description"), amount(p, k + "amount")));
            }
        }

        void save(Path file) throws IOException {
            Properties p = new Properties();
            put(p, "version", 1);
            writeExpenses(p, "expense", expenses);
            writeExpenses(p, "undo", deleted);
            writeEntries(p, "income", incomes);
            writeEntries(p, "deposit", deposits);
            writeEntries(p, "savingsReturn", savingsReturns);
            put(p, "category.count", categories.size());
            for (int i = 0; i < categories.size(); i++) put(p, "category." + i, categories.get(i));
            put(p, "goal.count", goals.size());
            for (int i = 0; i < goals.size(); i++) {
                Goal g = goals.get(i);
                String k = "goal." + i + ".";
                put(p, k + "name", g.name); put(p, k + "target", g.target);
                put(p, k + "saved", g.saved); put(p, k + "deadline", g.deadline);
            }
            ArrayList<YearMonth> months = months();
            put(p, "month.count", months.size());
            for (int i = 0; i < months.size(); i++) {
                YearMonth month = months.get(i);
                String k = "month." + i + ".";
                put(p, k + "date", month); put(p, k + "allowance", allowance(month));
                HashMap<String, Double> budget = plan(month);
                put(p, k + "plan.count", budget.size());
                int j = 0;
                for (Map.Entry<String, Double> entry : budget.entrySet()) {
                    String b = k + "plan." + j++ + ".";
                    put(p, b + "category", entry.getKey()); put(p, b + "amount", entry.getValue());
                }
            }
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path temp = Files.createTempFile(file.toAbsolutePath().getParent(), "ledger-", ".tmp");
            try {
                try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                    p.store(writer, "Ledger financial records");
                }
                try {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        }

        void load(Path file) throws IOException { load(file, ""); }

        void load(Path file, String username) throws IOException {
            if (Files.notExists(file)) return;
            Properties p = new Properties();
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                p.load(reader);
            }
            String version = required(p, "version");
            if (!"1".equals(version) && !"2".equals(version)) throw new IOException("Unsupported data version: " + version);
            String owner = p.getProperty("owner", "");
            if (!username.isEmpty() && !owner.isEmpty() && !owner.equalsIgnoreCase(username))
                throw new IOException("The saved file belongs to another account.");
            loadedLegacyEdition = "2".equals(version);
            expenses.clear(); deleted.clear(); incomes.clear(); deposits.clear(); savingsReturns.clear();
            categories.clear(); goals.clear(); allowances.clear(); plans.clear();
            readExpenses(p, "expense", expenses);
            if (loadedLegacyEdition) {
                // The simpler Ledger has no schedules; preserve each dated record.
                readExpenses(p, "scheduled", expenses);
                int changes = count(p, "change.count");
                for (int i = 0; i < changes; i++) {
                    String k = "change." + i + ".";
                    boolean before = p.containsKey(k + "before.date");
                    boolean after = p.containsKey(k + "after.date");
                    if (!before && !after) throw new IOException("Invalid undo record: " + k);
                    if (before && !after) deleted.push(readExpense(p, k + "before."));
                }
            } else readExpenses(p, "undo", deleted);
            readEntries(p, "income", incomes); readEntries(p, "deposit", deposits);
            // Older Ledger files lack this optional list and remain readable.
            if (p.containsKey("savingsReturn.count")) readEntries(p, "savingsReturn", savingsReturns);
            int n = count(p, "category.count");
            for (int i = 0; i < n; i++) categories.add(required(p, "category." + i));
            n = count(p, "goal.count");
            for (int i = 0; i < n; i++) {
                String k = "goal." + i + ".";
                String deadline = FinancialTracker.storedDeadline(required(p, k + "deadline")).toString();
                goals.add(new Goal(required(p, k + "name"), amount(p, k + "target"),
                        amount(p, k + "saved"), deadline));
            }
            n = count(p, "month.count");
            for (int i = 0; i < n; i++) {
                String k = "month." + i + ".";
                YearMonth m = FinancialTracker.storedMonth(required(p, k + "date"));
                allowances.put(m, amount(p, k + "allowance"));
                int size = count(p, k + "plan.count");
                for (int j = 0; j < size; j++) {
                    String b = k + "plan." + j + ".";
                    plan(m).put(required(p, b + "category"), amount(p, b + "amount"));
                }
            }
        }

        /** Never replace a legacy file without first saving its exact bytes. */
        Path migrateLegacyEdition(Path file) throws IOException {
            if (!loadedLegacyEdition) throw new IOException("No old-edition data to import.");
            Path backupDir = file.toAbsolutePath().getParent().resolve("backups");
            Files.createDirectories(backupDir);
            Path backup = Files.createTempFile(backupDir, file.getFileName().toString() + "-before-import-", ".properties");
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            save(file);
            loadedLegacyEdition = false;
            return backup;
        }

        private static String csv(String value) {
            // Quote text, escape quotes and prevent spreadsheet formula injection.
            if (value.matches("(?s)^\\s*[=+@-].*")) value = "'" + value;
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        private static String moneyNumber(double value) {
            return String.format(Locale.ROOT, "%.2f", value);
        }
        void exportCsv(Path file, YearMonth month) throws IOException {
            try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                out.write("\uFEFFDate,Type,Description,Category,Amount\r\n");
                out.write(month.atDay(1) + ",Allowance,Monthly allowance,," + moneyNumber(allowance(month)) + "\r\n");
                for (MoneyEntry e : incomes) if (YearMonth.from(e.date).equals(month))
                    out.write(e.date + ",Income," + csv(e.description) + ",," + moneyNumber(e.amount) + "\r\n");
                for (Expense e : expenses) if (YearMonth.from(e.date).equals(month))
                    out.write(e.date + ",Expense," + csv(e.description) + "," + csv(e.category) + "," + moneyNumber(e.amount) + "\r\n");
                for (MoneyEntry e : deposits) if (YearMonth.from(e.date).equals(month))
                    out.write(e.date + ",Savings," + csv(e.description) + ",," + moneyNumber(e.amount) + "\r\n");
                for (MoneyEntry e : savingsReturns) if (YearMonth.from(e.date).equals(month))
                    out.write(e.date + ",Savings return," + csv(e.description) + ",," + moneyNumber(e.amount) + "\r\n");
                out.write(",Summary,Remaining balance,," + moneyNumber(balance(month)) + "\r\n");
            }
        }
    }
