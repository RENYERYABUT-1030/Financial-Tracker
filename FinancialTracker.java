import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;

public class FinancialTracker extends JFrame {

    // =========================
    // Colors / Fonts
    // =========================
    private static final Color PRIMARY = new Color(30, 41, 82);
    private static final Color ACCENT = new Color(66, 99, 235);
    private static final Color GREEN = new Color(34, 139, 84);
    private static final Color RED = new Color(200, 55, 55);
    private static final Color BLUE = new Color(41, 98, 190);
    private static final Color BG = new Color(243, 245, 250);
    private static final Color ZEBRA = new Color(248, 249, 253);
    private static final Font BASE = new Font("SansSerif", Font.PLAIN, 13);
    private static final Font BOLD = new Font("SansSerif", Font.BOLD, 13);

    // =========================
    // Transaction Class
    // =========================
    static class Transaction {
        int id;
        String type;      // Income, Expense, Savings
        String category;
        double amount;
        String date;

        public Transaction(int id, String type, String category,
                           double amount, String date) {
            this.id = id;
            this.type = type;
            this.category = category;
            this.amount = amount;
            this.date = date;
        }
    }

    // =========================
    // Savings Goal Class
    // =========================
    static class Goal {
        String name;
        String purpose;
        double target;
        double saved;
        LocalDate deadline; // may be null

        Goal(String name, String purpose, double target, LocalDate deadline) {
            this.name = name;
            this.purpose = purpose;
            this.target = target;
            this.deadline = deadline;
        }

        boolean isReached() {
            return saved >= target;
        }
    }

    // =========================
    // Data Structures
    // =========================
    private ArrayList<Transaction> transactions;
    private HashMap<String, Double> categoryTotals;
    private PriorityQueue<Transaction> highestExpenses;
    private ArrayList<Goal> goals;

    // =========================
    // GUI Components
    // =========================
    private JTextField amountField;
    private JTextField categoryField;
    private JComboBox<String> typeBox;

    private JLabel incomeLabel;
    private JLabel expenseLabel;
    private JLabel savedLabel;
    private JLabel balanceLabel;

    private JTable table;
    private DefaultTableModel model;
    private JPanel goalsListPanel;

    private int transactionCounter = 1;

    // =========================
    // Constructor
    // =========================
    public FinancialTracker() {

        transactions = new ArrayList<>();
        categoryTotals = new HashMap<>();
        goals = new ArrayList<>();

        highestExpenses = new PriorityQueue<>(
                (a, b) -> Double.compare(b.amount, a.amount));

        initializeGUI();
    }

    // =========================
    // Helpers
    // =========================
    private static String peso(double v) {
        return "₱" + String.format("%,.2f", v);
    }

