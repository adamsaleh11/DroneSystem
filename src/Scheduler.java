import javax.swing.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.net.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;

public class Scheduler {
    private static final int FIRE_PORT = 5000;
    private static final int DRONE_PORT = 6000;
    private static final int SCHEDULER_PORT = 4000;

    private final Queue<Incident> pendingIncidents = new LinkedList<>();
    private final List<Incident> completedIncidents = new ArrayList<>();
    private final List<Zone> zones = new ArrayList<>();
    private final Map<Integer, DroneStatus> allDrones = new ConcurrentHashMap<>();
    // Set to track completed incident IDs and prevent duplicate completions
    Set<String> completedIncidentIDs = new HashSet<>();

    private boolean shouldRun = true;
    private Thread receiveIncidents;
    private Thread receiveDrones;
    private Thread processIncidents;
    Set<String> pendingIncidentIDs = new HashSet<>();

    public static void main(String[] args) {
        System.out.println("=== SCHEDULER SUBSYSTEM STARTING ===");
        Scanner scanner = new Scanner(System.in);

        try {
            Scheduler scheduler = new Scheduler();

            System.out.print("Enter zones file path (press Enter for default 'src/resources/Sample_zone_file.csv'): ");
            String zonesPath = scanner.nextLine().trim();
            if (zonesPath.isEmpty()) zonesPath = "src/resources/Sample_zone_file.csv";

            scheduler.loadZones(zonesPath);
            scheduler.start();

            SwingUtilities.invokeLater(() -> new SchedulerMonitorGUI(
                    scheduler.getAllDrones(),
                    scheduler.getPendingIncidents(),
                    scheduler.getCompletedIncidents()
            ));

            System.out.println("Scheduler is now running. Press Enter to stop.");
            scanner.nextLine();
            scheduler.stop();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            scanner.close();
        }
    }

    public void start() {
        receiveIncidents = new Thread(this::listenForIncidents);
        receiveDrones = new Thread(this::listenForDroneUpdates);
        processIncidents = new Thread(this::processPendingIncidents);

        receiveIncidents.start();
        receiveDrones.start();
        processIncidents.start();
    }

