import javax.swing.*;
import javax.swing.Timer;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.List;

public class SchedulerMonitorGUI extends JFrame {
    // Terminal colors
    private static final Color BG_COLOR = new Color(18, 18, 18);
    private static final Color TEXT_COLOR = new Color(204, 204, 204);
    private static final Color HEADER_COLOR = new Color(129, 162, 190);
    private static final Color VALUE_COLOR = new Color(95, 175, 95);
    private static final Color ALERT_COLOR = new Color(240, 113, 120);
    private static final Color HIGHLIGHT_COLOR = new Color(247, 200, 92);

    private final Set<Integer> printedFaults = new HashSet<>();

    private JTextPane dronesArea;
    private JTextPane pendingArea;
    private JTextPane completedArea;
    private JTextPane faultArea;
    private final Map<Integer, Scheduler.DroneStatus> allDrones;
    private final Queue<Incident> pendingIncidents;
    private final List<Incident> completedIncidents;
    private final Scheduler scheduler;
    private final JLabel elapsedTimeLabel = new JLabel("Elapsed Time: 00:00");

    public SchedulerMonitorGUI(Map<Integer, Scheduler.DroneStatus> drones,
                               Queue<Incident> pending,
                               List<Incident> completed,
                               Scheduler scheduler) {
        this.allDrones = drones;
        this.pendingIncidents = pending;
        this.completedIncidents = completed;
        this.scheduler = scheduler;

        setTitle("Scheduler Monitor");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(5, 5));
        getContentPane().setBackground(BG_COLOR);

        // Create styled text panes instead of JTextArea
        dronesArea = createStyledTextPane();
        pendingArea = createStyledTextPane();
        completedArea = createStyledTextPane();
        faultArea = createStyledTextPane();

        // Add panels with titles
        elapsedTimeLabel.setFont(new Font("Consolas", Font.BOLD, 16));
        elapsedTimeLabel.setForeground(HEADER_COLOR);
        elapsedTimeLabel.setHorizontalAlignment(SwingConstants.LEFT);
        elapsedTimeLabel.setBorder(BorderFactory.createEmptyBorder(10, 15, 5, 0));
        elapsedTimeLabel.setBackground(BG_COLOR);
        elapsedTimeLabel.setOpaque(true);
        add(elapsedTimeLabel, BorderLayout.NORTH);
        JPanel gridPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        gridPanel.setBackground(BG_COLOR);

        gridPanel.add(createTitledPanel("FAULT REPORTS", faultArea));
        gridPanel.add(createTitledPanel("DRONES STATUS", dronesArea));
        gridPanel.add(createTitledPanel("PENDING INCIDENTS", pendingArea));
        gridPanel.add(createTitledPanel("COMPLETED INCIDENTS", completedArea));

        add(gridPanel, BorderLayout.CENTER);

        // Update more frequently for responsive UI
        new Timer(100, (ActionEvent e) -> updateDisplays()).start();

