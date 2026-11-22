    public class Goal {
        String name;
        double target;
        double saved;
        String deadline;

        Goal(String name, double target, double saved, String deadline) {
            this.name = name;
            this.target = target;
            this.saved = saved;
            this.deadline = FinancialTracker.storedDeadline(deadline).toString();
        }
    }
