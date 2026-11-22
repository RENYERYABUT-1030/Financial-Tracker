import java.time.LocalDate;
import java.time.YearMonth;

    public class DashboardTotals {
        final double allowance, income, savings, spent, balance, dailyBudget;
        final int remainingDays, expenseCount;

        DashboardTotals(LedgerData data, YearMonth month, LocalDate today) {
            allowance = data.allowance(month);
            income = data.extraIncome(month);
            savings = data.savings(month);
            spent = data.spent(month, null);
            balance = data.balance(month);
            expenseCount = data.expenseCount(month);
            YearMonth current = YearMonth.from(today);
            remainingDays = month.isBefore(current) ? 0 : month.equals(current)
                    ? month.lengthOfMonth() - today.getDayOfMonth() + 1 : month.lengthOfMonth();
            dailyBudget = remainingDays == 0 ? 0 : LedgerData.cents(Math.max(0, balance) / remainingDays);
        }
    }
