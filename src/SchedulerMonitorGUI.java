import javax.swing.*;
import javax.swing.Timer;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
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
    private final MapPanel mapPanel;
    private final String logFilePath = "log.txt";
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final Set<String> loggedIncidents = new HashSet<>();

    public SchedulerMonitorGUI(Map<Integer, Scheduler.DroneStatus> drones,
                               Queue<Incident> pending,
                               List<Incident> completed,
                               Scheduler scheduler) {
        this.allDrones = drones;
        this.pendingIncidents = pending;
        this.completedIncidents = completed;
        this.scheduler = scheduler;
        this.mapPanel = new MapPanel(
                scheduler,
                BG_COLOR,
                HEADER_COLOR,   // zone outline
                VALUE_COLOR,    // drone dot
                ALERT_COLOR     // incident marker
        );
        initLogFile();
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
        elapsedTimeLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
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
        add(mapPanel, BorderLayout.EAST);
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
        String timeInfo = scheduler.getElapsedTimeFormatted();
        elapsedTimeLabel.setText(timeInfo);
        updateDronesArea();
        updatePendingArea();
        updateCompletedArea();
        updateFaultArea();
        mapPanel.repaint();
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

                        String logContent = "Drone " + id + ": " + current + " | Fix: " + fix;
                        logToFile("FAULT REPORT", logContent);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
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
                    if (status.currentIncident != null) {
                        double distance = scheduler.getDistanceToIncident(id);
                        doc.insertString(doc.getLength(), " Distance: ", defaultStyle);
                        doc.insertString(doc.getLength(), String.format("%.2f meters", distance), valueStyle);
                    }
                    doc.insertString(doc.getLength(), "\n", defaultStyle);
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
                String incidentID = inc.getIncidentID();

                doc.insertString(doc.getLength(), "Zone ", defaultStyle);
                doc.insertString(doc.getLength(), String.valueOf(inc.getZone()), zoneStyle);
                doc.insertString(doc.getLength(), " | Type: ", defaultStyle);
                doc.insertString(doc.getLength(), inc.getEventType(), typeStyle);
                doc.insertString(doc.getLength(), " | Sev: ", defaultStyle);
                doc.insertString(doc.getLength(), inc.getSeverity(), sevStyle);
                doc.insertString(doc.getLength(), "\n", defaultStyle);
                
                if (!loggedIncidents.contains(incidentID)) {
                    String logContent = "Zone " + inc.getZone() + " | Type: " + inc.getEventType() +
                            " | Sev: " + inc.getSeverity();
                    logToFile("PENDING INCIDENT", logContent);
                    loggedIncidents.add(incidentID);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initLogFile() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFilePath))) {
            writer.println("========== SCHEDULER MONITOR LOG ==========");
            writer.println("Started: " + dateFormat.format(new Date()));
            writer.println("=========================================");
            writer.println();
        } catch (IOException e) {
            System.err.println("Error initializing log file: " + e.getMessage());
        }
    }

    private void logToFile(String category, String content) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFilePath, true))) {
            writer.println(dateFormat.format(new Date()) + " - " + category + ": " + content);
        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
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
                String incidentID = inc.getIncidentID();

                doc.insertString(doc.getLength(), "Zone ", defaultStyle);
                doc.insertString(doc.getLength(), String.valueOf(inc.getZone()), zoneStyle);
                doc.insertString(doc.getLength(), " | Type: ", defaultStyle);
                doc.insertString(doc.getLength(), inc.getEventType(), typeStyle);
                doc.insertString(doc.getLength(), " | Response Time: ", defaultStyle);

                // Show completion time instead of timestamp
                String completionTime = inc.getCompletionTimeFormatted();
                doc.insertString(doc.getLength(), completionTime, timeStyle);
                doc.insertString(doc.getLength(), "\n", defaultStyle);

                if (!loggedIncidents.contains("completed:" + incidentID)) {
                    String logContent = "Zone " + inc.getZone() + " | Type: " + inc.getEventType() +
                            " | Response Time: " + completionTime;
                    logToFile("COMPLETED INCIDENT", logContent);
                    loggedIncidents.add("completed:" + incidentID);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class MapPanel extends JPanel {
    private final Scheduler scheduler;
    private final Color bgColor, zoneColor, droneColor, incidentColor;

    public MapPanel(Scheduler scheduler, Color bgColor, Color zoneColor, Color droneColor, Color incidentColor) {
        this.scheduler = scheduler;
        this.bgColor = bgColor;
        this.zoneColor = zoneColor;
        this.droneColor = droneColor;
        this.incidentColor = incidentColor;

        setPreferredSize(new Dimension(500, 500));
        setBackground(bgColor);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        List<Zone> zones = scheduler.getZones();
        Map<Integer, Scheduler.DroneStatus> drones = scheduler.getAllDrones();

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (Zone z : zones) {
            int sx = z.getStartX() / 10;
            int sy = z.getStartY() / 10;
            int ex = z.getEndX() / 10;
            int ey = z.getEndY() / 10;
            minX = Math.min(minX, sx);
            minY = Math.min(minY, sy);
            maxX = Math.max(maxX, ex);
            maxY = Math.max(maxY, ey);
        }

        int mapWidth = maxX - minX;
        int mapHeight = maxY - minY;
        int panelWidth = getWidth();
        int panelHeight = getHeight();
        int offsetX = (panelWidth - mapWidth) / 2 - minX;
        int offsetY = (panelHeight - mapHeight) / 2 - minY;

        Graphics2D g2 = (Graphics2D) g;
        g2.setColor(zoneColor);

        Set<String> activeIncidentIDs = new HashSet<>();
        Queue<Incident> pending = scheduler.getPendingIncidents();
        for (Incident i : pending) activeIncidentIDs.add(i.getIncidentID());
        for (Scheduler.DroneStatus status : drones.values()) {
            if (status.currentIncident != null) {
                activeIncidentIDs.add(status.currentIncident.getIncidentID());
            }
        }

        for (Zone zone : zones) {
            int x = zone.getStartX() / 10 + offsetX;
            int y = zone.getStartY() / 10 + offsetY;
            int width = (zone.getEndX() - zone.getStartX()) / 10;
            int height = (zone.getEndY() - zone.getStartY()) / 10;
            g2.drawRect(x, y, width, height);

            boolean hasIncident = false;
            for (Incident inc : pending) {
                if (inc.getZone() == zone.getId()) {
                    hasIncident = true;
                    break;
                }
            }
            for (Scheduler.DroneStatus status : drones.values()) {
                if (status.currentIncident != null && status.currentIncident.getZone() == zone.getId()) {
                    hasIncident = true;
                    break;
                }
            }

            if (hasIncident) {
                g2.setColor(incidentColor);
                g2.fillOval(x + width / 2 - 4, y + height / 2 - 4, 8, 8);
                g2.setColor(zoneColor);
            }
        }

        g2.setColor(droneColor);
        for (Scheduler.DroneStatus drone : drones.values()) {
            int dx = drone.droneInfo.x / 10 + offsetX;
            int dy = drone.droneInfo.y / 10 + offsetY;
            g2.fillOval(dx - 3, dy - 3, 6, 6);
        }
    }
}