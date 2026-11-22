import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class LoginDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /** ArrayList of all accounts, loaded from users.txt. */
    static final ArrayList<User> users =
            new ArrayList<User>();

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
        setBounds(100, 100, 380, 350);
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

        JCheckBox showPass = new JCheckBox("Show password");
        showPass.setFont(new Font("Helvetica Neue", Font.PLAIN, 11));
        showPass.setOpaque(false);
        showPass.setForeground(new Color(100, 116, 139));
        showPass.setBounds(30, 194, 140, 20);
        showPass.addActionListener(e -> {
            passwordField.setEchoChar(showPass.isSelected() ? (char) 0 : '\u2022');
        });
        contentPane.add(showPass);

        messageLabel = new JLabel(" ");
        messageLabel.setFont(new Font("Helvetica Neue", Font.PLAIN, 11));
        messageLabel.setForeground(new Color(185, 28, 28));
        messageLabel.setBounds(30, 216, 320, 18);
        contentPane.add(messageLabel);

        JButton loginButton = new JButton("Log in");
        loginButton.setFont(new Font("Helvetica Neue", Font.BOLD, 12));
        loginButton.setBackground(new Color(79, 70, 229));
        loginButton.setFocusPainted(false);
        loginButton.setBounds(30, 242, 140, 34);
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
        createButton.setBounds(180, 242, 150, 34);
        createButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                createAccount();
            }
        });
        contentPane.add(createButton);

        JLabel hint = new JLabel("Demo account:  student / 1234");
        hint.setFont(new Font("Helvetica Neue", Font.PLAIN, 10));
        hint.setForeground(new Color(148, 163, 184));
        hint.setBounds(30, 284, 320, 16);
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
            User account = users.get(i);
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

        users.add(new User(user, pass));
        if (!saveUsers()) {
            users.remove(users.size() - 1);
            return;
        }

        messageLabel.setForeground(new Color(21, 128, 61));
        messageLabel.setText("Account created. You can log in now.");
    }

    private User findUser(String name) {
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
            users.add(new User("student", "1234"));
            users.add(new User("admin", "admin"));
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
                        users.add(new User(name, pass));
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
                for (User account : users) {
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
