import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Stack;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerDateModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * Ledger - a simple student financial tracker.
 *
 * <p>This program was written for a Data Structures class, so the data is kept in
 * real data structures instead of plain variables. The three used here are:</p>
 *
 * <ul>
 *   <li><b>ArrayList</b> - the list of expenses, the list of goals, the list of
 *       categories and the list of user accounts. It works like an array that can
 *       grow and shrink.</li>
 *   <li><b>HashMap</b> - the planned budget. The key is the category name and the
 *       value is the amount planned for that category, so we can look a category
 *       up in one step instead of searching the whole list.</li>
 *   <li><b>Stack</b> - the recently deleted expenses. When you delete a row it is
 *       pushed on the stack, and "Undo last delete" pops it back. Last in, first out.</li>
 * </ul>
 *
 * <p>Sorting is done with a simple bubble sort written by hand (see
 * {@link #sortExpenses(ArrayList, int)}) so the algorithm is visible in the code.</p>
 *
 * <p>The screens are built in the same style Eclipse WindowBuilder uses: the frame
 * has a {@code contentPane}, the components are fields, and the UI is assembled in
 * {@code createContents()} and the {@code create...Panel()} methods.</p>
 *
 * <p>Log in with the demo account <b>student / 1234</b> (or create your own account
 * on the login window).</p>
 */
public class FinancialTracker extends JFrame {

    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------------
    // Colors and fonts used by the window
    // ------------------------------------------------------------------

    private static final Color BG = new Color(244, 246, 250);
    private static final Color CARD = Color.WHITE;
    private static final Color INK = new Color(15, 23, 42);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final Color ACCENT = new Color(79, 70, 229);
    private static final Color GREEN = new Color(21, 128, 61);
    private static final Color RED = new Color(185, 28, 28);
    private static final Color BORDER = new Color(226, 232, 240);

    private static final Font TITLE_FONT = new Font("Helvetica Neue", Font.BOLD, 20);
    private static final Font LABEL_FONT = new Font("Helvetica Neue", Font.PLAIN, 12);
    private static final Font SMALL_FONT = new Font("Helvetica Neue", Font.PLAIN, 11);
    private static final Font BOLD_FONT = new Font("Helvetica Neue", Font.BOLD, 12);
    private static final Font BIG_FONT = new Font("Helvetica Neue", Font.BOLD, 22);

    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00",
            java.text.DecimalFormatSymbols.getInstance(Locale.US));
    private static final DateTimeFormatter GOAL_DATE = DateTimeFormatter
            .ofPattern("MM/dd/uuuu", Locale.US)
            .withResolverStyle(java.time.format.ResolverStyle.STRICT);
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter
            .ofPattern("MM/uuuu", Locale.US)
            .withResolverStyle(java.time.format.ResolverStyle.STRICT);

    private static final String[] DEFAULT_CATEGORIES = {
        "Food", "Transport", "School", "Load", "Other"
    };

    // ------------------------------------------------------------------
    // THE DATA (data structures part)
    // ------------------------------------------------------------------

    /** ArrayList of all expenses. Each row of the table is one Expense object. */
    private final LedgerData data = new LedgerData();
    private final ArrayList<Expense> expenses = data.expenses;

    /** ArrayList of all savings goals. */
    private final ArrayList<Goal> goals = data.goals;

    /** ArrayList of category names. Starts with the six defaults, can grow. */
    private final ArrayList<String> categories = data.categories;

    /** Each month has its own category budget HashMap. */
    private HashMap<String, Double> planned() {
        return data.plan(YearMonth.now());
    }

    /** Stack of deleted expenses, used by "Undo last delete". */
    private final Stack<Expense> deleted = data.deleted;

    /** The expenses currently shown in the table, in table order. */
    private final ArrayList<Expense> shownExpenses = new ArrayList<Expense>();

    private String username = "";
    private Path dataFile;
    private boolean unsaved;

    // ------------------------------------------------------------------
    // COMPONENTS (declared as fields, the way WindowBuilder does it)
    // ------------------------------------------------------------------

    private JPanel contentPane;
    private JTabbedPane tabbedPane;

    // Dashboard
    private JLabel allowanceValueLabel;
    private JLabel spentValueLabel;
    private JLabel leftValueLabel;
    private JLabel perDayValueLabel;
    private JTextArea summaryArea;
    private JComboBox<String> dashboardMonthBox;
    private YearMonth dashboardMonth;
    private boolean fillingDashboardMonths;

    // Expenses
    private JTextField descriptionField;
    private JTextField amountField;
    private JComboBox<String> categoryBox;
    private JSpinner dateSpinner;
    private LocalDate dateFloor = LocalDate.now();
    private javax.swing.Timer dateRefreshTimer;
    private JButton addExpenseButton;
    private JButton saveExpenseButton;
    private JButton cancelExpenseButton;
    private JTextField searchField;
    private JComboBox<String> sortBox;
    private JTable expenseTable;
    private DefaultTableModel expenseTableModel;
    private JLabel expenseCountLabel;
    private JButton undoButton;

    /** Keep the object, not a table index: sorting/filtering can move rows. */
    private Expense editingExpense;
    private YearMonth expenseMonth = YearMonth.now();
    private YearMonth activeMonth = YearMonth.now();
    private LocalDate expenseFrom;
    private LocalDate expenseTo;
    private String expenseCategory;
    private JLabel filterInfo;

    // Goals
    private JTextField goalNameField;
    private JTextField goalTargetField;
    private JTextField goalDeadlineField;
    private JButton addGoalButton;
    private JButton saveGoalButton;
    private JButton cancelGoalButton;
    private JTable goalTable;
    private DefaultTableModel goalTableModel;
    private JTextField depositField;
    private JLabel goalInfoLabel;
    private int editingGoal = -1;

    // Allowance / budget
    private JTextField allowanceField;
    private JLabel allowanceMessageLabel;
    private JComboBox<String> planCategoryBox;
    private JTextField planAmountField;
    private JTable planTable;
    private DefaultTableModel planTableModel;
    private JLabel monthInfoLabel;

    // Reports (the graph)
    private JPanel categoryBarsPanel;
    private JPanel dailyBarsPanel;
    private JLabel reportSummaryLabel;
    private JComboBox<String> reportMonthBox;
    private boolean fillingReportMonths;

    // ------------------------------------------------------------------
    // Start of the program
    // ------------------------------------------------------------------

    public static void main(String[] args) {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                try {
                    UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");

                    // Nimbus takes its colors from these keys, so we set them once
                    // here and every button and table header follows the theme.
                    UIManager.put("nimbusBase", new Color(79, 70, 229));
                    UIManager.put("nimbusBlueGrey", new Color(226, 232, 240));
                    UIManager.put("nimbusLightBackground", Color.WHITE);
                    UIManager.put("control", new Color(244, 246, 250));
                    UIManager.put("info", new Color(244, 246, 250));
                    UIManager.put("nimbusSelectionBackground", new Color(79, 70, 229));
                    UIManager.put("nimbusOrange", new Color(79, 70, 229));
                    UIManager.put("text", new Color(15, 23, 42));
                } catch (Exception e) {
                    // If Nimbus is missing we simply keep the default look and feel.
                }

                LoginDialog login = new LoginDialog();
                login.setVisible(true);

                if (login.isLoggedIn()) {
                    openTracker(login.getUsername());
                } else {
                    System.exit(0);
                }
            }
        });
    }

    /** Builds the main window for the user who just logged in. */
    public FinancialTracker(String username) {
        this.username = username;
        dataFile = dataPath(Paths.get(System.getProperty("user.home")), username);
        try {
            data.load(dataFile, username);
            if (data.loadedLegacyEdition) {
                int choice = JOptionPane.showConfirmDialog(null,
                        "Your saved records are from an earlier Ledger edition.\n"
                        + "Import them into this version? An exact backup will be made first.\n\n"
                        + "Expenses, income, allowance, savings and budgets will be retained.\n"
                        + "Scheduled expenses become ordinary dated expenses; automatic repeats stop.\n"
                        + "Monthly balances use this version's rules (no automatic carry-over).\n"
                        + "The original undo history and settings remain in the backup.",
                        "Import previous Ledger data", JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE);
                if (choice != JOptionPane.OK_OPTION)
                    throw new IOException("Import cancelled. Your original file is unchanged.");
                Path backup = data.migrateLegacyEdition(dataFile);
                JOptionPane.showMessageDialog(null, "Previous records imported.\nOriginal file backed up to:\n" + backup,
                        "Ledger", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Could not open your Ledger data. No records were reset.\n"
                    + "Reason: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                    + "\nFile: " + dataFile + "\nKeep this file and any backups; do not delete them.", e);
        }
        dashboardMonth = data.initialDashboardMonth(YearMonth.now());
        expenseMonth = dashboardMonth;

        setTitle("Ledger - Student Financial Tracker");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (!unsaved || saveData()) dispose();
            }
            public void windowClosed(java.awt.event.WindowEvent e) {
                if (dateRefreshTimer != null) dateRefreshTimer.stop();
            }
        });
        setBounds(60, 40, 1000, 720);
        setMinimumSize(new Dimension(900, 620));

        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(0, 0, 0, 0));
        contentPane.setLayout(new BorderLayout(0, 0));
        contentPane.setBackground(BG);
        setContentPane(contentPane);

        createContents();
        // If the tracker stays open overnight, advance the minimum date automatically.
        dateRefreshTimer = new javax.swing.Timer(30000, e -> {
            if (!LocalDate.now().equals(dateFloor)) {
                refreshDateFloor();
                refreshCurrentMonth();
                refreshAll();
            }
        });
        dateRefreshTimer.start();
        allowanceField.setText(MONEY.format(data.allowance(YearMonth.now())));
        refreshFilterLabel();
        rebuildExpenseTable();
        refreshAll();
    }

    private static void openTracker(String username) {
        try {
            new FinancialTracker(username).setVisible(true);
        } catch (IllegalStateException e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), "Ledger", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Stable, safe and unique filename for each case-insensitive login. */
    static Path dataPath(Path home, String user) {
        StringBuilder key = new StringBuilder();
        for (byte b : user.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8))
            key.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return home.resolve("LedgerData").resolve(key + ".properties");
    }

    private boolean saveData() {
        try {
            data.save(dataFile);
            unsaved = false;
            return true;
        } catch (IOException | RuntimeException e) {
            unsaved = true;
            showError("Could not save financial records. Keep Ledger open and retry. " + e.getMessage());
            return false;
        }
    }

    private boolean changed() {
        unsaved = true;
        boolean saved = saveData();
        refreshAll();
        return saved;
    }

    /** Full reset affects only the signed-in user; a backup is required first. */
    private void resetAllData() {
        String answer = JOptionPane.showInputDialog(this,
                "Reset ALL financial data for " + username + " across every month?\n"
                + "Expenses, goals, income, allowance, plans, custom categories and undo history will be cleared.\n"
                + "Your login and other accounts will not change. A backup will be saved first.\n\n"
                + "Type RESET to confirm:", "Reset all data", JOptionPane.WARNING_MESSAGE);
        if (!"RESET".equals(answer)) return;
        try {
            Path backup = data.backupAndReset(dataFile);
            unsaved = false;
            dashboardMonth = YearMonth.now();
            activeMonth = dashboardMonth;
            stopEditingExpense();
            stopEditingGoal();
            depositField.setText("");
            allowanceField.setText("0.00");
            allowanceMessageLabel.setText("Financial data reset.");
            planAmountField.setText("");
            fillCategoryBox();
            revealExpenseMonth(LocalDate.now());
            sortBox.setSelectedIndex(0);
            rebuildExpenseTable();
            refreshAll();
            reportMonthBox.setSelectedItem(dashboardMonth.format(MONTH_FORMAT));
            JOptionPane.showMessageDialog(this, "Financial data reset successfully.\nBackup: " + backup,
                    "Reset complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException | RuntimeException e) {
            rebuildExpenseTable();
            refreshAll();
            showError("Reset could not be completed. Your financial data has not been intentionally discarded. "
                    + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Putting the window together
    // ------------------------------------------------------------------

    private void createContents() {
        contentPane.add(createHeader(), BorderLayout.NORTH);

        tabbedPane = new JTabbedPane(SwingConstants.TOP);
        tabbedPane.addTab("Dashboard", null, createDashboardPanel(), null);
        tabbedPane.addTab("Expenses", null, createExpensesPanel(), null);
        tabbedPane.addTab("Savings Goals", null, createGoalsPanel(), null);
        tabbedPane.addTab("Allowance", null, createAllowancePanel(), null);
        tabbedPane.addTab("Reports", null, createReportsPanel(), null);
        tabbedPane.addChangeListener(e -> {
            refreshDateFloor();
            refreshCurrentMonth();
            refreshAll();
        });
        contentPane.add(tabbedPane, BorderLayout.CENTER);

        contentPane.add(createStatusBar(), BorderLayout.SOUTH);
    }

    private void refreshCurrentMonth() {
        YearMonth now = YearMonth.now();
        if (now.equals(activeMonth)) return;
        if (activeMonth.equals(expenseMonth)) {
            expenseMonth = now;
            refreshFilterLabel();
        }
        if (activeMonth.equals(dashboardMonth)) dashboardMonth = now;
        activeMonth = now;
        allowanceField.setText(MONEY.format(data.allowance(now)));
        rebuildExpenseTable();
    }

    private JPanel createHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BorderLayout(10, 0));
        header.setBackground(CARD);
        header.setBorder(new MatteBorder(0, 0, 1, 0, BORDER));

        JPanel left = new JPanel();
        left.setLayout(new FlowLayout(FlowLayout.LEFT, 14, 10));
        left.setOpaque(false);

        JLabel logo = new JLabel("Ledger");
        logo.setFont(TITLE_FONT);
        logo.setForeground(ACCENT);

        JLabel subtitle = new JLabel("Student financial tracker");
        subtitle.setFont(SMALL_FONT);
        subtitle.setForeground(MUTED);

        left.add(logo);
        left.add(subtitle);

        JPanel right = new JPanel();
        right.setLayout(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        right.setOpaque(false);

        JLabel userLabel = new JLabel("Signed in: " + username);
        userLabel.setFont(LABEL_FONT);
        userLabel.setForeground(MUTED);

        JButton logoutButton = new JButton("Log out");
        logoutButton.setFont(LABEL_FONT);
        logoutButton.setFocusPainted(false);
        logoutButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                logOut();
            }
        });

        right.add(userLabel);
        right.add(logoutButton);

        header.add(left, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JPanel createStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(CARD);
        bar.setBorder(new MatteBorder(1, 0, 0, 0, BORDER));

        JLabel hint = new JLabel("  ArrayList (records)  -  HashMap (monthly budget)  -  "
                + "Stack (undo)  -  Automatic local file saving");
        hint.setFont(SMALL_FONT);
        hint.setForeground(MUTED);
        hint.setBorder(new EmptyBorder(6, 8, 6, 8));

        bar.add(hint, BorderLayout.WEST);
        return bar;
    }

    // ------------------------------------------------------------------
    // Dashboard tab
    // ------------------------------------------------------------------

    private JPanel createDashboardPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBackground(BG);
        panel.setBorder(new EmptyBorder(14, 14, 14, 14));

        JPanel stats = new JPanel(new GridLayout(1, 4, 10, 0));
        stats.setOpaque(false);

        allowanceValueLabel = new JLabel("₱0.00");
        spentValueLabel = new JLabel("₱0.00");
        leftValueLabel = new JLabel("₱0.00");
        perDayValueLabel = new JLabel("₱0.00");

        stats.add(createStatCard("MONTHLY ALLOWANCE", allowanceValueLabel, ACCENT));
        stats.add(createStatCard("TOTAL SPENT", spentValueLabel, RED));
        stats.add(createStatCard("MONEY LEFT", leftValueLabel, GREEN));
        stats.add(createStatCard("LEFT PER DAY", perDayValueLabel, new Color(2, 132, 199)));

        panel.add(stats, BorderLayout.NORTH);

        JPanel middle = new JPanel(new BorderLayout(0, 8));
        middle.setOpaque(false);

        JLabel summaryTitle = new JLabel("Summary of your data");
        summaryTitle.setFont(BOLD_FONT);
        summaryTitle.setForeground(INK);
        JPanel summaryHeader = new JPanel(new BorderLayout(8, 0));
        summaryHeader.setOpaque(false);
        summaryHeader.add(summaryTitle, BorderLayout.WEST);
        JPanel monthChooser = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        monthChooser.setOpaque(false);
        monthChooser.add(smallLabel("SHOW MONTH"));
        dashboardMonthBox = new JComboBox<String>();
        dashboardMonthBox.addActionListener(e -> {
            if (!fillingDashboardMonths && dashboardMonthBox.getSelectedItem() != null) {
                dashboardMonth = parseFilterMonth(dashboardMonthBox.getSelectedItem().toString());
                refreshDashboard();
            }
        });
        monthChooser.add(dashboardMonthBox);
        JButton resetAllButton = new JButton("Reset all data...");
        resetAllButton.addActionListener(e -> resetAllData());
        monthChooser.add(resetAllButton);
        summaryHeader.add(monthChooser, BorderLayout.EAST);

        summaryArea = new JTextArea();
        summaryArea.setEditable(false);
        summaryArea.setFont(new Font("Menlo", Font.PLAIN, 12));
        summaryArea.setBackground(CARD);
        summaryArea.setForeground(INK);
        summaryArea.setBorder(new EmptyBorder(10, 12, 10, 12));

        JScrollPane scroll = new JScrollPane(summaryArea);
        scroll.setBorder(new MatteBorder(1, 1, 1, 1, BORDER));

        middle.add(summaryHeader, BorderLayout.NORTH);
        middle.add(scroll, BorderLayout.CENTER);

        panel.add(middle, BorderLayout.CENTER);
        return panel;
    }

    /** One small white card with a caption and a big number. */
    private JPanel createStatCard(String caption, JLabel valueLabel, Color valueColor) {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBackground(CARD);
        card.setBorder(new EmptyBorder(12, 14, 12, 14));

        JLabel captionLabel = new JLabel(caption);
        captionLabel.setFont(new Font("Helvetica Neue", Font.BOLD, 10));
        captionLabel.setForeground(MUTED);

        valueLabel.setFont(BIG_FONT);
        valueLabel.setForeground(valueColor);

        card.add(captionLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    // ------------------------------------------------------------------
    // Expenses tab
    // ------------------------------------------------------------------

    private JPanel createExpensesPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(BG);
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));

        panel.add(createExpenseForm(), BorderLayout.NORTH);
        panel.add(createExpenseList(), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createExpenseForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(CARD);
        form.setBorder(new EmptyBorder(12, 14, 12, 14));

        descriptionField = new JTextField();
        descriptionField.setPreferredSize(new Dimension(230, 28));
        descriptionField.setFont(LABEL_FONT);

        amountField = new JTextField();
        amountField.setPreferredSize(new Dimension(110, 28));
        amountField.setFont(LABEL_FONT);

        categoryBox = new JComboBox<String>();
        categoryBox.setPreferredSize(new Dimension(140, 28));
        categoryBox.setFont(LABEL_FONT);
        fillCategoryBox();

        dateSpinner = new JSpinner(expenseDateModel(LocalDate.now()));
        dateSpinner.setEditor(new JSpinner.DateEditor(dateSpinner, "MM/dd/yyyy"));
        ((JSpinner.DateEditor) dateSpinner.getEditor()).getFormat().setLenient(false);
        dateSpinner.setToolTipText("Today or a later date. Past dates cannot be used for new expenses.");
        dateSpinner.setPreferredSize(new Dimension(140, 28));
        dateSpinner.setFont(LABEL_FONT);

        addExpenseButton = new JButton("Add expense");
        addExpenseButton.setFont(BOLD_FONT);
        addExpenseButton.setBackground(ACCENT);
        addExpenseButton.setFocusPainted(false);
        addExpenseButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                addExpense();
            }
        });

        saveExpenseButton = new JButton("Save changes");
        saveExpenseButton.setFont(BOLD_FONT);
        saveExpenseButton.setVisible(false);
        saveExpenseButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                saveExpenseChanges();
            }
        });

        cancelExpenseButton = new JButton("Cancel");
        cancelExpenseButton.setFont(LABEL_FONT);
        cancelExpenseButton.setVisible(false);
        cancelExpenseButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                stopEditingExpense();
            }
        });

        JButton newCategoryButton = new JButton("New category...");
        newCategoryButton.setFont(LABEL_FONT);
        newCategoryButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                addNewCategory();
            }
        });

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 4, 12);

        gbc.gridy = 0;
        gbc.gridx = 0;
        form.add(smallLabel("DESCRIPTION"), gbc);
        gbc.gridx = 1;
        form.add(smallLabel("AMOUNT"), gbc);
        gbc.gridx = 2;
        form.add(smallLabel("CATEGORY"), gbc);
        gbc.gridx = 3;
        form.add(smallLabel("DATE"), gbc);

        gbc.gridy = 1;
        gbc.insets = new Insets(0, 0, 8, 12);
        gbc.gridx = 0;
        form.add(descriptionField, gbc);
        gbc.gridx = 1;
        form.add(amountField, gbc);
        gbc.gridx = 2;
        form.add(categoryBox, gbc);
        gbc.gridx = 3;
        form.add(dateSpinner, gbc);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(addExpenseButton);
        buttons.add(saveExpenseButton);
        buttons.add(cancelExpenseButton);
        JButton clearExpenseButton = new JButton("Clear");
        clearExpenseButton.addActionListener(e -> {
            stopEditingExpense();
            if (categoryBox.getItemCount() > 0) categoryBox.setSelectedIndex(0);
            descriptionField.requestFocusInWindow();
        });
        buttons.add(clearExpenseButton);
        buttons.add(newCategoryButton);

        gbc.gridy = 2;
        gbc.gridx = 0;
        gbc.gridwidth = 4;
        form.add(buttons, gbc);

        // An empty, stretchy cell on the right keeps the fields on the left.
        gbc.gridy = 1;
        gbc.gridx = 4;
        gbc.gridwidth = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        form.add(spacer, gbc);
        return form;
    }

    private JPanel createExpenseList() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(CARD);
        panel.setBorder(new EmptyBorder(10, 14, 12, 14));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        toolbar.setOpaque(false);

        searchField = new JTextField();
        searchField.setPreferredSize(new Dimension(180, 28));
        searchField.setFont(LABEL_FONT);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                rebuildExpenseTable();
            }

            public void removeUpdate(DocumentEvent e) {
                rebuildExpenseTable();
            }

            public void changedUpdate(DocumentEvent e) {
                rebuildExpenseTable();
            }
        });

        sortBox = new JComboBox<String>(new String[] {
            "Newest first", "Oldest first", "Amount: high to low",
            "Amount: low to high", "Description A-Z"
        });
        sortBox.setFont(LABEL_FONT);
        sortBox.setPreferredSize(new Dimension(170, 28));
        sortBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                rebuildExpenseTable();
            }
        });

        JButton editButton = new JButton("Edit");
        editButton.setFont(LABEL_FONT);
        editButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                startEditingExpense();
            }
        });

        JButton deleteButton = new JButton("Delete");
        deleteButton.setFont(LABEL_FONT);
        deleteButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                deleteExpense();
            }
        });

        undoButton = new JButton("Undo delete");
        undoButton.setFont(LABEL_FONT);
        undoButton.setEnabled(false);
        undoButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                undoDelete();
            }
        });

        expenseCountLabel = new JLabel("No expenses yet");
        expenseCountLabel.setFont(SMALL_FONT);
        expenseCountLabel.setForeground(MUTED);

        toolbar.add(smallLabel("SEARCH"));
        toolbar.add(searchField);
        toolbar.add(smallLabel("SORT BY"));
        toolbar.add(sortBox);
        toolbar.add(editButton);
        toolbar.add(deleteButton);
        toolbar.add(undoButton);
        toolbar.add(expenseCountLabel);
        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filterRow.setOpaque(false);
        JButton filterButton = new JButton("Filters / history...");
        filterButton.addActionListener(e -> showFilters());
        filterInfo = smallLabel("Current month");
        filterRow.add(filterButton);
        JButton clearFilters = new JButton("Clear filters");
        clearFilters.addActionListener(e -> {
            expenseMonth = YearMonth.now();
            expenseFrom = null;
            expenseTo = null;
            expenseCategory = null;
            searchField.setText("");
            sortBox.setSelectedIndex(0);
            refreshFilterLabel();
            rebuildExpenseTable();
        });
        filterRow.add(clearFilters);
        filterRow.add(filterInfo);
        JPanel top = new JPanel(new GridLayout(2, 1, 0, 4));
        top.setOpaque(false);
        top.add(toolbar);
        top.add(filterRow);

        expenseTableModel = new DefaultTableModel(new Object[][] {},
                new String[] {"Date", "Description", "Category", "Amount"}) {
            private static final long serialVersionUID = 1L;

            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        expenseTable = new JTable(expenseTableModel);
        expenseTable.setFont(LABEL_FONT);
        expenseTable.setRowHeight(26);
        expenseTable.setShowGrid(false);
        expenseTable.setFillsViewportHeight(true);
        expenseTable.getTableHeader().setFont(BOLD_FONT);
        expenseTable.getTableHeader().setReorderingAllowed(false);

        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        expenseTable.getColumnModel().getColumn(3).setCellRenderer(right);
        expenseTable.getColumnModel().getColumn(0).setPreferredWidth(110);
        expenseTable.getColumnModel().getColumn(1).setPreferredWidth(300);
        expenseTable.getColumnModel().getColumn(2).setPreferredWidth(140);
        expenseTable.getColumnModel().getColumn(3).setPreferredWidth(120);

        JScrollPane scroll = new JScrollPane(expenseTable);
        scroll.setBorder(new MatteBorder(1, 1, 1, 1, BORDER));

        panel.add(top, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    // ------------------------------------------------------------------
    // Savings goals tab
    // ------------------------------------------------------------------

    private JPanel createGoalsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(BG);
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));
        panel.add(createGoalForm(), BorderLayout.NORTH);
        panel.add(createGoalList(), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createGoalForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(CARD);
        form.setBorder(new EmptyBorder(12, 14, 12, 14));

        goalNameField = new JTextField();
        goalNameField.setPreferredSize(new Dimension(220, 28));
        goalNameField.setFont(LABEL_FONT);

        goalTargetField = new JTextField();
        goalTargetField.setPreferredSize(new Dimension(110, 28));
        goalTargetField.setFont(LABEL_FONT);

        goalDeadlineField = new JTextField();
        goalDeadlineField.setPreferredSize(new Dimension(120, 28));
        goalDeadlineField.setFont(LABEL_FONT);
        goalDeadlineField.setToolTipText("Enter MM/DD/YYYY, for example 05/31/2028.");

        addGoalButton = new JButton("Add goal");
        addGoalButton.setFont(BOLD_FONT);
        addGoalButton.setBackground(ACCENT);
        addGoalButton.setFocusPainted(false);
        addGoalButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                addGoal();
            }
        });

        saveGoalButton = new JButton("Save changes");
        saveGoalButton.setFont(BOLD_FONT);
        saveGoalButton.setVisible(false);
        saveGoalButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                saveGoalChanges();
            }
        });

        cancelGoalButton = new JButton("Cancel");
        cancelGoalButton.setFont(LABEL_FONT);
        cancelGoalButton.setVisible(false);
        cancelGoalButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                stopEditingGoal();
            }
        });

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 4, 12);

        gbc.gridy = 0;
        gbc.gridx = 0;
        form.add(smallLabel("GOAL NAME"), gbc);
        gbc.gridx = 1;
        form.add(smallLabel("TARGET AMOUNT"), gbc);
        gbc.gridx = 2;
        form.add(smallLabel("SAVE BY (MM/DD/YYYY)"), gbc);

        gbc.gridy = 1;
        gbc.insets = new Insets(0, 0, 8, 12);
        gbc.gridx = 0;
        form.add(goalNameField, gbc);
        gbc.gridx = 1;
        form.add(goalTargetField, gbc);
        gbc.gridx = 2;
        form.add(goalDeadlineField, gbc);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(addGoalButton);
        buttons.add(saveGoalButton);
        buttons.add(cancelGoalButton);
        JButton clearGoalButton = new JButton("Clear");
        clearGoalButton.addActionListener(e -> {
            stopEditingGoal();
            goalNameField.requestFocusInWindow();
        });
        buttons.add(clearGoalButton);

        gbc.gridy = 2;
        gbc.gridx = 0;
        gbc.gridwidth = 3;
        form.add(buttons, gbc);

        gbc.gridy = 1;
        gbc.gridx = 3;
        gbc.gridwidth = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        form.add(spacer, gbc);
        return form;
    }

    private JPanel createGoalList() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(CARD);
        panel.setBorder(new EmptyBorder(10, 14, 12, 14));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        toolbar.setOpaque(false);

        depositField = new JTextField();
        depositField.setPreferredSize(new Dimension(110, 28));
        depositField.setFont(LABEL_FONT);

        JButton depositButton = new JButton("Add money");
        depositButton.setFont(LABEL_FONT);
        depositButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                depositToGoal();
            }
        });

        JButton editButton = new JButton("Edit");
        editButton.setFont(LABEL_FONT);
        editButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                startEditingGoal();
            }
        });

        JButton deleteButton = new JButton("Delete");
        deleteButton.setFont(LABEL_FONT);
        deleteButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                deleteGoal();
            }
        });
        JButton adviceButton = new JButton("Savings advice");
        adviceButton.addActionListener(e -> showSavingsAdvice());

        goalInfoLabel = new JLabel("Select a goal to add money to it");
        goalInfoLabel.setFont(SMALL_FONT);
        goalInfoLabel.setForeground(MUTED);

        toolbar.add(smallLabel("ADD MONEY"));
        toolbar.add(depositField);
        toolbar.add(depositButton);
        JButton clearDepositButton = new JButton("Clear amount");
        clearDepositButton.addActionListener(e -> depositField.setText(""));
        toolbar.add(clearDepositButton);
        JPanel goalActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        goalActions.setOpaque(false);
        goalActions.add(editButton);
        goalActions.add(deleteButton);
        JButton resetGoalButton = new JButton("Reset progress...");
        resetGoalButton.addActionListener(e -> resetGoalProgress());
        goalActions.add(resetGoalButton);
        goalActions.add(adviceButton);
        goalActions.add(goalInfoLabel);

        goalTableModel = new DefaultTableModel(new Object[][] {},
                new String[] {"Goal", "Target", "Saved", "Progress", "Save by"}) {
            private static final long serialVersionUID = 1L;

            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        goalTable = new JTable(goalTableModel);
        goalTable.setFont(LABEL_FONT);
        goalTable.setRowHeight(26);
        goalTable.setShowGrid(false);
        goalTable.setFillsViewportHeight(true);
        goalTable.getTableHeader().setFont(BOLD_FONT);
        goalTable.getTableHeader().setReorderingAllowed(false);

        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        goalTable.getColumnModel().getColumn(1).setCellRenderer(right);
        goalTable.getColumnModel().getColumn(2).setCellRenderer(right);
        goalTable.getColumnModel().getColumn(3).setCellRenderer(right);
        goalTable.getColumnModel().getColumn(0).setPreferredWidth(240);
        goalTable.getColumnModel().getColumn(3).setPreferredWidth(90);

        JScrollPane scroll = new JScrollPane(goalTable);
        scroll.setBorder(new MatteBorder(1, 1, 1, 1, BORDER));

        JPanel goalTools = new JPanel(new GridLayout(2, 1, 0, 4));
        goalTools.setOpaque(false);
        goalTools.add(toolbar);
        goalTools.add(goalActions);
        panel.add(goalTools, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    // ------------------------------------------------------------------
    // Allowance tab
    // ------------------------------------------------------------------

    private JPanel createAllowancePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(BG);
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(CARD);
        form.setBorder(new EmptyBorder(12, 14, 12, 14));

        allowanceField = new JTextField();
        allowanceField.addActionListener(e -> saveAllowance());
        allowanceField.setPreferredSize(new Dimension(150, 28));
        allowanceField.setFont(LABEL_FONT);

        JButton saveButton = new JButton("Save allowance");
        saveButton.setFont(BOLD_FONT);
        saveButton.setBackground(ACCENT);
        saveButton.setFocusPainted(false);
        saveButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                saveAllowance();
            }
        });

        allowanceMessageLabel = new JLabel(" ");
        allowanceMessageLabel.setFont(SMALL_FONT);

        monthInfoLabel = new JLabel(" ");
        monthInfoLabel.setFont(LABEL_FONT);
        monthInfoLabel.setForeground(MUTED);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 6, 12);

        gbc.gridy = 0;
        gbc.gridx = 0;
        form.add(smallLabel("MONTHLY ALLOWANCE"), gbc);
        gbc.gridx = 1;
        form.add(smallLabel(""), gbc);

        gbc.gridy = 1;
        gbc.gridx = 0;
        form.add(allowanceField, gbc);
        gbc.gridx = 1;
        form.add(saveButton, gbc);
        gbc.gridx = 2;
        JButton incomeButton = new JButton("Add extra income");
        incomeButton.addActionListener(e -> addIncome());
        form.add(incomeButton, gbc);

        gbc.gridy = 2;
        gbc.gridx = 0;
        gbc.gridwidth = 3;
        JPanel allowanceActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        allowanceActions.setOpaque(false);
        JButton clearAllowanceButton = new JButton("Clear input");
        clearAllowanceButton.addActionListener(e -> {
            allowanceField.setText("");
            allowanceMessageLabel.setText("Input cleared; saved allowance unchanged.");
            allowanceMessageLabel.setForeground(MUTED);
        });
        JButton resetAllowanceButton = new JButton("Reset allowance...");
        resetAllowanceButton.addActionListener(e -> resetAllowance());
        JButton manageIncomeButton = new JButton("Manage income...");
        manageIncomeButton.addActionListener(e -> manageIncome());
        allowanceActions.add(clearAllowanceButton);
        allowanceActions.add(resetAllowanceButton);
        allowanceActions.add(manageIncomeButton);
        form.add(allowanceActions, gbc);

        gbc.gridy = 3;
        gbc.gridx = 0;
        gbc.gridwidth = 3;
        form.add(allowanceMessageLabel, gbc);

        gbc.gridy = 4;
        form.add(monthInfoLabel, gbc);

        gbc.gridy = 1;
        gbc.gridx = 3;
        gbc.gridwidth = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        form.add(spacer, gbc);

        panel.add(form, BorderLayout.NORTH);
        panel.add(createPlanPanel(), BorderLayout.CENTER);
        return panel;
    }

    /** Planned budget per category, kept in a HashMap. */
    private JPanel createPlanPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(CARD);
        panel.setBorder(new EmptyBorder(10, 14, 12, 14));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        toolbar.setOpaque(false);

        planCategoryBox = new JComboBox<String>();
        planCategoryBox.setFont(LABEL_FONT);
        planCategoryBox.setPreferredSize(new Dimension(140, 28));

        planAmountField = new JTextField();
        planAmountField.setPreferredSize(new Dimension(110, 28));
        planAmountField.setFont(LABEL_FONT);

        JButton setButton = new JButton("Set plan");
        setButton.setFont(LABEL_FONT);
        setButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                savePlanForCategory();
            }
        });

        JButton clearButton = new JButton("Reset monthly plan...");
        clearButton.setFont(LABEL_FONT);
        clearButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                clearPlan();
            }
        });

        toolbar.add(smallLabel("CATEGORY"));
        toolbar.add(planCategoryBox);
        toolbar.add(smallLabel("PLANNED AMOUNT"));
        toolbar.add(planAmountField);
        toolbar.add(setButton);
        JButton clearPlanInput = new JButton("Clear input");
        clearPlanInput.addActionListener(e -> planAmountField.setText(""));
        toolbar.add(clearPlanInput);
        JPanel planActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        planActions.setOpaque(false);
        JButton deletePlanButton = new JButton("Delete selected plan...");
        deletePlanButton.addActionListener(e -> deleteSelectedPlan());
        planActions.add(deletePlanButton);
        planActions.add(clearButton);

        planTableModel = new DefaultTableModel(new Object[][] {},
                new String[] {"Category", "Planned", "Actual", "Difference"}) {
            private static final long serialVersionUID = 1L;

            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        planTable = new JTable(planTableModel);
        planTable.setFont(LABEL_FONT);
        planTable.setRowHeight(26);
        planTable.setShowGrid(false);
        planTable.setFillsViewportHeight(true);
        planTable.getTableHeader().setFont(BOLD_FONT);
        planTable.getTableHeader().setReorderingAllowed(false);
        planTable.getSelectionModel().addListSelectionListener(e -> {
            int row = planTable.getSelectedRow();
            if (!e.getValueIsAdjusting() && row >= 0) {
                String category = planTable.getValueAt(row, 0).toString();
                planCategoryBox.setSelectedItem(category);
                Double amount = planned().get(category);
                planAmountField.setText(amount == null ? "" : MONEY.format(amount));
            }
        });

        JScrollPane scroll = new JScrollPane(planTable);
        scroll.setBorder(new MatteBorder(1, 1, 1, 1, BORDER));

        JPanel planTools = new JPanel(new GridLayout(2, 1, 0, 4));
        planTools.setOpaque(false);
        planTools.add(toolbar);
        planTools.add(planActions);
        panel.add(planTools, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        fillCategoryBox();
        return panel;
    }

    // ------------------------------------------------------------------
    // The "+" quick add button
    // ------------------------------------------------------------------


    // ------------------------------------------------------------------
    // Expenses: add, edit, delete
    // ------------------------------------------------------------------

    private void addExpense() {
        String description = descriptionField.getText().trim();
        if (description.length() == 0) {
            showError("Please type what you bought.");
            return;
        }

        double amount = readAmount(amountField.getText());
        if (amount <= 0) {
            showError("Please type an amount greater than zero.");
            return;
        }

        String category = (String) categoryBox.getSelectedItem();
        if (category == null) {
            showError("Please choose a category.");
            return;
        }

        LocalDate date = validatedExpenseDate();
        if (date == null) return;
        Expense expense = new Expense(date, description, category, amount);
        expenses.add(expense);
        revealExpenseMonth(expense.date);

        descriptionField.setText("");
        amountField.setText("");
        resetExpenseDate();
        rebuildExpenseTable();
        changed();
    }

    private void startEditingExpense() {
        int row = expenseTable.getSelectedRow();
        if (row < 0) {
            showError("Please click a row in the table first.");
            return;
        }

        Expense expense = shownExpenses.get(row);
        editingExpense = expense;

        descriptionField.setText(expense.description);
        amountField.setText(MONEY.format(expense.amount));
        categoryBox.setSelectedItem(expense.category);
        configureExpenseDate(expense.date, LocalDate.now());

        addExpenseButton.setVisible(false);
        saveExpenseButton.setVisible(true);
        cancelExpenseButton.setVisible(true);
        descriptionField.requestFocusInWindow();
    }

    private void saveExpenseChanges() {
        if (editingExpense == null) {
            return;
        }

        String description = descriptionField.getText().trim();
        double amount = readAmount(amountField.getText());
        String category = (String) categoryBox.getSelectedItem();

        if (description.length() == 0 || amount <= 0 || category == null) {
            showError("Please complete the description, amount and category.");
            return;
        }

        LocalDate date = validatedExpenseDate();
        if (date == null) return;
        Expense expense = editingExpense;
        expense.description = description;
        expense.amount = amount;
        expense.category = category;
        expense.date = date;
        revealExpenseMonth(expense.date);

        stopEditingExpense();
        rebuildExpenseTable();
        changed();
    }

    private void stopEditingExpense() {
        editingExpense = null;
        descriptionField.setText("");
        amountField.setText("");
        resetExpenseDate();
        addExpenseButton.setVisible(true);
        saveExpenseButton.setVisible(false);
        cancelExpenseButton.setVisible(false);
    }

    /** Removes the selected expense and pushes it on the undo stack. */
    private void deleteExpense() {
        int row = expenseTable.getSelectedRow();
        if (row < 0) {
            showError("Please click a row in the table first.");
            return;
        }

        Expense expense = shownExpenses.get(row);
        int answer = JOptionPane.showConfirmDialog(this,
                "Delete \"" + expense.description + "\"?", "Delete expense",
                JOptionPane.OK_CANCEL_OPTION);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }

        expenses.remove(expense);
        dashboardMonth = YearMonth.from(expense.date);
        deleted.push(expense);
        stopEditingExpense();
        rebuildExpenseTable();
        changed();
    }

    /** Takes the last deleted expense off the Stack and puts it back. */
    private void undoDelete() {
        if (deleted.isEmpty()) {
            return;
        }
        Expense expense = deleted.pop();
        expenses.add(expense);
        revealExpenseMonth(expense.date);
        rebuildExpenseTable();
        changed();
    }

    /** Show the month of the saved record, even if it differs from today's month. */
    private void revealExpenseMonth(LocalDate date) {
        dashboardMonth = YearMonth.from(date);
        // Clear only the temporary display filters, not any saved financial records.
        expenseMonth = dashboardMonth;
        expenseFrom = null;
        expenseTo = null;
        expenseCategory = null;
        if (searchField != null) searchField.setText("");
        if (filterInfo != null) refreshFilterLabel();
    }

    private void addNewCategory() {
        String name = JOptionPane.showInputDialog(this, "New category name:");
        if (name == null) {
            return;
        }
        name = name.trim();

        if (name.length() == 0) {
            showError("The category name cannot be empty.");
            return;
        }
        if (categories.stream().anyMatch(name::equalsIgnoreCase)) {
            showError("That category already exists.");
            return;
        }

        categories.add(name);
        fillCategoryBox();
        categoryBox.setSelectedItem(name);
        changed();
    }

    /** History and optional date/category filtering, without changing the tab layout. */
    private void showFilters() {
        JTextField month = new JTextField(expenseMonth == null ? "" : expenseMonth.format(MONTH_FORMAT));
        JTextField from = new JTextField(expenseFrom == null ? "" : expenseFrom.format(GOAL_DATE));
        JTextField to = new JTextField(expenseTo == null ? "" : expenseTo.format(GOAL_DATE));
        JComboBox<String> category = new JComboBox<String>();
        category.addItem("All categories");
        for (String name : categories) category.addItem(name);
        if (expenseCategory != null) category.setSelectedItem(expenseCategory);
        Object[] fields = {"Month (MM/YYYY, blank = all history)", month,
                "From date (MM/DD/YYYY, optional)", from,
                "To date (MM/DD/YYYY, optional)", to, "Category", category};
        while (JOptionPane.showConfirmDialog(this, fields, "Expense filters",
                JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            try {
                YearMonth m = parseFilterMonth(month.getText().trim());
                LocalDate f = parseFilterDate(from.getText().trim());
                LocalDate t = parseFilterDate(to.getText().trim());
                if (f != null && t != null && f.isAfter(t)) throw new IllegalArgumentException();
                expenseMonth = m;
                expenseFrom = f;
                expenseTo = t;
                expenseCategory = category.getSelectedIndex() == 0 ? null : (String) category.getSelectedItem();
                refreshFilterLabel();
                rebuildExpenseTable();
                return;
            } catch (RuntimeException e) {
                showError("Use MM/YYYY for the month and MM/DD/YYYY for dates.\nThe start date must not be later than the end date.");
            }
        }
    }

    static YearMonth parseFilterMonth(String input) {
        if (input.isEmpty()) return null;
        if (!input.matches("[0-9]{2}/[0-9]{4}")) throw new IllegalArgumentException("Use MM/YYYY");
        YearMonth result = YearMonth.parse(input, MONTH_FORMAT);
        if (result.getYear() < 1) throw new IllegalArgumentException("Invalid year");
        return result;
    }

    static LocalDate parseFilterDate(String input) {
        if (input.isEmpty()) return null;
        if (!input.matches("[0-9]{2}/[0-9]{2}/[0-9]{4}")) throw new IllegalArgumentException("Use MM/DD/YYYY");
        LocalDate result = LocalDate.parse(input, GOAL_DATE);
        if (result.getYear() < 1) throw new IllegalArgumentException("Invalid year");
        return result;
    }

    private void refreshFilterLabel() {
        filterInfo.setText((expenseMonth == null ? "All months" : expenseMonth.format(MONTH_FORMAT))
                + (expenseFrom == null ? "" : " from " + expenseFrom.format(GOAL_DATE))
                + (expenseTo == null ? "" : " to " + expenseTo.format(GOAL_DATE))
                + (expenseCategory == null ? "" : " | " + expenseCategory));
    }

    // ------------------------------------------------------------------
    // Showing the expenses in the table
    // ------------------------------------------------------------------

    /**
     * Rebuilds the table from the ArrayList.
     * First we keep only the rows that match the search box, then we sort them,
     * then we copy them into the table model.
     */
    private void rebuildExpenseTable() {
        if (expenseTableModel == null) {
            return;
        }

        ArrayList<Expense> result = new ArrayList<Expense>();
        String search = searchField == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);

        for (int i = 0; i < expenses.size(); i++) {
            Expense expense = expenses.get(i);
            if ((expenseMonth == null || YearMonth.from(expense.date).equals(expenseMonth))
                    && (expenseFrom == null || !expense.date.isBefore(expenseFrom))
                    && (expenseTo == null || !expense.date.isAfter(expenseTo))
                    && (expenseCategory == null || expenseCategory.equals(expense.category))
                    && (search.length() == 0 || matches(expense, search))) {
                result.add(expense);
            }
        }

        sortExpenses(result, sortBox == null ? 0 : sortBox.getSelectedIndex());

        expenseTableModel.setRowCount(0);
        shownExpenses.clear();

        for (int i = 0; i < result.size(); i++) {
            Expense expense = result.get(i);
            shownExpenses.add(expense);
            expenseTableModel.addRow(new Object[] {
                expense.date.format(GOAL_DATE),
                expense.description,
                expense.category,
                "₱" + MONEY.format(expense.amount)
            });
        }

        if (expenseCountLabel != null) {
            if (result.size() == 0) {
                expenseCountLabel.setText("No expenses to show");
            } else {
                expenseCountLabel.setText(result.size() + " expense(s)");
            }
        }
    }

    /** True when the search text appears in the description or the category. */
    static boolean matches(Expense expense, String search) {
        if (expense.description.toLowerCase(Locale.ROOT).contains(search)) {
            return true;
        }
        if (expense.category.toLowerCase(Locale.ROOT).contains(search)) {
            return true;
        }
        return expense.date.toString().contains(search)
                || expense.date.format(GOAL_DATE).contains(search)
                || String.format(Locale.ROOT, "%.2f", expense.amount).contains(search);
    }

    /**
     * Bubble sort. Simple on purpose: it is the sorting algorithm from the class.
     * mode 0 = newest first, 1 = oldest first, 2 = amount high to low,
     * 3 = amount low to high, 4 = description A-Z.
     */
    static void sortExpenses(ArrayList<Expense> list, int mode) {
        for (int i = 0; i < list.size() - 1; i++) {
            boolean swapped = false;
            for (int j = 0; j < list.size() - 1 - i; j++) {
                Expense first = list.get(j);
                Expense second = list.get(j + 1);
                boolean swap = false;

                if (mode == 0) {
                    swap = first.date.isBefore(second.date);
                } else if (mode == 1) {
                    swap = first.date.isAfter(second.date);
                } else if (mode == 2) {
                    swap = first.amount < second.amount;
                } else if (mode == 3) {
                    swap = first.amount > second.amount;
                } else {
                    swap = first.description.compareToIgnoreCase(second.description) > 0;
                }

                if (swap) {
                    list.set(j, second);
                    list.set(j + 1, first);
                    swapped = true;
                }
            }
            if (!swapped) break;
        }
    }

    // ------------------------------------------------------------------
    // Goals: add, edit, delete, deposit
    // ------------------------------------------------------------------

    private void addGoal() {
        String name = goalNameField.getText().trim();
        double target = readAmount(goalTargetField.getText());

        if (name.length() == 0) {
            showError("Please type a goal name.");
            return;
        }
        if (target <= 0) {
            showError("The target amount must be greater than zero.");
            return;
        }

        String deadline = readDeadline(goalDeadlineField.getText().trim(), LocalDate.now());
        if (deadline == null) {
            showError("Enter a valid deadline as MM/DD/YYYY (today or later).");
            return;
        }

        goals.add(new Goal(name, target, 0, deadline));

        goalNameField.setText("");
        goalTargetField.setText("");
        goalDeadlineField.setText("");
        rebuildGoalTable();
        changed();
    }

    private void startEditingGoal() {
        int row = goalTable.getSelectedRow();
        if (row < 0) {
            showError("Please click a goal in the table first.");
            return;
        }

        editingGoal = row;
        Goal goal = goals.get(row);

        goalNameField.setText(goal.name);
        goalTargetField.setText(MONEY.format(goal.target));
        goalDeadlineField.setText(goal.deadline == null ? "" : formatDeadline(goal.deadline));

        addGoalButton.setVisible(false);
        saveGoalButton.setVisible(true);
        cancelGoalButton.setVisible(true);
        goalNameField.requestFocusInWindow();
    }

    private void saveGoalChanges() {
        if (editingGoal < 0) {
            return;
        }

        String name = goalNameField.getText().trim();
        double target = readAmount(goalTargetField.getText());

        if (name.length() == 0 || target <= 0) {
            showError("Please type a name and a target above zero.");
            return;
        }

        Goal goal = goals.get(editingGoal);
        String entered = goalDeadlineField.getText().trim();
        String deadline = readDeadline(entered, LocalDate.now());
        // Existing overdue goals can still be edited without changing their deadline.
        if (deadline == null && entered.equals(formatDeadline(goal.deadline))) deadline = goal.deadline;
        if (deadline == null) {
            showError("Enter a valid deadline as MM/DD/YYYY (today or later).");
            return;
        }

        if (target < goal.saved) {
            showError("Target cannot be less than the amount already saved.");
            return;
        }
        goal.name = name;
        goal.target = target;
        goal.deadline = deadline;

        stopEditingGoal();
        rebuildGoalTable();
        changed();
    }

    private void stopEditingGoal() {
        editingGoal = -1;
        goalNameField.setText("");
        goalTargetField.setText("");
        goalDeadlineField.setText("");
        addGoalButton.setVisible(true);
        saveGoalButton.setVisible(false);
        cancelGoalButton.setVisible(false);
    }

    private void deleteGoal() {
        int row = goalTable.getSelectedRow();
        if (row < 0) {
            showError("Please click a goal in the table first.");
            return;
        }

        Goal goal = goals.get(row);
        int answer = JOptionPane.showConfirmDialog(this,
                "Delete the goal \"" + goal.name + "\"?"
                + (goal.saved > 0 ? "\n₱" + MONEY.format(goal.saved)
                + " will return to this month's available balance. Deposit history is kept." : ""), "Delete goal",
                JOptionPane.OK_CANCEL_OPTION);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }

        data.resetGoal(goal, LocalDate.now());
        goals.remove(goal);
        dashboardMonth = YearMonth.now();
        depositField.setText("");
        stopEditingGoal();
        rebuildGoalTable();
        changed();
    }

    private void resetGoalProgress() {
        int row = goalTable.getSelectedRow();
        if (row < 0) { showError("Select a savings goal first."); return; }
        Goal goal = goals.get(row);
        if (goal.saved == 0) { showError("This goal already has zero progress."); return; }
        if (JOptionPane.showConfirmDialog(this,
                "Reset progress for \"" + goal.name + "\"?\n₱" + MONEY.format(goal.saved)
                + " will return to this month's available balance.\nThe goal and its deposit history will be kept.",
                "Reset savings progress", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE)
                != JOptionPane.OK_OPTION) return;
        data.resetGoal(goal, LocalDate.now());
        dashboardMonth = YearMonth.now();
        depositField.setText("");
        stopEditingGoal();
        changed();
    }

    private void depositToGoal() {
        int row = goalTable.getSelectedRow();
        if (row < 0) {
            showError("Please click a goal in the table first.");
            return;
        }

        double amount = readAmount(depositField.getText());
        if (amount <= 0) {
            showError("Please type an amount greater than zero.");
            return;
        }

        Goal goal = goals.get(row);
        if (amount > data.balance(YearMonth.now()) + 0.000001) {
            showError("Not enough money left this month. Add allowance or income first.");
            return;
        }
        if (amount > goal.target - goal.saved + 0.000001) {
            showError("The deposit exceeds the amount still needed for this goal.");
            return;
        }
        goal.saved = LedgerData.cents(goal.saved + amount);
        data.deposits.add(new MoneyEntry(LocalDate.now(), goal.name, amount));
        dashboardMonth = YearMonth.now();
        depositField.setText("");

        rebuildGoalTable();
        changed();

        if (goal.saved >= goal.target) {
            JOptionPane.showMessageDialog(this,
                    "Goal reached! " + goal.name + " is fully saved.",
                    "Congratulations", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void showSavingsAdvice() {
        int row = goalTable.getSelectedRow();
        if (row < 0) {
            showError("Select a savings goal first.");
            return;
        }
        Goal goal = goals.get(row);
        LocalDate today = LocalDate.now();
        LocalDate deadline = storedDeadline(goal.deadline);
        double remaining = Math.max(0, goal.target - goal.saved);
        long days = daysUntilDeadline(today, deadline);
        String advice = remaining == 0 ? "Goal complete!"
                : days <= 0 ? "Deadline passed. Update the goal deadline."
                : "Still needed: ₱" + MONEY.format(remaining)
                + "\nSave about ₱" + MONEY.format(Math.ceil(remaining / days * 100) / 100)
                + " per day for " + days + " day(s).";
        JOptionPane.showMessageDialog(this, advice, goal.name, JOptionPane.INFORMATION_MESSAGE);
    }

    private void rebuildGoalTable() {
        if (goalTableModel == null) {
            return;
        }

        goalTableModel.setRowCount(0);
        for (int i = 0; i < goals.size(); i++) {
            Goal goal = goals.get(i);
            int percent = 0;
            if (goal.target > 0) {
                percent = (int) Math.min(100, Math.round(goal.saved / goal.target * 100));
            }
            goalTableModel.addRow(new Object[] {
                goal.name,
                "₱" + MONEY.format(goal.target),
                "₱" + MONEY.format(goal.saved),
                percent + "%",
                goal.deadline == null ? "-" : formatDeadline(goal.deadline)
            });
        }

        if (goalInfoLabel != null) {
            goalInfoLabel.setText(goals.size() == 0
                    ? "No goals yet"
                    : goals.size() + " goal(s)");
        }
    }

    // ------------------------------------------------------------------
    // Allowance and planned budget
    // ------------------------------------------------------------------

    private void saveAllowance() {
        double value = readAmount(allowanceField.getText());
        if (value <= 0) {
            allowanceMessageLabel.setForeground(RED);
            allowanceMessageLabel.setText("Please type an amount greater than zero.");
            return;
        }

        data.allowances.put(YearMonth.now(), value);
        dashboardMonth = YearMonth.now();
        boolean saved = changed();
        allowanceMessageLabel.setForeground(saved ? GREEN : RED);
        allowanceMessageLabel.setText(saved ? "Allowance saved." : "Not yet saved to disk.");
    }

    private void addIncome() {
        MoneyEntry entry = incomeForm(null);
        if (entry == null) return;
        data.incomes.add(entry);
        dashboardMonth = YearMonth.now();
        changed();
    }

    private MoneyEntry incomeForm(MoneyEntry existing) {
        JTextField source = new JTextField(existing == null ? "" : existing.description);
        JTextField value = new JTextField(existing == null ? "" : MONEY.format(existing.amount));
        while (true) {
            int action = JOptionPane.showOptionDialog(this,
                    new Object[] {"Income source", source, "Amount", value},
                    existing == null ? "Add extra income" : "Edit income (" + existing.date.format(GOAL_DATE) + ")",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null,
                    new String[] {"Save", "Clear", "Cancel"}, "Save");
            if (action == 1) { source.setText(""); value.setText(""); continue; }
            if (action != 0) return null;
            double amount = readAmount(value.getText());
            if (source.getText().trim().isEmpty() || amount <= 0) {
                showError("Enter a source and a valid positive amount.");
                continue;
            }
            return new MoneyEntry(existing == null ? LocalDate.now() : existing.date,
                    source.getText().trim(), amount);
        }
    }

    private void manageIncome() {
        while (true) {
            if (data.incomes.isEmpty()) { showError("There are no saved extra income records."); return; }
            DefaultTableModel model = new DefaultTableModel(new String[] {"Date", "Source", "Amount"}, 0) {
                public boolean isCellEditable(int r, int c) { return false; }
            };
            for (MoneyEntry entry : data.incomes)
                model.addRow(new Object[] {entry.date.format(GOAL_DATE), entry.description, "₱" + MONEY.format(entry.amount)});
            JTable table = new JTable(model);
            table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
            JScrollPane scroll = new JScrollPane(table);
            scroll.setPreferredSize(new Dimension(510, 200));
            int action = JOptionPane.showOptionDialog(this, scroll, "Extra income history (all months)",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null,
                    new String[] {"Edit", "Delete", "Close"}, "Close");
            if (action < 0 || action > 1) return;
            int row = table.getSelectedRow();
            if (row < 0) { showError("Select an income record first."); continue; }
            MoneyEntry entry = data.incomes.get(row);
            if (action == 0) {
                MoneyEntry edited = incomeForm(entry);
                if (edited == null) continue;
                data.incomes.set(row, edited);
            } else {
                if (JOptionPane.showConfirmDialog(this, "Delete \"" + entry.description + "\" (₱"
                        + MONEY.format(entry.amount) + ") from " + entry.date.format(GOAL_DATE) + "?\nIts month's balance will be recalculated.",
                        "Delete extra income", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE)
                        != JOptionPane.OK_OPTION) continue;
                data.incomes.remove(row);
            }
            dashboardMonth = YearMonth.from(entry.date);
            changed();
        }
    }

    private void resetAllowance() {
        YearMonth month = YearMonth.now();
        if (JOptionPane.showConfirmDialog(this, "Set the allowance for " + month.format(MONTH_FORMAT) + " to ₱0.00?\n"
                + "Expenses, extra income, savings and other months will stay unchanged.",
                "Reset allowance", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE)
                != JOptionPane.OK_OPTION) return;
        data.allowances.remove(month);
        allowanceField.setText("0.00");
        dashboardMonth = month;
        boolean saved = changed();
        allowanceMessageLabel.setForeground(saved ? GREEN : RED);
        allowanceMessageLabel.setText(saved ? "Allowance reset." : "Not yet saved to disk.");
    }

    private void savePlanForCategory() {
        String category = (String) planCategoryBox.getSelectedItem();
        if (category == null) {
            return;
        }

        double amount = readAmount(planAmountField.getText());
        if (amount <= 0) {
            showError("Please type a planned amount greater than zero.");
            return;
        }

        // HashMap put: if the key is already there, the value is replaced.
        planned().put(category, Double.valueOf(amount));
        dashboardMonth = YearMonth.now();
        planAmountField.setText("");
        rebuildPlanTable();
        changed();
    }

    private void clearPlan() {
        if (planned().isEmpty()) { showError("No budget plans for this month."); return; }
        if (JOptionPane.showConfirmDialog(this, "Delete all category budget plans for " + YearMonth.now().format(MONTH_FORMAT)
                + "?\nExpenses and other months will stay unchanged.", "Reset monthly plan",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
        planned().clear();
        planAmountField.setText("");
        dashboardMonth = YearMonth.now();
        rebuildPlanTable();
        changed();
    }

    private void deleteSelectedPlan() {
        int row = planTable.getSelectedRow();
        if (row < 0) { showError("Select a category row first."); return; }
        String category = planTable.getValueAt(row, 0).toString();
        if (!planned().containsKey(category)) { showError("This category has no saved plan."); return; }
        if (JOptionPane.showConfirmDialog(this, "Delete the " + category + " plan for " + YearMonth.now().format(MONTH_FORMAT)
                + "?\nIts actual expenses will remain.", "Delete budget plan",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
        planned().remove(category);
        planAmountField.setText("");
        dashboardMonth = YearMonth.now();
        changed();
    }

    private void rebuildPlanTable() {
        if (planTableModel == null) {
            return;
        }

        planTableModel.setRowCount(0);
        for (int i = 0; i < categories.size(); i++) {
            String category = categories.get(i);
            Double plannedAmount = planned().get(category);
            double plan = plannedAmount == null ? 0 : plannedAmount.doubleValue();
            double actual = spentInCategory(category);

            String difference;
            if (plan <= 0) {
                difference = "-";
            } else if (actual > plan) {
                difference = "-₱" + MONEY.format(actual - plan);
            } else {
                difference = "₱" + MONEY.format(plan - actual);
            }

            planTableModel.addRow(new Object[] {
                category,
                plan <= 0 ? "-" : "₱" + MONEY.format(plan),
                actual <= 0 ? "-" : "₱" + MONEY.format(actual),
                difference
            });
        }
    }

    // ------------------------------------------------------------------
    // Refresh every screen after the data changed
    // ------------------------------------------------------------------

    private void refreshAll() {
        updateDashboardMonths();
        refreshDashboard();
        rebuildGoalTable();
        rebuildPlanTable();
        updateReportMonths();
        rebuildReports();
        updateAllowanceInfo();

        if (undoButton != null) {
            undoButton.setEnabled(!deleted.isEmpty());
        }
    }

    private void updateDashboardMonths() {
        if (dashboardMonthBox == null) return;
        if (dashboardMonth == null) dashboardMonth = data.initialDashboardMonth(YearMonth.now());
        ArrayList<YearMonth> choices = data.months();
        if (!choices.contains(dashboardMonth)) choices.add(dashboardMonth);
        fillingDashboardMonths = true;
        dashboardMonthBox.removeAllItems();
        for (YearMonth choice : choices) dashboardMonthBox.addItem(choice.format(MONTH_FORMAT));
        dashboardMonthBox.setSelectedItem(dashboardMonth.format(MONTH_FORMAT));
        fillingDashboardMonths = false;
    }

    private void refreshDashboard() {
        if (allowanceValueLabel == null) {
            return;
        }

        YearMonth month = dashboardMonth == null ? YearMonth.now() : dashboardMonth;
        DashboardTotals totals = data.dashboardTotals(month, LocalDate.now());
        double allowance = totals.allowance;
        double extra = totals.income;
        double savings = totals.savings;
        double spent = totals.spent;
        double left = totals.balance;
        double perDay = totals.dailyBudget;

        allowanceValueLabel.setText("₱" + MONEY.format(allowance));
        spentValueLabel.setText("₱" + MONEY.format(spent));
        leftValueLabel.setText("₱" + MONEY.format(left));
        leftValueLabel.setForeground(left < 0 ? RED : GREEN);
        perDayValueLabel.setText(totals.remainingDays == 0 ? "—" : "₱" + MONEY.format(perDay));
        perDayValueLabel.setForeground(perDay < 0 ? RED : new Color(2, 132, 199));

        StringBuilder text = new StringBuilder();
        int count = totals.expenseCount;
        text.append("Month             : ").append(month.format(MONTH_FORMAT)).append("\n");
        if (!data.hasActivity(month)) {
            text.append("No saved financial records for this month.\n")
                    .append("Select another month above, or save an allowance / add an expense.\n\n");
        }
        text.append("Extra income      : ₱").append(MONEY.format(extra)).append("\n");
        text.append("Savings deposits  : ₱").append(MONEY.format(savings)).append("\n");
        text.append("Savings returned  : ₱").append(MONEY.format(data.savingsReturned(month))).append("\n");
        text.append("Expenses recorded : ").append(count).append("\n");
        text.append("Total spent      : ₱").append(MONEY.format(spent)).append("\n");

        if (count > 0) {
            text.append("Average expense  : ₱").append(MONEY.format(spent / count)).append("\n");
        }

        Expense biggest = biggestExpense(month);
        if (biggest != null) {
            text.append("Biggest expense  : ").append(biggest.description)
                    .append(" (₱").append(MONEY.format(biggest.amount)).append(")\n");
        }

        double availableFunds = allowance + extra + data.savingsReturned(month);
        if (availableFunds > 0) {
            long used = Math.round((spent + savings) / availableFunds * 100);
            text.append("Available funds used: ").append(used).append("%\n");
        }
        if (left < 0) text.append("WARNING: Spending and savings exceed this month's funds.\n");
        else if (availableFunds > 0 && left <= availableFunds * 0.1)
            text.append("WARNING: 10% or less of this month's funds remain.\n");

        text.append("\nSpending per category\n");
        text.append("---------------------\n");
        for (int i = 0; i < categories.size(); i++) {
            String category = categories.get(i);
            double amount = data.spent(month, category);
            if (amount > 0) {
                text.append(String.format("%-12s ₱%s%n", category, MONEY.format(amount)));
            }
        }

        text.append("\nPlanned budget (HashMap)\n");
        text.append("------------------------\n");
        Map<String, Double> monthPlan = data.plans.get(month);
        if (monthPlan == null || monthPlan.isEmpty()) {
            text.append("No plan set yet. Open the Allowance tab.\n");
        } else {
            for (int i = 0; i < categories.size(); i++) {
                String category = categories.get(i);
                Double amount = monthPlan.get(category);
                if (amount != null) {
                    text.append(String.format("%-12s ₱%s%n", category, MONEY.format(amount.doubleValue())));
                    if (data.spent(month, category) > amount)
                        text.append("  WARNING: category budget exceeded.\n");
                }
            }
        }

        if (!deleted.isEmpty()) {
            text.append("\nUndo stack       : ").append(deleted.size()).append(" deleted expense(s)\n");
        }

        summaryArea.setText(text.toString());
    }

    private void updateAllowanceInfo() {
        if (monthInfoLabel == null) {
            return;
        }

        YearMonth month = YearMonth.now();
        int daysInMonth = month.lengthOfMonth();
        int daysLeft = daysInMonth - LocalDate.now().getDayOfMonth() + 1;
        if (daysLeft < 1) {
            daysLeft = 1;
        }

        String monthName = month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));
        monthInfoLabel.setText(monthName + "  -  " + daysInMonth + " days in the month  -  "
                + daysLeft + " day(s) left");
    }

    // ------------------------------------------------------------------
    // Small calculations
    // ------------------------------------------------------------------

    /** Loops over the ArrayList and adds every amount. */
    private double totalSpent() {
        return data.spent(YearMonth.now(), null);
    }

    /** Loops again, but only adds the rows of one category. */
    private double spentInCategory(String category) {
        return data.spent(YearMonth.now(), category);
    }

    /** Linear search for the largest expense. */
    private Expense biggestExpense(YearMonth month) {
        Expense biggest = null;
        for (int i = 0; i < expenses.size(); i++) {
            Expense expense = expenses.get(i);
            if (YearMonth.from(expense.date).equals(month)
                    && (biggest == null || expense.amount > biggest.amount)) {
                biggest = expense;
            }
        }
        return biggest;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private JLabel smallLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Helvetica Neue", Font.BOLD, 10));
        label.setForeground(MUTED);
        return label;
    }

    /**
     * Copies the category names into both combo boxes.
     * The two boxes are created at different times, so each one is checked first.
     */
    private void fillCategoryBox() {
        if (categoryBox != null) {
            categoryBox.removeAllItems();
            for (int i = 0; i < categories.size(); i++) {
                categoryBox.addItem(categories.get(i));
            }
        }
        if (planCategoryBox != null) {
            planCategoryBox.removeAllItems();
            for (int i = 0; i < categories.size(); i++) {
                planCategoryBox.addItem(categories.get(i));
            }
        }
    }

    private LocalDate getDateFromSpinner() {
        Date value = (Date) dateSpinner.getValue();
        return value.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    static Date spinnerDate(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    /** Start at today, step one calendar day, and prohibit stepping into the past. */
    static SpinnerDateModel expenseDateModel(LocalDate today) {
        Date first = spinnerDate(today);
        return new SpinnerDateModel(first, first, null, Calendar.DAY_OF_MONTH) {
            @Override
            public void setCalendarField(int ignored) {
                super.setCalendarField(Calendar.DAY_OF_MONTH);
            }
        };
    }

    static boolean allowedExpenseDate(LocalDate date, LocalDate today) {
        return date != null && !date.isBefore(today);
    }

    /** A historical entry keeps its original date when its other details are edited. */
    private void configureExpenseDate(LocalDate value, LocalDate today) {
        SpinnerDateModel model = (SpinnerDateModel) dateSpinner.getModel();
        boolean historical = editingExpense != null && editingExpense.date.isBefore(today);
        model.setStart(spinnerDate(historical ? editingExpense.date : today));
        LocalDate selected = historical ? editingExpense.date
                : (allowedExpenseDate(value, today) ? value : today);
        dateSpinner.setValue(spinnerDate(selected));
        dateSpinner.setEnabled(!historical);
        ((JSpinner.DateEditor) dateSpinner.getEditor()).getTextField().setEditable(!historical);
        dateSpinner.setToolTipText(historical ? "Historical expense date is preserved."
                : "Today or a later date. Past dates cannot be used for new expenses.");
        dateFloor = today;
    }

    private void resetExpenseDate() {
        LocalDate today = LocalDate.now();
        configureExpenseDate(today, today);
        // Discard even an uncommitted date typed directly into the editor.
        ((JSpinner.DateEditor) dateSpinner.getEditor()).getTextField().setValue(spinnerDate(today));
    }

    private void refreshDateFloor() {
        if (dateSpinner == null) return;
        LocalDate today = LocalDate.now();
        if (!today.equals(dateFloor)) configureExpenseDate(getDateFromSpinner(), today);
    }

    /** Reads "1,250.50" or "1250" and returns 0 when it is not a number. */
    static double readAmount(String text) {
        try {
            String raw = text.trim();
            if (!raw.matches("([0-9]+|[0-9]{1,3}(,[0-9]{3})+)(\\.[0-9]{1,2})?")) return 0;
            String cleaned = raw.replace(",", "");
            double value = Double.parseDouble(cleaned);
            return Double.isFinite(value) && value <= 1000000000 ? value : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private LocalDate validatedExpenseDate() {
        LocalDate today = LocalDate.now();
        if (editingExpense != null && editingExpense.date.isBefore(today)) return editingExpense.date;
        try {
            dateSpinner.commitEdit();
            LocalDate date = getDateFromSpinner();
            if (!allowedExpenseDate(date, today)) throw new IllegalArgumentException();
            return date;
        } catch (Exception e) {
            configureExpenseDate(today, today);
            showError("Choose today's date or a later date. Past dates are not allowed.");
            return null;
        }
    }

    /** Strict MM/DD/YYYY input, saved as an unambiguous ISO date. */
    static String readDeadline(String text, LocalDate today) {
        try {
            if (!text.matches("[0-9]{2}/[0-9]{2}/[0-9]{4}")) return null;
            LocalDate date = LocalDate.parse(text, GOAL_DATE);
            return date.isBefore(today) ? null : date.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** Old month-only goals keep the final-day deadline used by the previous advice. */
    static LocalDate storedDeadline(String stored) {
        if (stored.matches("[0-9]{4}-[0-9]{2}")) return YearMonth.parse(stored).atEndOfMonth();
        return storedDate(stored);
    }

    /** Accept ISO storage and valid slash-format dates from older/custom builds. */
    static LocalDate storedDate(String stored) {
        return stored.indexOf('/') >= 0 ? parseFilterDate(stored) : LocalDate.parse(stored);
    }

    static YearMonth storedMonth(String stored) {
        return stored.indexOf('/') >= 0 ? parseFilterMonth(stored) : YearMonth.parse(stored);
    }

    static String formatDeadline(String stored) {
        return storedDeadline(stored).format(GOAL_DATE);
    }

    static long daysUntilDeadline(LocalDate today, LocalDate deadline) {
        return ChronoUnit.DAYS.between(today, deadline) + 1;
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Please check", JOptionPane.WARNING_MESSAGE);
    }

    private void logOut() {
        int answer = JOptionPane.showConfirmDialog(this, "Log out now?",
                "Log out", JOptionPane.OK_CANCEL_OPTION);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }

        if (unsaved && !saveData()) return;
        dispose();
        LoginDialog login = new LoginDialog();
        login.setVisible(true);
        if (login.isLoggedIn()) {
            openTracker(login.getUsername());
        } else {
            System.exit(0);
        }
    }


    // ------------------------------------------------------------------
    // Reports tab (the graph)
    // ------------------------------------------------------------------

    /**
     * Two simple bar charts built from Swing panels.
     */
    private JPanel createReportsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(BG);
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));

        categoryBarsPanel = new JPanel();
        categoryBarsPanel.setBackground(CARD);
        categoryBarsPanel.setLayout(new BoxLayout(categoryBarsPanel, BoxLayout.Y_AXIS));

        dailyBarsPanel = new JPanel();
        dailyBarsPanel.setBackground(CARD);
        dailyBarsPanel.setLayout(new BoxLayout(dailyBarsPanel, BoxLayout.Y_AXIS));

        JPanel charts = new JPanel(new GridLayout(1, 2, 12, 0));
        charts.setOpaque(false);
        charts.add(createChartCard("Spending per category", categoryBarsPanel));
        charts.add(createChartCard("Spending per day (selected month)", dailyBarsPanel));

        reportSummaryLabel = new JLabel(" ");
        reportSummaryLabel.setFont(LABEL_FONT);
        reportSummaryLabel.setForeground(MUTED);
        reportSummaryLabel.setBorder(new EmptyBorder(4, 4, 0, 4));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        toolbar.setOpaque(false);
        reportMonthBox = new JComboBox<String>();
        reportMonthBox.addActionListener(e -> {
            if (!fillingReportMonths) rebuildReports();
        });
        JButton exportButton = new JButton("Export CSV...");
        exportButton.addActionListener(e -> exportReport());
        toolbar.add(smallLabel("REPORT MONTH"));
        toolbar.add(reportMonthBox);
        toolbar.add(exportButton);
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(charts, BorderLayout.CENTER);
        panel.add(reportSummaryLabel, BorderLayout.SOUTH);
        return panel;
    }

    /** A white card with a title and a column of bars inside. */
    private JPanel createChartCard(String title, JPanel bars) {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(CARD);
        card.setBorder(new EmptyBorder(14, 16, 14, 16));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(BOLD_FONT);
        titleLabel.setForeground(INK);

        card.add(titleLabel, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(bars);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    private YearMonth reportMonth() {
        Object selected = reportMonthBox == null ? null : reportMonthBox.getSelectedItem();
        return selected == null ? YearMonth.now() : parseFilterMonth(selected.toString());
    }

    private void updateReportMonths() {
        if (reportMonthBox == null) return;
        YearMonth selected = reportMonth();
        fillingReportMonths = true;
        reportMonthBox.removeAllItems();
        for (YearMonth month : data.months()) reportMonthBox.addItem(month.format(MONTH_FORMAT));
        reportMonthBox.setSelectedItem(selected.format(MONTH_FORMAT));
        if (reportMonthBox.getSelectedItem() == null) reportMonthBox.setSelectedItem(YearMonth.now().format(MONTH_FORMAT));
        fillingReportMonths = false;
    }

    private void exportReport() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("ledger-" + reportMonth() + ".csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".csv"))
            file = new File(file.getParentFile(), file.getName() + ".csv");
        if (file.exists() && JOptionPane.showConfirmDialog(this, "Replace this CSV file?",
                "Export report", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            data.exportCsv(file.toPath(), reportMonth());
            JOptionPane.showMessageDialog(this, "Report exported to " + file.getName());
        } catch (IOException e) {
            showError("Could not export the report: " + e.getMessage());
        }
    }

    /** One bar: a label on the left, the bar in the middle, the amount on the right. */
    private void addBar(JPanel target, String labelText, double value, double max, String amountText, Color color) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(CARD);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.setBorder(new EmptyBorder(3, 0, 3, 0));

        JLabel name = new JLabel(labelText);
        name.setFont(SMALL_FONT);
        name.setForeground(INK);
        name.setPreferredSize(new Dimension(92, 18));

        ReportBar bar = new ReportBar(value, max, color);

        JLabel amount = new JLabel(amountText);
        amount.setFont(SMALL_FONT);
        amount.setForeground(MUTED);
        amount.setHorizontalAlignment(SwingConstants.RIGHT);
        amount.setPreferredSize(new Dimension(76, 18));

        row.add(name, BorderLayout.WEST);
        row.add(bar, BorderLayout.CENTER);
        row.add(amount, BorderLayout.EAST);
        target.add(row);
    }

    /** Stable color by category's saved ArrayList position, not its sorted rank. */
    private static final Color[] CATEGORY_COLORS = {
        new Color(22, 163, 74), new Color(37, 99, 235), new Color(234, 88, 12),
        new Color(124, 58, 237), new Color(13, 148, 136), new Color(190, 24, 93),
        new Color(161, 98, 7), new Color(8, 145, 178), new Color(220, 38, 38),
        new Color(71, 85, 105)
    };

    static Color categoryColor(int index) {
        if (index < 0) return ACCENT;
        if (index < CATEGORY_COLORS.length) return CATEGORY_COLORS[index];
        // Custom categories beyond the palette get a new hue automatically.
        return Color.getHSBColor((float) ((index * 0.61803398875) % 1), 0.78f, 0.75f);
    }

    /** Draw the actual color rather than letting Nimbus override a progress bar. */
    static class ReportBar extends JPanel {
        private final double fraction;
        private final Color color;

        ReportBar(double value, double max, Color color) {
            this.fraction = max <= 0 ? 0 : Math.max(0, Math.min(1, value / max));
            this.color = color;
            setBackground(new Color(238, 240, 246));
            setOpaque(true);
            setPreferredSize(new Dimension(120, 14));
        }

        @Override protected void paintComponent(java.awt.Graphics g) {
            super.paintComponent(g);
            g.setColor(color);
            if (fraction > 0) g.fillRect(0, 0, Math.max(1, (int) Math.round(getWidth() * fraction)), getHeight());
        }
    }

    private JLabel chartNote(String text) {
        JLabel label = new JLabel(text);
        label.setFont(SMALL_FONT);
        label.setForeground(MUTED);
        label.setBorder(new EmptyBorder(6, 2, 0, 0));
        return label;
    }

    /** Fills both charts from the ArrayList of expenses. */
    private void rebuildReports() {
        if (categoryBarsPanel == null) {
            return;
        }

        categoryBarsPanel.removeAll();
        dailyBarsPanel.removeAll();

        YearMonth month = reportMonth();
        ArrayList<Slice> byCategory = sortedCategoryTotals(month);
        double maxCategory = 0;
        for (int i = 0; i < byCategory.size(); i++) {
            maxCategory = Math.max(maxCategory, byCategory.get(i).amount);
        }
        if (byCategory.size() == 0) {
            categoryBarsPanel.add(chartNote("No expenses yet."));
        } else {
            for (int i = 0; i < byCategory.size(); i++) {
                Slice slice = byCategory.get(i);
                addBar(categoryBarsPanel, slice.name, slice.amount, maxCategory,
                        "\u20b1" + MONEY.format(slice.amount), categoryColor(categories.indexOf(slice.name)));
            }
        }

        ArrayList<Slice> byDay = dailyTotals(month);
        double maxDay = 0;
        for (int i = 0; i < byDay.size(); i++) {
            maxDay = Math.max(maxDay, byDay.get(i).amount);
        }
        for (int i = 0; i < byDay.size(); i++) {
            Slice slice = byDay.get(i);
            addBar(dailyBarsPanel, slice.name, slice.amount, maxDay,
                    slice.amount > 0 ? "\u20b1" + MONEY.format(slice.amount) : "-", ACCENT);
        }

        double total = data.spent(month, null);
        reportSummaryLabel.setText("<html>" + month.format(MONTH_FORMAT) + " | Income: ₱" + MONEY.format(data.allowance(month) + data.extraIncome(month))
                + " | Expenses: ₱" + MONEY.format(total)
                + " | Savings: ₱" + MONEY.format(data.savings(month))
                + " | Savings returned: ₱" + MONEY.format(data.savingsReturned(month))
                + " | Balance: ₱" + MONEY.format(data.balance(month)) + "</html>");

        categoryBarsPanel.revalidate();
        categoryBarsPanel.repaint();
        dailyBarsPanel.revalidate();
        dailyBarsPanel.repaint();
    }

    /** One slice of the graph: a name and its amount. */
    static class Slice {
        String name;
        double amount;

        Slice(String name, double amount) {
            this.name = name;
            this.amount = amount;
        }
    }

    /** Totals per category, biggest first (bubble sort again). */
    private ArrayList<Slice> sortedCategoryTotals(YearMonth month) {
        ArrayList<Slice> list = new ArrayList<Slice>();
        for (int i = 0; i < categories.size(); i++) {
            double amount = data.spent(month, categories.get(i));
            if (amount > 0) {
                list.add(new Slice(categories.get(i), amount));
            }
        }
        for (int i = 0; i < list.size() - 1; i++) {
            for (int j = 0; j < list.size() - 1 - i; j++) {
                if (list.get(j).amount < list.get(j + 1).amount) {
                    Slice temp = list.get(j);
                    list.set(j, list.get(j + 1));
                    list.set(j + 1, temp);
                }
            }
        }
        return list;
    }

    /** Totals for each day of the selected month, oldest first. */
    private ArrayList<Slice> dailyTotals(YearMonth month) {
        ArrayList<Slice> list = new ArrayList<Slice>();

        for (int i = 1; i <= month.lengthOfMonth(); i++) {
            LocalDate day = month.atDay(i);
            double total = 0;
            for (int j = 0; j < expenses.size(); j++) {
                if (expenses.get(j).date.equals(day)) {
                    total = total + expenses.get(j).amount;
                }
            }
            list.add(new Slice(day.format(GOAL_DATE), total));
        }
        return list;
    }

    // ------------------------------------------------------------------
    // The data classes (simple boxes that hold data)
    // ------------------------------------------------------------------

    /** All four dashboard cards use this single, consistent month calculation. */
    static class DashboardTotals {
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

    /** A dated income or transfer into savings. The history is independent of goal names. */
    static class MoneyEntry {
        final LocalDate date;
        final String description;
        final double amount;
        MoneyEntry(LocalDate date, String description, double amount) {
            this.date = date;
            this.description = description;
            this.amount = amount;
        }
    }

    /**
     * ArrayLists, HashMaps and the undo Stack remain the working data structures.
     * A single UTF-8 text snapshot per user is saved after every modification.
     * No database, external library or Java object serialization is needed.
     */
    static class LedgerData {
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
            Collections.addAll(categories, DEFAULT_CATEGORIES);
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
            Collections.addAll(categories, DEFAULT_CATEGORIES);
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
            return new Expense(storedDate(required(p, k + "date")),
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
                list.add(new MoneyEntry(storedDate(required(p, k + "date")),
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
                String deadline = storedDeadline(required(p, k + "deadline")).toString();
                goals.add(new Goal(required(p, k + "name"), amount(p, k + "target"),
                        amount(p, k + "saved"), deadline));
            }
            n = count(p, "month.count");
            for (int i = 0; i < n; i++) {
                String k = "month." + i + ".";
                YearMonth m = storedMonth(required(p, k + "date"));
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

    /** One expense row. */
    static class Expense {
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

    /** One savings goal. */
    static class Goal {
        String name;
        double target;
        double saved;
        String deadline;

        Goal(String name, double target, double saved, String deadline) {
            this.name = name;
            this.target = target;
            this.saved = saved;
            this.deadline = storedDeadline(deadline).toString();
        }
    }

    /** One user account for the login window. */
    static class User {
        String username;
        String password;

        User(String username, String password) {
            this.username = username;
            this.password = password;
        }

        /** Previous editions stored PBKDF2 hashes; do not mistake a hash for a password. */
        boolean verify(String candidate) {
            if (!password.startsWith("pbkdf2$")) return password.equals(candidate);
            try {
                String[] parts = password.split("\\$", -1);
                if (parts.length != 4) return false;
                int rounds = Integer.parseInt(parts[1]);
                if (rounds < 100000 || rounds > 2000000) return false;
                byte[] salt = java.util.Base64.getDecoder().decode(parts[2]);
                byte[] expected = java.util.Base64.getDecoder().decode(parts[3]);
                if (salt.length < 8 || expected.length != 32) return false;
                javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(candidate.toCharArray(), salt, rounds, 256);
                try {
                    byte[] actual = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
                    return java.security.MessageDigest.isEqual(expected, actual);
                } finally { spec.clearPassword(); }
            } catch (java.security.GeneralSecurityException | RuntimeException e) { return false; }
        }
    }
}

/**
 * The login window. It is a small JDialog with an absolute layout, the way a
 * WindowBuilder form with fixed coordinates looks.
 *
 * <p>Accounts are kept in an ArrayList and copied to {@code users.txt} so a new
 * account is still there the next time the program runs. A real application would
 * store a hashed password instead of plain text.</p>
 */
class LoginDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /** ArrayList of all accounts, loaded from users.txt. */
    static final ArrayList<FinancialTracker.User> users =
            new ArrayList<FinancialTracker.User>();

    /** users.txt lives in the home folder, so it works no matter where we start. */
    private static File userFile() {
        return new File(System.getProperty("user.home"), "ledger-users.txt");
    }

    private JPanel contentPane;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JLabel messageLabel;
    private boolean loggedIn = false;
    private boolean accountStorageAvailable = true;
    private String username = "";

    public LoginDialog() {
        setTitle("Ledger - Log in");
        setModal(true);
        setResizable(false);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setBounds(100, 100, 380, 330);
        setLocationRelativeTo(null);

        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(0, 0, 0, 0));
        contentPane.setLayout(null);
        contentPane.setBackground(Color.WHITE);
        setContentPane(contentPane);

        JLabel title = new JLabel("Ledger");
        title.setFont(new Font("Helvetica Neue", Font.BOLD, 24));
        title.setForeground(new Color(79, 70, 229));
        title.setBounds(30, 18, 200, 32);
        contentPane.add(title);

        JLabel subtitle = new JLabel("Log in to open your tracker");
        subtitle.setFont(new Font("Helvetica Neue", Font.PLAIN, 12));
        subtitle.setForeground(new Color(100, 116, 139));
        subtitle.setBounds(30, 50, 300, 20);
        contentPane.add(subtitle);

        JLabel userLabel = new JLabel("USERNAME");
        userLabel.setFont(new Font("Helvetica Neue", Font.BOLD, 10));
        userLabel.setForeground(new Color(100, 116, 139));
        userLabel.setBounds(30, 84, 200, 16);
        contentPane.add(userLabel);

        usernameField = new JTextField();
        usernameField.setFont(new Font("Helvetica Neue", Font.PLAIN, 13));
        usernameField.setBounds(30, 102, 300, 32);
        contentPane.add(usernameField);

        JLabel passLabel = new JLabel("PASSWORD");
        passLabel.setFont(new Font("Helvetica Neue", Font.BOLD, 10));
        passLabel.setForeground(new Color(100, 116, 139));
        passLabel.setBounds(30, 142, 200, 16);
        contentPane.add(passLabel);

        passwordField = new JPasswordField();
        passwordField.setFont(new Font("Helvetica Neue", Font.PLAIN, 13));
        passwordField.setBounds(30, 160, 300, 32);
        contentPane.add(passwordField);

        messageLabel = new JLabel(" ");
        messageLabel.setFont(new Font("Helvetica Neue", Font.PLAIN, 11));
        messageLabel.setForeground(new Color(185, 28, 28));
        messageLabel.setBounds(30, 198, 320, 18);
        contentPane.add(messageLabel);

        JButton loginButton = new JButton("Log in");
        loginButton.setFont(new Font("Helvetica Neue", Font.BOLD, 12));
        loginButton.setBackground(new Color(79, 70, 229));
        loginButton.setFocusPainted(false);
        loginButton.setBounds(30, 224, 140, 34);
        loginButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                tryLogin();
            }
        });
        contentPane.add(loginButton);
        getRootPane().setDefaultButton(loginButton);

        JButton createButton = new JButton("Create account");
        createButton.setFont(new Font("Helvetica Neue", Font.PLAIN, 12));
        createButton.setFocusPainted(false);
        createButton.setBounds(180, 224, 150, 34);
        createButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                createAccount();
            }
        });
        contentPane.add(createButton);

        JLabel hint = new JLabel("Demo account:  student / 1234");
        hint.setFont(new Font("Helvetica Neue", Font.PLAIN, 10));
        hint.setForeground(new Color(148, 163, 184));
        hint.setBounds(30, 268, 320, 16);
        contentPane.add(hint);
        loadUsers();
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public String getUsername() {
        return username;
    }

    // ------------------------------------------------------------------
    // Logging in
    // ------------------------------------------------------------------

    private void tryLogin() {
        String user = usernameField.getText().trim();
        String pass = new String(passwordField.getPassword());

        if (user.length() == 0 || pass.length() == 0) {
            showMessage("Please type your username and password.");
            return;
        }

        // Linear search through the ArrayList of users.
        for (int i = 0; i < users.size(); i++) {
            FinancialTracker.User account = users.get(i);
            if (account.username.equalsIgnoreCase(user) && account.verify(pass)) {
                loggedIn = true;
                username = account.username;
                dispose();
                return;
            }
        }

        showMessage("Wrong username or password.");
    }

    private void createAccount() {
        String user = usernameField.getText().trim();
        String pass = new String(passwordField.getPassword());

        if (!accountStorageAvailable) {
            showMessage("Account file unavailable. Please restart Ledger.");
            return;
        }
        if (!user.matches("[A-Za-z0-9_.-]{3,32}")) {
            showMessage("Use 3-32 letters, numbers, dots, _ or -.");
            return;
        }
        if (pass.length() < 4 || !pass.equals(pass.trim()) || pass.contains("\n") || pass.contains("\r")) {
            showMessage("Use 4+ characters without outer spaces.");
            return;
        }
        if (findUser(user) != null) {
            showMessage("That username is already taken.");
            return;
        }

        users.add(new FinancialTracker.User(user, pass));
        if (!saveUsers()) {
            users.remove(users.size() - 1);
            return;
        }

        messageLabel.setForeground(new Color(21, 128, 61));
        messageLabel.setText("Account created. You can log in now.");
    }

    private FinancialTracker.User findUser(String name) {
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).username.equalsIgnoreCase(name)) {
                return users.get(i);
            }
        }
        return null;
    }

    private void showMessage(String text) {
        messageLabel.setForeground(new Color(185, 28, 28));
        messageLabel.setText(text);
    }

    // ------------------------------------------------------------------
    // users.txt (one "username;password" per line)
    // ------------------------------------------------------------------

    private void loadUsers() {
        users.clear();

        File file = userFile();
        if (!file.exists()) {
            // First run: create two demo accounts.
            users.add(new FinancialTracker.User("student", "1234"));
            users.add(new FinancialTracker.User("admin", "admin"));
            saveUsers();
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line = reader.readLine();
            while (line != null) {
                int split = line.indexOf(';');
                if (split > 0) {
                    String name = line.substring(0, split).trim();
                    String pass = line.substring(split + 1).trim();
                    if (name.length() > 0) {
                        users.add(new FinancialTracker.User(name, pass));
                    }
                }
                line = reader.readLine();
            }
        } catch (Exception e) {
            users.clear();
            accountStorageAvailable = false;
            showMessage("Could not read users.txt");
        }
    }

    private boolean saveUsers() {
        Path temp = null;
        try {
            Path file = userFile().toPath();
            temp = Files.createTempFile(file.toAbsolutePath().getParent(), "ledger-users-", ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                for (FinancialTracker.User account : users) {
                    writer.write(account.username + ";" + account.password + "\n");
                }
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception e) {
            showMessage("Could not save users.txt");
            return false;
        } finally {
            if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
        }
    }
}

