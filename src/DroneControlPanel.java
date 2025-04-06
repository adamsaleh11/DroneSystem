import javax.swing.*;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class DroneControlPanel extends JFrame {
    // Terminal theme colors
    private static final Color BG_COLOR = new Color(18, 18, 18);
    private static final Color TEXT_COLOR = new Color(204, 204, 204);
    private static final Color HEADER_COLOR = new Color(129, 162, 190);
    private static final Color VALUE_COLOR = new Color(95, 175, 95);
    private static final Color ALERT_COLOR = new Color(240, 113, 120);
    private static final Color HIGHLIGHT_COLOR = new Color(247, 200, 92);

    private final Map<Integer, DroneSubsystem> drones;
    private final Map<Integer, JTextPane> droneStatusPanes;
    private final JPanel droneControlsPanel;
    private final Timer statusUpdateTimer;

    public DroneControlPanel(Map<Integer, DroneSubsystem> drones) {
        this.drones = drones;
        this.droneStatusPanes = new HashMap<>();

        setTitle("Drone Control Panel");
        setSize(600, 500);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dispose();
                // Let the main program handle drone shutdown
            }
        });

        setLayout(new BorderLayout());
        getContentPane().setBackground(BG_COLOR);

        // Header
        JLabel headerLabel = new JLabel("DRONE CONTROL PANEL");
        headerLabel.setFont(new Font("Consolas", Font.BOLD, 16));
        headerLabel.setForeground(HEADER_COLOR);
        headerLabel.setHorizontalAlignment(JLabel.CENTER);
        headerLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        add(headerLabel, BorderLayout.NORTH);

        // Drone controls section
        droneControlsPanel = new JPanel();
        droneControlsPanel.setLayout(new BoxLayout(droneControlsPanel, BoxLayout.Y_AXIS));
        droneControlsPanel.setBackground(BG_COLOR);

        // Create control panels for each drone
        for (Integer droneId : drones.keySet()) {
            droneControlsPanel.add(createDronePanel(droneId));
        }

        JScrollPane scrollPane = new JScrollPane(droneControlsPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setBackground(BG_COLOR);
        add(scrollPane, BorderLayout.CENTER);

        // Create a status bar at the bottom
        JLabel statusBar = new JLabel(" Ready");
        statusBar.setFont(new Font("Consolas", Font.PLAIN, 12));
        statusBar.setForeground(TEXT_COLOR);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(60, 60, 60)),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        statusBar.setBackground(new Color(30, 30, 30));
        statusBar.setOpaque(true);
        add(statusBar, BorderLayout.SOUTH);

        // Timer to update drone status displays
        statusUpdateTimer = new Timer(1000, e -> updateAllDroneStatus());
        statusUpdateTimer.start();

        setVisible(true);
    }

    private JPanel createDronePanel(int droneId) {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.setBackground(new Color(25, 25, 25));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(50, 50, 50)),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        // Drone header with ID
        JLabel droneLabel = new JLabel("Drone " + droneId);
        droneLabel.setFont(new Font("Consolas", Font.BOLD, 14));
        droneLabel.setForeground(HIGHLIGHT_COLOR);

        // Status display
        JTextPane statusPane = createStatusPane();
        droneStatusPanes.put(droneId, statusPane);

        // Control buttons panel
        JPanel buttonsPanel = new JPanel();
        buttonsPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        buttonsPanel.setBackground(new Color(25, 25, 25));

        // Fault injection button
        JButton faultButton = createStyledButton("Inject Fault", ALERT_COLOR);
        faultButton.addActionListener(e -> {
            DroneSubsystem drone = drones.get(droneId);
            if (drone != null) {
                drone.injectFault();
                updateDroneStatus(droneId);

                // Visual feedback
                faultButton.setEnabled(false);
                new Timer(2000, ev -> faultButton.setEnabled(true)).start();
            }
        });

        // Add fault button to panel
        buttonsPanel.add(faultButton);

        // Assemble the drone panel
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(new Color(25, 25, 25));
        topPanel.add(droneLabel, BorderLayout.WEST);

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(statusPane, BorderLayout.CENTER);
        panel.add(buttonsPanel, BorderLayout.SOUTH);

        return panel;
    }


    private JTextPane createStatusPane() {
        JTextPane textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setBackground(new Color(30, 30, 30));
        textPane.setFont(new Font("Consolas", Font.PLAIN, 12));
        textPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Set initial height
        textPane.setPreferredSize(new Dimension(500, 50));

        return textPane;
    }

    private JButton createStyledButton(String text, Color primaryColor) {
        JButton button = new JButton(text);
        button.setFont(new Font("Consolas", Font.BOLD, 12));
        button.setForeground(Color.WHITE);
        button.setBackground(primaryColor);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);

        // Add hover effect
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(new Color(
                        Math.min(primaryColor.getRed() + 20, 255),
                        Math.min(primaryColor.getGreen() + 20, 255),
                        Math.min(primaryColor.getBlue() + 20, 255)
                ));
            }

            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(primaryColor);
            }
        });

        return button;
    }

    private void updateAllDroneStatus() {
        for (Integer droneId : drones.keySet()) {
            updateDroneStatus(droneId);
        }
    }

    private void updateDroneStatus(int droneId) {
        JTextPane statusPane = droneStatusPanes.get(droneId);
        DroneSubsystem drone = drones.get(droneId);

        if (statusPane != null && drone != null) {
            StyledDocument doc = statusPane.getStyledDocument();

            try {
                doc.remove(0, doc.getLength());
            } catch (Exception e) {
                e.printStackTrace();
            }

            Style defaultStyle = statusPane.addStyle("default", null);
            StyleConstants.setForeground(defaultStyle, TEXT_COLOR);

            Style stateStyle = statusPane.addStyle("state", null);
            Color stateColor;

            switch (drone.getCurrentState()) {
                case IDLE:
                    stateColor = VALUE_COLOR;
                    break;
                case OFFLINE:
                    stateColor = ALERT_COLOR;
                    break;
                case EN_ROUTE:
                case RETURNING:
                    stateColor = HIGHLIGHT_COLOR;
                    break;
                case DROPPING_AGENT:
                    stateColor = new Color(130, 180, 255);
                    break;
                default:
                    stateColor = TEXT_COLOR;
            }

            StyleConstants.setForeground(stateStyle, stateColor);
            StyleConstants.setBold(stateStyle, true);

            try {
                doc.insertString(doc.getLength(), "State: ", defaultStyle);
                doc.insertString(doc.getLength(), drone.getCurrentState().toString(), stateStyle);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void shutdown() {
        if (statusUpdateTimer != null) {
            statusUpdateTimer.stop();
        }
        dispose();
    }

    public static void addControlPanelToExistingApplication(Map<Integer, DroneSubsystem> drones) {
        SwingUtilities.invokeLater(() -> new DroneControlPanel(drones));
    }
}
