import java.time.LocalDate;

    public class MoneyEntry {
        final LocalDate date;
        final String description;
        final double amount;
        MoneyEntry(LocalDate date, String description, double amount) {
            this.date = date;
            this.description = description;
            this.amount = amount;
        }
    }