    public void stop() {
        shouldRun = false;
        try {
            DatagramSocket tempSocket = new DatagramSocket();
            byte[] stop = "STOP".getBytes();
            tempSocket.send(new DatagramPacket(stop, stop.length, InetAddress.getLocalHost(), SCHEDULER_PORT));
            tempSocket.send(new DatagramPacket(stop, stop.length, InetAddress.getLocalHost(), DRONE_PORT));
            tempSocket.close();

            if (receiveIncidents != null) receiveIncidents.join();
            if (receiveDrones != null) receiveDrones.join();
            if (processIncidents != null) processIncidents.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void listenForIncidents() {
        try (DatagramSocket socket = new DatagramSocket(SCHEDULER_PORT)) {
            socket.setSoTimeout(1000);
            while (shouldRun) {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                try {
                    socket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength());
                    if (message.equals("STOP")) continue;

                    String[] parts = message.split(",");
                    if (parts.length >= 8 && parts[0].equals("Incident")) {
                        Incident incident = new Incident(parts[7], Integer.parseInt(parts[1]), parts[4], parts[5]);
                        incident.setWaterAmountNeeded(Integer.parseInt(parts[6]));
                        pendingIncidents.add(incident);
                    }
                } catch (SocketTimeoutException ignored) {} catch (Exception e) {
                    if (shouldRun) e.printStackTrace();
                }
            }
        } catch (Exception e) {
            if (shouldRun) e.printStackTrace();
        }
    }

    private void listenForDroneUpdates() {
        try (DatagramSocket socket = new DatagramSocket(DRONE_PORT)) {
            socket.setSoTimeout(1000);
            while (shouldRun) {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength());
                    System.out.println(message);
                    if (message.startsWith("Reassign")) {
                        String[] parts = message.split(",");
                        if (parts.length >= 7) {
                            int droneId = Integer.parseInt(parts[1]);
                            int zone = Integer.parseInt(parts[2]);
                            String type = parts[3];
                            String severity = parts[4];
                            int waterAmount = Integer.parseInt(parts[5]);
                            String time = parts[6];

                            Incident reassigned = new Incident(time, zone, type, severity);
                            reassigned.setWaterAmountNeeded(waterAmount);

                            String incidentID = reassigned.getIncidentID();
                            if (!completedIncidentIDs.contains(incidentID) && !pendingIncidentIDs.contains(incidentID)) {
                                pendingIncidents.add(reassigned);
                                pendingIncidentIDs.add(incidentID);

                                System.out.println("\n================== REASSIGNMENT ==================");
                                System.out.printf("Reassigned incident from Drone %d\n", droneId);
                                System.out.printf("Incident Zone:    %d\n", reassigned.getZone());
                                System.out.printf("Event Type:       %s\n", reassigned.getEventType());
                                System.out.printf("Severity:         %s\n", reassigned.getSeverity());
                                System.out.println("================================================\n");
                            }

                            DroneStatus status = allDrones.get(droneId);
                            if (status != null) {
                                status.currentIncident = null;
//                                status.state = "IDLE";
                                status.isAvailable = true;
                            }
                        }
                        continue;
                    }

                    if (message.contains("Complete")) {
                        String[] parts = message.split(",");
                        if (parts.length >= 6) {
                            int droneId = Integer.parseInt(parts[1]);
                            DroneStatus status = allDrones.get(droneId);
                            if (status != null && status.currentIncident != null) {
                                String incidentID = status.currentIncident.getIncidentID();
                                if (!completedIncidentIDs.contains(incidentID)) {
                                    status.currentIncident.markAsCompleted();
                                    completedIncidents.add(status.currentIncident);
                                    completedIncidentIDs.add(incidentID);
                                    pendingIncidentIDs.remove(incidentID);
                                    System.out.println("Drone " + droneId + " completed incident in Zone " + status.currentIncident.getZone());
                                }
                                status.currentIncident = null;
                            }
                            if (status != null) {
//                                status.state = "IDLE";
                                status.isAvailable = true;
                            }
                        }
                        continue;
                    }

                    if (message.equals("STOP")) continue;
                    if (message.contains("Fault")) {
                        processDroneFault(message);
                        continue;
                    }

                    String[] parts = message.split(",");
                    if (parts.length >= 5 && parts[0].equals("Drone")) {
                        int id = Integer.parseInt(parts[1]);
                        int x = Integer.parseInt(parts[2]);
                        int y = Integer.parseInt(parts[3]);
                        String state = parts[4];

                        updateDroneStatus(id, x, y, packet.getAddress(), state, state.equals("IDLE"));
                    }
                } catch (SocketTimeoutException ignored) {} catch (Exception e) {
                    if (shouldRun) e.printStackTrace();
                }
            }
        } catch (Exception e) {
            if (shouldRun) e.printStackTrace();
        }
    }

    private void updateDroneStatus(int id, int x, int y, InetAddress address, String state, boolean available) {
        allDrones.compute(id, (droneId, status) -> {
            if (status == null) {
                DroneInfo info = new DroneInfo(id, x, y, address);
                status = new DroneStatus(info);
            }
            status.droneInfo.x = x;
            status.droneInfo.y = y;
            status.droneInfo.address = address;
            status.droneInfo.lastUpdateTime = System.currentTimeMillis();
            status.state = state;
            status.isAvailable = available;
            return status;
        });
    }

    private void processPendingIncidents() {
        try {
            while (shouldRun) {
                if (!pendingIncidents.isEmpty()) {
                    Incident incident = pendingIncidents.poll();
                    boolean assigned = assignDrone(incident);
                    if (!assigned) {
                        // Re-queue the incident
                        pendingIncidents.offer(incident);
                    }
                }
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            if (shouldRun) e.printStackTrace();
        }
    }

    public boolean assignDrone(Incident incident) {
        Zone zone = getZoneById(incident.getZone());
        if (zone == null) return false;

        int x = zone.getCenterX();
        int y = zone.getCenterY();
        DroneStatus best = null;
        double minDist = Double.MAX_VALUE;
        boolean isReroute = false;

        for (DroneStatus status : allDrones.values()) {
            if (status.isAvailable && status.state.equals("IDLE")) {
                DroneInfo drone = status.droneInfo;
                double dist = Math.hypot(drone.x - x, drone.y - y);
                if (dist < minDist) {
                    minDist = dist;
                    best = status;
                    isReroute = false;
                }
            }
        }

        double rerouteThreshold = 200.0;
        double currentMinDist = minDist;

        if (minDist > rerouteThreshold || best == null) {
            for (DroneStatus status : allDrones.values()) {
                if (!status.isAvailable && status.state.equals("EN_ROUTE") &&
                        status.currentIncident != null &&
                        !status.hasBeenRerouted) {

                    DroneInfo drone = status.droneInfo;
                    double dist = Math.hypot(drone.x - x, drone.y - y);

                    Zone originalZone = getZoneById(status.currentIncident.getZone());
                    double distToOriginal = Double.MAX_VALUE;
                    if (originalZone != null) {
                        distToOriginal = Math.hypot(drone.x - originalZone.getCenterX(),
                                drone.y - originalZone.getCenterY());
                    }
                    int newIncidentPriority = getSeverityPriority(incident.getSeverity());
                    int currentIncidentPriority = getSeverityPriority(status.currentIncident.getSeverity());

                    if (dist < distToOriginal * 0.5 &&
                            newIncidentPriority >= currentIncidentPriority &&
                            dist < currentMinDist) {
                        best = status;
                        isReroute = true;
                    }
                }
            }
        }

        if (best != null) {
            if (isReroute && best.currentIncident != null) {
                pendingIncidents.add(best.currentIncident);
                pendingIncidentIDs.add(best.currentIncident.getIncidentID());  // <-- also re-track it
                best.hasBeenRerouted = true;
                scheduleRerouteReset(best.droneInfo.id);
            }

            best.isAvailable = false;
            best.currentIncident = incident;
            best.state = "EN_ROUTE";
            sendDroneAssignment(best.droneInfo, incident, x, y);
            System.out.println("\n================== ASSIGNMENT ==================");
            System.out.printf("Drone ID:         %d\n", best.droneInfo.id);
            System.out.printf("Current Position: (%d, %d)\n", best.droneInfo.x, best.droneInfo.y);
            System.out.printf("Incident Zone:    %d\n", incident.getZone());
            System.out.printf("Event Type:       %s\n", incident.getEventType());
            System.out.printf("Severity:         %s\n", incident.getSeverity());
            System.out.printf("Water Needed:     %d L\n", incident.getWaterAmountNeeded());
            System.out.printf("Time:             %s\n", incident.getTime());
            System.out.println("===============================================\n");
            return true;
        }
        return false;
    }

    // Helper method to determine severity priority
    private int getSeverityPriority(String severity) {
        switch(severity.toUpperCase()) {
            case "HIGH": return 3;
            case "MEDIUM": return 2;
            case "LOW": return 1;
            default: return 0;
        }
    }

    // Schedule resetting of the rerouted flag
    private void scheduleRerouteReset(int droneId) {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                DroneStatus status = allDrones.get(droneId);
                if (status != null) {
                    status.hasBeenRerouted = false;
                }
            }
        }, 60000); // Reset after 1 minute
    }

    private void sendDroneAssignment(DroneInfo drone, Incident inc, int x, int y) {
        try (DatagramSocket socket = new DatagramSocket()) {
            String msg = String.format("Assign,%d,%d,%d,%s,%s,%d,%s",
                    inc.getZone(), x, y, inc.getEventType(), inc.getSeverity(), inc.getWaterAmountNeeded(), inc.getTime());

            byte[] buffer = msg.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, drone.address, DRONE_PORT + drone.id);
            socket.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Zone getZoneById(int id) {
        return zones.stream().filter(z -> z.getId() == id).findFirst().orElse(null);
    }

    public Map<Integer, DroneStatus> getAllDrones() {
        return allDrones;
    }

    public Queue<Incident> getPendingIncidents() {
        return pendingIncidents;
    }

    public List<Incident> getCompletedIncidents() {
        return completedIncidents;
    }

    public void loadZones(String file) {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                int id = Integer.parseInt(parts[0].trim());
                String[] start = parts[1].replace("(", "").replace(")", "").split(";");
                String[] end = parts[2].replace("(", "").replace(")", "").split(";");
                zones.add(new Zone(id, Integer.parseInt(start[0]), Integer.parseInt(start[1]),
                        Integer.parseInt(end[0]), Integer.parseInt(end[1])));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void processDroneFault(String message) {
        System.out.println("\n####################################");
        System.out.println("#####        DRONE FAULT       #####");
        System.out.println("####################################");

        String[] parts = message.split("Fault:");
        if (parts.length > 1) {
            String faultDetails = parts[1].trim();
            String[] faultParts = faultDetails.split("ERROR:");
            String faultDescription = faultParts.length > 1 ? faultParts[1].trim() : faultParts[0].trim();

            System.out.println("# Fault Description: " + faultDescription);

            String[] droneParts = parts[0].trim().split(" ");
            int droneId = -1;
            if (droneParts.length >= 2) {
                try {
                    droneId = Integer.parseInt(droneParts[1]);
                } catch (NumberFormatException e) {
                    System.out.println("# Error: Invalid Drone ID format");
                }
            }

            String faultType = "";
            if (faultDescription.toLowerCase().contains("connection lost via packet loss")) {
                faultType = "packet";
            } else if (faultDescription.toLowerCase().contains("stuck")) {
                faultType = "stuck";
            } else if (faultDescription.toLowerCase().contains("nozzle")) {
                faultType = "nozzle";
            }

            if (droneId != -1 && !faultType.isEmpty()) {
                System.out.println("# Drone ID: " + droneId);
                System.out.println("# Fault Type: " + faultType.toUpperCase());

                DroneStatus status = allDrones.get(droneId);
                if (status != null) {
                    status.faultMessage = faultDescription;
                }

                DroneInfo drone = status != null ? status.droneInfo : null;
                if (drone != null) {
                    System.out.println("# Action: Reassigning incident and resetting drone");
                    reassignIncident(droneId);
                    switch (faultType) {
                        case "stuck":
                            System.out.println("# Resetting stuck drone");
                            break;
                        case "nozzle":
                            System.out.println("# Forcing nozzle reset");
                            forceNozzle(droneId);
                            break;
                        case "packet":
                            System.out.println("# Re-establishing packet connection");
                            establishConnection(droneId);
                            break;
                    }
                    resetDroneToWorking(droneId);
                }
            } else {
                if (droneId == -1) {
                    System.out.println("# Error: Could not identify drone");
                }
                if (faultType.isEmpty()) {
                    System.out.println("# Error: Unknown fault type");
                }
            }
        }
        System.out.println("####################################\n");
    }

    private void reassignIncident(int droneId) {
        DroneStatus status = allDrones.get(droneId);
        if (status != null) {
            if (status.currentIncident != null) {
                System.out.println("Reassigning incident from faulted drone " + droneId);
                pendingIncidents.add(status.currentIncident);
                status.currentIncident = null;
            } else {
                System.out.println("No current incident found for faulted drone " + droneId + ", nothing to reassign.");
            }
        }
    }

    private void forceNozzle(int droneId) {
        System.out.println("Nozzle Malfunction, drone returning to base");
    }

    private void establishConnection(int droneId) {
        System.out.println("Re-establishing Connection, drone returning to base");
    }

    private void resetDroneToWorking(int droneId) {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        DroneStatus status = allDrones.get(droneId);
        if (status != null) {
            status.faultMessage = null;
            status.isAvailable = true;
            sendCountdownResetCommand(status.droneInfo);
        }
    }

    private void sendCountdownResetCommand(DroneInfo drone) {
        try (DatagramSocket socket = new DatagramSocket()) {
            String message = "ResetCountdown";
            byte[] buffer = message.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, drone.address, DRONE_PORT + drone.id);
            socket.send(packet);
            System.out.println("Sent countdown reset command to Drone " + drone.id);
        } catch (Exception e) {
            System.err.println("Error sending countdown reset to drone: " + e.getMessage());
        }
    }

    static class DroneInfo {
        int id, x, y;
        InetAddress address;
        long lastUpdateTime;

        DroneInfo(int id, int x, int y, InetAddress address) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.address = address;
            this.lastUpdateTime = System.currentTimeMillis();
        }
    }

    static class DroneStatus {
        DroneInfo droneInfo;
        String state = "UNKNOWN";  // Now includes "EN_ROUTE" state
        boolean isAvailable = false;
        String faultMessage = null;
        Incident currentIncident = null;
        boolean hasBeenRerouted = false;  // Track if drone was recently rerouted

        DroneStatus(DroneInfo info) {
            this.droneInfo = info;
        }
    }
}