        setVisible(true);
    }

    private JTextPane createStyledTextPane() {
        JTextPane textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setBackground(BG_COLOR);
        textPane.setFont(new Font("Consolas", Font.PLAIN, 12));
        return textPane;
    }

    private JPanel createTitledPanel(String title, JTextPane textPane) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_COLOR);
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JLabel titleLabel = new JLabel(" " + title + " ");
        titleLabel.setFont(new Font("Consolas", Font.BOLD, 14));
        titleLabel.setForeground(HEADER_COLOR);
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        titleLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, HEADER_COLOR),
                BorderFactory.createEmptyBorder(3, 0, 3, 0)
        ));

        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(new JScrollPane(textPane), BorderLayout.CENTER);
        return panel;
    }

    private void updateDisplays() {
        elapsedTimeLabel.setText(scheduler.getElapsedTimeFormatted());
        updateDronesArea();
        updatePendingArea();
        updateCompletedArea();
        updateFaultArea();
    }

    private void updateFaultArea() {
        StyledDocument doc = faultArea.getStyledDocument();

        Style defaultStyle = faultArea.addStyle("default", null);
        StyleConstants.setForeground(defaultStyle, TEXT_COLOR);

        Style idStyle = faultArea.addStyle("id", null);
        StyleConstants.setForeground(idStyle, HIGHLIGHT_COLOR);
        StyleConstants.setBold(idStyle, true);

        Style errorStyle = faultArea.addStyle("error", null);
        StyleConstants.setForeground(errorStyle, ALERT_COLOR);

        Style fixStyle = faultArea.addStyle("fix", null);
        StyleConstants.setForeground(fixStyle, VALUE_COLOR);

        try {
            for (Map.Entry<Integer, Scheduler.DroneStatus> entry : allDrones.entrySet()) {
                int id = entry.getKey();
                Scheduler.DroneStatus status = entry.getValue();

                String current = status.faultMessage;
                if (current != null) {
                    try {
                        doc.insertString(doc.getLength(), "Drone ", defaultStyle);
                        doc.insertString(doc.getLength(), String.valueOf(id), idStyle);
                        doc.insertString(doc.getLength(), ": ", defaultStyle);
                        doc.insertString(doc.getLength(), current + "\n", errorStyle);

                        String fix = diagnoseFix(current);
                        doc.insertString(doc.getLength(), "Fix: ", defaultStyle);
                        doc.insertString(doc.getLength(), fix + "\n\n", fixStyle);

                        // Auto-scroll to bottom
                        faultArea.setCaretPosition(doc.getLength());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    // Clear it so it gets printed only once
                    status.faultMessage = null;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private String diagnoseFix(String msg) {
        msg = msg.toLowerCase();
        if (msg.contains("nozzle")) return "Force nozzle reset and return to base.";
        if (msg.contains("stuck")) return "Initiate return-to-base maneuver.";
        if (msg.contains("packet loss")) return "Re-establish communication.";
        return "Manual inspection required.";
    }

    private void updateDronesArea() {
        StyledDocument doc = dronesArea.getStyledDocument();

        // Define styles
        Style defaultStyle = dronesArea.addStyle("default", null);
        StyleConstants.setForeground(defaultStyle, TEXT_COLOR);

        Style valueStyle = dronesArea.addStyle("value", null);
        StyleConstants.setForeground(valueStyle, VALUE_COLOR);

        Style highlightStyle = dronesArea.addStyle("highlight", null);
        StyleConstants.setForeground(highlightStyle, HIGHLIGHT_COLOR);
        StyleConstants.setBold(highlightStyle, true);

        Style alertStyle = dronesArea.addStyle("alert", null);
        StyleConstants.setForeground(alertStyle, ALERT_COLOR);

        try {
            doc.remove(0, doc.getLength());

            allDrones.forEach((id, status) -> {
                String zone = status.currentIncident != null ? String.valueOf(status.currentIncident.getZone()) : "None";
                try {
                    doc.insertString(doc.getLength(), "Drone ", defaultStyle);
                    doc.insertString(doc.getLength(), String.valueOf(id), highlightStyle);
                    doc.insertString(doc.getLength(), ": (", defaultStyle);
                    doc.insertString(doc.getLength(), String.valueOf(status.droneInfo.x), valueStyle);
                    doc.insertString(doc.getLength(), ", ", defaultStyle);
                    doc.insertString(doc.getLength(), String.valueOf(status.droneInfo.y), valueStyle);
                    doc.insertString(doc.getLength(), ") State: ", defaultStyle);

                    Style stateStyle = status.state.equals("IDLE") ? defaultStyle :
                            status.state.equals("RETURNING") ? valueStyle : highlightStyle;
                    doc.insertString(doc.getLength(), status.state, stateStyle);

                    doc.insertString(doc.getLength(), " Zone: ", defaultStyle);
                    Style zoneStyle = zone.equals("None") ? defaultStyle : alertStyle;
                    doc.insertString(doc.getLength(), zone, zoneStyle);
//                    doc.insertString(doc.getLength(), "\n", defaultStyle);
                    if (status.currentIncident != null) {
                        double distance = scheduler.getDistanceToIncident(id);
                        doc.insertString(doc.getLength(), " Distance: ", defaultStyle);
                        doc.insertString(doc.getLength(), String.format("%.2f meters", distance), valueStyle);
                    }
                    doc.insertString(doc.getLength(), "\n", defaultStyle);
//                    doc.insertString(doc.getLength(), "\n", defaultStyle);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updatePendingArea() {
        StyledDocument doc = pendingArea.getStyledDocument();

        Style defaultStyle = pendingArea.addStyle("default", null);
        StyleConstants.setForeground(defaultStyle, TEXT_COLOR);

        Style zoneStyle = pendingArea.addStyle("zone", null);
        StyleConstants.setForeground(zoneStyle, HIGHLIGHT_COLOR);
        StyleConstants.setBold(zoneStyle, true);

        Style typeStyle = pendingArea.addStyle("type", null);
        StyleConstants.setForeground(typeStyle, VALUE_COLOR);

        Style sevStyle = pendingArea.addStyle("severity", null);
        StyleConstants.setForeground(sevStyle, ALERT_COLOR);

        try {
            doc.remove(0, doc.getLength());

            for (Incident inc : pendingIncidents) {
                doc.insertString(doc.getLength(), "Zone ", defaultStyle);
                doc.insertString(doc.getLength(), String.valueOf(inc.getZone()), zoneStyle);
                doc.insertString(doc.getLength(), " | Type: ", defaultStyle);
                doc.insertString(doc.getLength(), inc.getEventType(), typeStyle);
                doc.insertString(doc.getLength(), " | Sev: ", defaultStyle);
                doc.insertString(doc.getLength(), inc.getSeverity(), sevStyle);
                doc.insertString(doc.getLength(), "\n", defaultStyle);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateCompletedArea() {
        StyledDocument doc = completedArea.getStyledDocument();

        Style defaultStyle = completedArea.addStyle("default", null);
        StyleConstants.setForeground(defaultStyle, TEXT_COLOR);

        Style zoneStyle = completedArea.addStyle("zone", null);
        StyleConstants.setForeground(zoneStyle, HIGHLIGHT_COLOR);
        StyleConstants.setBold(zoneStyle, true);

        Style typeStyle = completedArea.addStyle("type", null);
        StyleConstants.setForeground(typeStyle, VALUE_COLOR);

        Style timeStyle = completedArea.addStyle("time", null);
        StyleConstants.setForeground(timeStyle, new Color(186, 140, 241));

        try {
            doc.remove(0, doc.getLength());

            for (Incident inc : completedIncidents) {
                doc.insertString(doc.getLength(), "Zone ", defaultStyle);
                doc.insertString(doc.getLength(), String.valueOf(inc.getZone()), zoneStyle);
                doc.insertString(doc.getLength(), " | Type: ", defaultStyle);
                doc.insertString(doc.getLength(), inc.getEventType(), typeStyle);
                doc.insertString(doc.getLength(), " | Response Time: ", defaultStyle);

                // Show completion time instead of timestamp
                String completionTime = inc.getCompletionTimeFormatted();
                doc.insertString(doc.getLength(), completionTime, timeStyle);
                doc.insertString(doc.getLength(), "\n", defaultStyle);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}