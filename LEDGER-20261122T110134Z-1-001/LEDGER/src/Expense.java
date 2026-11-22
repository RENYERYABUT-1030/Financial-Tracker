import java.time.LocalDate;

    public class Expense {
        LocalDate date;
        String description;
        String category;
        double amount;

        Expense(LocalDate date, String description, String category, double amount) {
            this.date = date;
            this.description = description;
            this.category = category;
            this.amount = amount;
        }
    }