    private JButton makeButton(String text, Color bg) {
        JButton b = new JButton(text);
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setFont(BOLD);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setOpaque(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(new EmptyBorder(8, 14, 8, 14));
        return b;
    }

    private JPanel makeCard(String title, JLabel valueLabel, Color color) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(new CompoundBorder(
                new MatteBorder(0, 5, 0, 0, color),
                new EmptyBorder(10, 14, 10, 14)));

        JLabel t = new JLabel(title);
        t.setForeground(Color.GRAY);
        t.setFont(new Font("SansSerif", Font.PLAIN, 12));

        valueLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        valueLabel.setForeground(color);

        card.add(t, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br>");
    }

    // =========================
    // GUI
    // =========================
    private void initializeGUI() {

        try {
            UIManager.setLookAndFeel(
                    UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        for (Object key : Collections.list(UIManager.getDefaults().keys())) {
            Object v = UIManager.get(key);
            if (v instanceof javax.swing.plaf.FontUIResource) {
                UIManager.put(key, new javax.swing.plaf.FontUIResource(BASE));
            }
        }

        setTitle("Financial Tracker");
        setSize(1200, 720);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        getContentPane().setBackground(BG);
        setLayout(new BorderLayout());

        // ---------- NORTH: header + input ----------
        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(PRIMARY);
        header.setBorder(new EmptyBorder(14, 20, 14, 20));
        JLabel title = new JLabel("Financial Tracker");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        JLabel sub = new JLabel("Track your money. Reach your goals.");
        sub.setForeground(new Color(190, 200, 235));
        header.add(title, BorderLayout.NORTH);
        header.add(sub, BorderLayout.SOUTH);

        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        inputPanel.setBackground(Color.WHITE);
        inputPanel.setBorder(new MatteBorder(0, 0, 1, 0,
                new Color(220, 224, 235)));

        inputPanel.add(new JLabel("Amount"));
        amountField = new JTextField(9);
        inputPanel.add(amountField);

        inputPanel.add(new JLabel("Category"));
        categoryField = new JTextField(12);
        inputPanel.add(categoryField);

        typeBox = new JComboBox<>(new String[]{"Income", "Expense"});
        inputPanel.add(typeBox);

        JButton addBtn = makeButton("Add", ACCENT);
        JButton sortBtn = makeButton("Sort Highest Amount", new Color(90, 98, 120));
        JButton reportBtn = makeButton("Category Report", new Color(90, 98, 120));

        inputPanel.add(addBtn);
        inputPanel.add(sortBtn);
        inputPanel.add(reportBtn);

        north.add(header);
        north.add(inputPanel);
        add(north, BorderLayout.NORTH);

        // ---------- CENTER: table + goals ----------
        model = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        model.addColumn("ID");
        model.addColumn("Type");
        model.addColumn("Category");
        model.addColumn("Amount");
        model.addColumn("Date");

        table = new JTable(model);
        table.setRowHeight(28);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(new Color(214, 224, 252));
        table.setSelectionForeground(Color.BLACK);
        table.getColumnModel().getColumn(0).setMaxWidth(60);

        JTableHeader th = table.getTableHeader();
        th.setBackground(PRIMARY);
        th.setForeground(Color.WHITE);
        th.setFont(BOLD);
        th.setPreferredSize(new Dimension(0, 32));

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable tbl, Object value, boolean sel,
                    boolean focus, int row, int col) {

                super.getTableCellRendererComponent(
                        tbl, value, sel, false, row, col);

                setBorder(new EmptyBorder(0, 10, 0, 10));

                String type = String.valueOf(model.getValueAt(row, 1));
                Color c = type.equals("Income") ? GREEN
                        : type.equals("Expense") ? RED : BLUE;

                if (col == 3) {
                    double amt = (Double) value;
                    setText((amt < 0 ? "-" : "") + peso(Math.abs(amt)));
                    setHorizontalAlignment(RIGHT);
                } else {
                    setHorizontalAlignment(LEFT);
                }

                if (col == 1 || col == 3) {
                    setForeground(c);
                    setFont(BOLD);
                } else {
                    setForeground(Color.DARK_GRAY);
                    setFont(BASE);
                }

                if (!sel) {
                    setBackground(row % 2 == 0 ? Color.WHITE : ZEBRA);
                }
                return this;
            }
        });

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(new LineBorder(new Color(220, 224, 235)));
        tableScroll.getViewport().setBackground(Color.WHITE);

        JPanel tableWrap = new JPanel(new BorderLayout(0, 8));
        tableWrap.setOpaque(false);
        JLabel tLabel = new JLabel("Transactions");
        tLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        tLabel.setForeground(PRIMARY);
        tableWrap.add(tLabel, BorderLayout.NORTH);
        tableWrap.add(tableScroll, BorderLayout.CENTER);

        // Goals panel
        JPanel goalsWrap = new JPanel(new BorderLayout(0, 8));
        goalsWrap.setOpaque(false);
        goalsWrap.setPreferredSize(new Dimension(360, 0));

        JPanel goalsHeader = new JPanel(new BorderLayout());
        goalsHeader.setOpaque(false);
        JLabel gLabel = new JLabel("Savings Goals");
        gLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        gLabel.setForeground(PRIMARY);
        JButton newGoalBtn = makeButton("+ New Goal", GREEN);
        goalsHeader.add(gLabel, BorderLayout.WEST);
        goalsHeader.add(newGoalBtn, BorderLayout.EAST);

        goalsListPanel = new JPanel();
        goalsListPanel.setLayout(new BoxLayout(goalsListPanel, BoxLayout.Y_AXIS));
        goalsListPanel.setBackground(BG);

        JPanel goalsHolder = new JPanel(new BorderLayout());
        goalsHolder.setBackground(BG);
        goalsHolder.add(goalsListPanel, BorderLayout.NORTH);

        JScrollPane goalsScroll = new JScrollPane(goalsHolder);
        goalsScroll.setBorder(new LineBorder(new Color(220, 224, 235)));
        goalsScroll.getVerticalScrollBar().setUnitIncrement(14);
        goalsScroll.getViewport().setBackground(BG);

        goalsWrap.add(goalsHeader, BorderLayout.NORTH);
        goalsWrap.add(goalsScroll, BorderLayout.CENTER);

        JPanel center = new JPanel(new BorderLayout(16, 0));
        center.setBackground(BG);
        center.setBorder(new EmptyBorder(14, 16, 8, 16));
        center.add(tableWrap, BorderLayout.CENTER);
        center.add(goalsWrap, BorderLayout.EAST);
        add(center, BorderLayout.CENTER);

        // ---------- SOUTH: dashboard ----------
        incomeLabel = new JLabel(peso(0));
        expenseLabel = new JLabel(peso(0));
        savedLabel = new JLabel(peso(0));
        balanceLabel = new JLabel(peso(0));

        JPanel dashboard = new JPanel(new GridLayout(1, 4, 14, 0));
        dashboard.setBackground(BG);
        dashboard.setBorder(new EmptyBorder(6, 16, 16, 16));
        dashboard.add(makeCard("INCOME", incomeLabel, GREEN));
        dashboard.add(makeCard("EXPENSE", expenseLabel, RED));
        dashboard.add(makeCard("SAVED (GOALS)", savedLabel, BLUE));
        dashboard.add(makeCard("BALANCE", balanceLabel, PRIMARY));
        add(dashboard, BorderLayout.SOUTH);

        // ---------- Events ----------
        addBtn.addActionListener(e -> addTransaction());
        amountField.addActionListener(e -> addTransaction());
        categoryField.addActionListener(e -> addTransaction());
        sortBtn.addActionListener(e -> sortTransactions());
        reportBtn.addActionListener(e -> showCategoryReport());
        newGoalBtn.addActionListener(e -> createGoal());

        refreshGoals();

        setVisible(true);
    }

    // =========================
    // Add Transaction
    // =========================
    private void addTransaction() {

        try {

            double amount = Double.parseDouble(amountField.getText().trim());
            String category = categoryField.getText().trim();
            String type = (String) typeBox.getSelectedItem();

            if (amount <= 0 || category.isEmpty()) {
                throw new IllegalArgumentException();
            }

            Transaction t = recordTransaction(type, category, amount);

            if (type.equals("Expense")) {
                highestExpenses.add(t);
                categoryTotals.put(category,
                        categoryTotals.getOrDefault(category, 0.0) + amount);
            }

            updateDashboard();

            amountField.setText("");
            categoryField.setText("");
            amountField.requestFocus();

        } catch (Exception ex) {

            JOptionPane.showMessageDialog(this,
                    "Invalid Input!\nPut a valid amount (more than 0) and a category.",
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Transaction recordTransaction(String type, String category,
                                          double amount) {
        Transaction t = new Transaction(
                transactionCounter++, type, category, amount,
                LocalDate.now().toString());

        transactions.add(t);

        model.addRow(new Object[]{t.id, t.type, t.category, t.amount, t.date});
        table.scrollRectToVisible(
                table.getCellRect(model.getRowCount() - 1, 0, true));
        return t;
    }

    // =========================
    // Dashboard Calculation
    // =========================
    private double[] totals() {
        double income = 0, expense = 0, saved = 0;

        for (Transaction t : transactions) {
            if (t.type.equals("Income")) income += t.amount;
            else if (t.type.equals("Expense")) expense += t.amount;
            else saved += t.amount; // Savings (can be negative on withdraw)
        }
        return new double[]{income, expense, saved, income - expense - saved};
    }

    private double availableBalance() {
        return totals()[3];
    }

    private void updateDashboard() {

        double[] t = totals();

        incomeLabel.setText(peso(t[0]));
        expenseLabel.setText(peso(t[1]));
        savedLabel.setText(peso(t[2]));
        balanceLabel.setText(peso(t[3]));
        balanceLabel.setForeground(t[3] < 0 ? RED : PRIMARY);
    }

    // =========================
    // Sorting Algorithm
    // O(n log n)
    // =========================
    private void sortTransactions() {

        transactions.sort((a, b) -> Double.compare(b.amount, a.amount));

        model.setRowCount(0);

        for (Transaction t : transactions) {
            model.addRow(new Object[]{t.id, t.type, t.category, t.amount, t.date});
        }
    }

    // =========================
    // Category Analysis
    // =========================
    private void showCategoryReport() {

        StringBuilder report = new StringBuilder();

        report.append("Expense Report\n\n");

        for (String category : categoryTotals.keySet()) {
            report.append(category)
                    .append(" : ")
                    .append(peso(categoryTotals.get(category)))
                    .append("\n");
        }

        report.append("\n");

        if (!highestExpenses.isEmpty()) {
            Transaction top = highestExpenses.peek();
            report.append("Highest Expense\n");
            report.append(top.category + " - " + peso(top.amount) + "\n");
        }

        if (!goals.isEmpty()) {
            report.append("\nSavings Goals\n\n");
            for (Goal g : goals) {
                int pct = (int) Math.min(100, Math.round(g.saved / g.target * 100));
                report.append(g.name).append(" : ")
                        .append(peso(g.saved)).append(" / ")
                        .append(peso(g.target))
                        .append(" (").append(pct).append("%)\n");
            }
        }

        JTextArea area = new JTextArea(report.toString(), 16, 34);
        area.setEditable(false);
        area.setMargin(new Insets(10, 10, 10, 10));

        JOptionPane.showMessageDialog(this, new JScrollPane(area),
                "Report", JOptionPane.INFORMATION_MESSAGE);
    }

    // =========================
    // Savings Goals
    // =========================
    private void createGoal() {

        JTextField nameField = new JTextField(20);
        JTextArea purposeArea = new JTextArea(3, 20);
        purposeArea.setLineWrap(true);
        purposeArea.setWrapStyleWord(true);
        JTextField targetField = new JTextField(20);
        JTextField dateField = new JTextField(20);

        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        String[] labels = {"Goal name", "Purpose (para saan?)",
                "Target amount (₱)", "Target date (yyyy-MM-dd, optional)"};
        Component[] fields = {nameField, new JScrollPane(purposeArea),
                targetField, dateField};

        for (int i = 0; i < labels.length; i++) {
            c.gridx = 0;
            c.gridy = i;
            c.weightx = 0;
            p.add(new JLabel(labels[i]), c);
            c.gridx = 1;
            c.weightx = 1;
            p.add(fields[i], c);
        }

        int res = JOptionPane.showConfirmDialog(this, p, "New Savings Goal",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (res != JOptionPane.OK_OPTION) return;

        try {
            String name = nameField.getText().trim();
            String purpose = purposeArea.getText().trim();
            double target = Double.parseDouble(targetField.getText().trim());

            if (name.isEmpty() || target <= 0) {
                throw new IllegalArgumentException();
            }

            LocalDate deadline = null;
            String d = dateField.getText().trim();
            if (!d.isEmpty()) {
                deadline = LocalDate.parse(d);
                if (!deadline.isAfter(LocalDate.now())) {
                    JOptionPane.showMessageDialog(this,
                            "Target date must be in the future.",
                            "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            goals.add(new Goal(name, purpose, target, deadline));
            refreshGoals();

        } catch (DateTimeParseException ex) {
            JOptionPane.showMessageDialog(this,
                    "Invalid date. Use format yyyy-MM-dd (e.g. 2026-12-25).",
                    "Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Invalid Input!\nGoal name and a target amount above 0 are required.",
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addSavings(Goal g) {

        if (g.isReached()) {
            JOptionPane.showMessageDialog(this, "This goal is already reached!");
            return;
        }

        double balance = availableBalance();
        double remaining = g.target - g.saved;

        if (balance <= 0) {
            JOptionPane.showMessageDialog(this,
                    "You have no available balance to save.\nAdd income first.",
                    "No balance", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String input = JOptionPane.showInputDialog(this,
                "Available balance: " + peso(balance)
                        + "\nStill needed: " + peso(remaining)
                        + "\n\nHow much do you want to save?",
                "Add Savings - " + g.name, JOptionPane.PLAIN_MESSAGE);

        if (input == null) return;

        try {
            double amt = Double.parseDouble(input.trim());

            if (amt <= 0) throw new IllegalArgumentException();

            if (amt > balance) {
                JOptionPane.showMessageDialog(this,
                        "Not enough balance. You only have " + peso(balance) + ".",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (amt > remaining) {
                amt = remaining; // only save what's needed
            }

            g.saved += amt;
            recordTransaction("Savings", "Goal: " + g.name, amt);
            updateDashboard();
            refreshGoals();

            if (g.isReached()) {
                JOptionPane.showMessageDialog(this,
                        "Congratulations! You reached your goal:\n"
                                + g.name + " (" + peso(g.target) + ")",
                        "Goal Reached!", JOptionPane.INFORMATION_MESSAGE);
            }

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Invalid amount!",
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteGoal(Goal g) {

        int ok = JOptionPane.showConfirmDialog(this,
                "Delete goal \"" + g.name + "\"?"
                        + (g.saved > 0
                        ? "\n" + peso(g.saved) + " will be returned to your balance."
                        : ""),
                "Delete Goal", JOptionPane.YES_NO_OPTION);

        if (ok != JOptionPane.YES_OPTION) return;

        if (g.saved > 0) {
            recordTransaction("Savings", "Withdrawn: " + g.name, -g.saved);
        }

        goals.remove(g);
        updateDashboard();
        refreshGoals();
    }

    private void refreshGoals() {

        goalsListPanel.removeAll();

        if (goals.isEmpty()) {
            JLabel empty = new JLabel("<html><div style='width:260px;padding:16px;"
                    + "color:gray'>No goals yet.<br>Click <b>+ New Goal</b> to "
                    + "start saving for something.</div></html>");
            goalsListPanel.add(empty);
        }

        for (Goal g : goals) {
            JPanel card = buildGoalCard(g);
            card.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                    card.getPreferredSize().height));
            goalsListPanel.add(card);
            goalsListPanel.add(Box.createVerticalStrut(10));
        }

        goalsListPanel.revalidate();
        goalsListPanel.repaint();
    }

    private JPanel buildGoalCard(Goal g) {

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(new CompoundBorder(
                new MatteBorder(0, 5, 0, 0, g.isReached() ? GREEN : ACCENT),
                new EmptyBorder(10, 12, 10, 12)));

        JLabel name = new JLabel(g.name);
        name.setFont(new Font("SansSerif", Font.BOLD, 15));
        name.setForeground(PRIMARY);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(name);

        if (!g.purpose.isEmpty()) {
            JLabel purpose = new JLabel("<html><div style='width:250px;color:#555'>"
                    + "<i>" + escapeHtml(g.purpose) + "</i></div></html>");
            purpose.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(Box.createVerticalStrut(4));
            card.add(purpose);
        }

        int pct = (int) Math.min(100, Math.round(g.saved / g.target * 100));
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setValue(pct);
        bar.setStringPainted(true);
        bar.setString(pct + "%");
        bar.setForeground(g.isReached() ? GREEN : ACCENT);
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        card.add(Box.createVerticalStrut(8));
        card.add(bar);

        JLabel amounts = new JLabel(peso(g.saved) + " / " + peso(g.target));
        amounts.setFont(BOLD);
        amounts.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(Box.createVerticalStrut(6));
        card.add(amounts);

        String status;
        if (g.isReached()) {
            status = "Goal reached!";
        } else {
            double remaining = g.target - g.saved;
            status = "Remaining: " + peso(remaining);

            if (g.deadline != null) {
                long days = ChronoUnit.DAYS.between(LocalDate.now(), g.deadline);
                if (days <= 0) {
                    status += " | Deadline passed";
                } else {
                    double perDay = remaining / days;
                    status += " | " + days + " days left"
                            + "<br>Save ~" + peso(perDay) + "/day or ~"
                            + peso(perDay * 7) + "/week";
                }
            }
        }

        JLabel statusLabel = new JLabel("<html><div style='width:250px;color:"
                + (g.isReached() ? "#228b54" : "#555") + "'>" + status
                + "</div></html>");
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(Box.createVerticalStrut(4));
        card.add(statusLabel);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        btns.setOpaque(false);
        btns.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton save = makeButton("Add Savings", ACCENT);
        JButton del = makeButton("Delete", RED);
        save.setEnabled(!g.isReached());
        save.addActionListener(e -> addSavings(g));
        del.addActionListener(e -> deleteGoal(g));
        btns.add(save);
        btns.add(del);

        card.add(Box.createVerticalStrut(8));
        card.add(btns);

        return card;
    }

    // =========================
    // Binary Search
    // =========================
    public Transaction searchById(int id) {

        List<Transaction> sorted = new ArrayList<>(transactions);

        sorted.sort(Comparator.comparingInt(a -> a.id));

        int left = 0;
        int right = sorted.size() - 1;

        while (left <= right) {

            int mid = (left + right) / 2;

            if (sorted.get(mid).id == id)
                return sorted.get(mid);

            if (sorted.get(mid).id < id)
                left = mid + 1;
            else
                right = mid - 1;
        }

        return null;
    }

    // =========================
    // Main Method
    // =========================
    public static void main(String[] args) {

        SwingUtilities.invokeLater(FinancialTracker::new);
    }
}