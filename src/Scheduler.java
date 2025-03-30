import java.io.BufferedReader;
import java.io.FileReader;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Scheduler {
    private static final int FIRE_PORT = 5000;
    private static final int DRONE_PORT = 6000;
    private static final int SCHEDULER_PORT = 4000;

    private final List<DroneInfo> idleDrones = new ArrayList<>();
    private final Queue<Incident> pendingIncidents = new LinkedList<>();
    private final List<Zone> zones = new ArrayList<>();

    // New map to track all drones and their statuses
    private final Map<Integer, DroneStatus> allDrones = new ConcurrentHashMap<>();

    private boolean shouldRun = true;
    private Thread receiveIncidents;
    private Thread receiveDrones;
    private Thread processIncidents;

    public static void main(String[] args) {
        System.out.println("=== SCHEDULER SUBSYSTEM STARTING ===");
        System.out.println("This subsystem will coordinate drones to handle fire incidents.");
        System.out.println();

        Scanner scanner = new Scanner(System.in);

        try {
            Scheduler scheduler = new Scheduler();

            System.out.print("Enter zones file path (press Enter for default 'src/resources/Sample_zone_file.csv'): ");
            String zonesPath = scanner.nextLine().trim();
            if (zonesPath.isEmpty()) {
                zonesPath = "src/resources/Sample_zone_file.csv";
            }

            System.out.println("Loading zones from: " + zonesPath);
            scheduler.loadZones(zonesPath);

            System.out.println("Starting scheduler services...");
            scheduler.start();

            // Add a thread to periodically display drone statuses
            Thread statusThread = new Thread(() -> {
                while (scheduler.shouldRun) {
                    try {
                        Thread.sleep(10000); // Print statuses every 10 seconds
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
            statusThread.setDaemon(true);
            statusThread.start();

            System.out.println("Scheduler is now running. Press Enter to stop.");
            scanner.nextLine();

            System.out.println("Stopping scheduler...");
            scheduler.stop();
            System.out.println("Scheduler has been stopped.");

        } catch (Exception e) {
            System.err.println("Error initializing Scheduler: " + e.getMessage());
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
            // Use a temporary socket to unblock the listening sockets
            DatagramSocket tempSocket = new DatagramSocket();
            byte[] data = "STOP".getBytes();

            // Send a packet to unblock the incident listener
            tempSocket.send(new DatagramPacket(data, data.length,
                    InetAddress.getLocalHost(), SCHEDULER_PORT));

            // Send a packet to unblock the drone listener
            tempSocket.send(new DatagramPacket(data, data.length,
                    InetAddress.getLocalHost(), DRONE_PORT));

            tempSocket.close();

            // Wait for all threads to terminate
            if (receiveIncidents != null) receiveIncidents.join();
            if (receiveDrones != null) receiveDrones.join();
            if (processIncidents != null) processIncidents.join();
        } catch (Exception e) {
            System.err.println("Error stopping scheduler: " + e.getMessage());
        }
    }

    private void listenForIncidents() {
        try (DatagramSocket socket = new DatagramSocket(SCHEDULER_PORT)) {
            socket.setSoTimeout(1000); // Set timeout to check shouldRun flag periodically

            System.out.println("Listening for incidents on port " + SCHEDULER_PORT);

            while (shouldRun) {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                try {
                    socket.receive(packet);

                    String message = new String(packet.getData(), 0, packet.getLength());

                    if (message.equals("STOP")) continue;

                    String[] parts = message.split(",");
                    if (parts.length >= 4 && parts[0].equals("Incident")) {
                        int zoneId = Integer.parseInt(parts[1]);
                        int x = Integer.parseInt(parts[2]);
                        int y = Integer.parseInt(parts[3]);
                        String eventType = parts.length > 4 ? parts[4] : "UNKNOWN";
                        String severity = parts.length > 5 ? parts[5] : "Unknown";
                        int water = parts.length > 6 ? Integer.parseInt(parts[6]) : 0;
                        String time = parts.length > 7 ? parts[7] : "Unknown";

                        System.out.println("Received incident for Zone " + zoneId + ": " + eventType + " - " + severity);

                        Incident incident = new Incident(time, zoneId, eventType, severity);
                        incident.setWaterAmountNeeded(water);
                        pendingIncidents.add(incident);
                    }
                } catch (SocketTimeoutException e) {
                    // Timeout occurred, loop will continue if shouldRun is true
                } catch (Exception e) {
                    if (shouldRun) {
                        System.err.println("Error receiving incident: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            if (shouldRun) {
                System.err.println("Error in incident listener: " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("Incident listener stopped");
    }

    private void listenForDroneUpdates() {
        try (DatagramSocket socket = new DatagramSocket(DRONE_PORT)) {
            socket.setSoTimeout(1000); // Set timeout to check shouldRun flag periodically

            System.out.println("Listening for drone updates on port " + DRONE_PORT);

            while (shouldRun) {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                try {
                    socket.receive(packet);

                    String message = new String(packet.getData(), 0, packet.getLength());
                    System.out.println("Scheduler received from drone: " + message);

                    if (message.equals("STOP")) continue;

                    if (message.contains("Fault")) {
                        processDroneFault(message);
                        continue;
                    }

                    // Handle drone registration messages - This was missing!
                    String[] parts = message.split(",");
                    if (parts.length >= 4 && parts[0].equals("Drone")) {
                        int droneId = Integer.parseInt(parts[1]);
                        int x = Integer.parseInt(parts[2]);
                        int y = Integer.parseInt(parts[3]);

                        InetAddress droneAddress = packet.getAddress();

                        // Update drone in the tracking system
                        updateDroneStatus(droneId, x, y, droneAddress, "IDLE", true);

                        boolean droneFound = false;
                        for (DroneInfo drone : idleDrones) {
                            if (drone.id == droneId) {
                                // Update existing drone info
                                drone.x = x;
                                drone.y = y;
                                drone.lastUpdateTime = System.currentTimeMillis();
                                droneFound = true;
                                break;
                            }
                        }

                        if (!droneFound) {
                            DroneInfo newDrone = new DroneInfo(droneId, x, y, droneAddress);
                            idleDrones.add(newDrone);
                            System.out.println("Registered new drone: Drone " + droneId + " at position (" + x + ", " + y + ")");
                        }
                    }

                } catch (SocketTimeoutException e) {
                    // Timeout occurred, loop will continue if shouldRun is true
                } catch (Exception e) {
                    if (shouldRun) {
                        System.err.println("Error receiving drone update: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            if (shouldRun) {
                System.err.println("Error in drone listener: " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("Drone listener stopped");
    }

    // New method to update drone status
    private void updateDroneStatus(int droneId, int x, int y, InetAddress address, String state, boolean isAvailable) {
        DroneStatus status = allDrones.get(droneId);

        if (status == null) {
            DroneInfo droneInfo = new DroneInfo(droneId, x, y, address);
            status = new DroneStatus(droneInfo);status.state = state;
            status.isAvailable = isAvailable;
            allDrones.put(droneId, status);
        } else {
            status.droneInfo.x = x;
            status.droneInfo.y = y;
            status.droneInfo.address = address;
            status.droneInfo.lastUpdateTime = System.currentTimeMillis();
            status.state = state;
            status.isAvailable = isAvailable;
        }
    }

    private void processDroneFault(String message) {
        System.out.println("Processing fault message: " + message);

        // Split by "Fault:" to separate drone ID and the fault part
        String[] parts = message.split("Fault:");
        if (parts.length > 1) {
            String faultDetails = parts[1].trim(); // The fault message after "Fault:"
            System.out.println("Fault details: " + faultDetails);

            // Split the fault message to isolate the error description
            String[] faultParts = faultDetails.split("ERROR:");
            String faultDescription = faultParts.length > 1 ? faultParts[1].trim() : faultParts[0].trim();
            System.out.println("Fault description: " + faultDescription);

            // Extract drone ID from the first part of the message
            String[] droneParts = parts[0].trim().split(" ");
            int droneId = -1;
            if (droneParts.length >= 2) {
                try {
                    droneId = Integer.parseInt(droneParts[1]);
                } catch (NumberFormatException e) {
                    System.out.println("Error parsing drone ID: " + e.getMessage());
                }
            }

            // Determine fault type from the description
            String faultType = "";
            if (faultDescription.toLowerCase().contains("connection lost via packet loss")) {
                faultType = "packet";
            } else if (faultDescription.toLowerCase().contains("stuck")) {
                faultType = "stuck";
            } else if (faultDescription.toLowerCase().contains("nozzle")) {
                faultType = "nozzle";
            }

            // Check if we found a valid drone ID
            if (droneId != -1 && !faultType.isEmpty()) {
                System.out.println("Found Drone ID: " + droneId + " with fault type: " + faultType);

                // Update drone status with fault information
                DroneStatus status = allDrones.get(droneId);
                if (status != null) {
                    status.faultMessage = faultDescription;
                }

                // Look for the drone in the idleDrones list
                DroneInfo faultyDrone = null;
                for (DroneInfo drone : idleDrones) {
                    if (drone.id == droneId) {
                        faultyDrone = drone;
                        break;
                    }
                }

                if (faultyDrone != null) {
                    // Handle the fault based on the fault type
                    switch (faultType) {
                        case "stuck":
                            System.out.println("Drone " + droneId + " is stuck in flight, reassigning incident.");
                            reassignIncident(faultyDrone);
                            break;
                        case "nozzle":
                            System.out.println("Drone " + droneId + " has a nozzle error, forcing nozzle to work.");
                            forceNozzle(faultyDrone);
                            break;
                        case "packet":
                            System.out.println("Drone " + droneId + " has packet loss, re-establishing connection.");
                            establishConnection(faultyDrone);
                            break;
                        default:
                            System.out.println("Unknown fault for Drone " + droneId + ": " + faultType);
                            break;
                    }

                    // After handling the fault, reset the drone to working state
                    resetDroneToWorking(faultyDrone);
                } else {
                    System.out.println("Fault detected for drone " + droneId);
                }
            } else {
                if (droneId == -1) {
                    System.out.println("Failed to extract valid drone ID from the fault message.");
                }
                if (faultType.isEmpty()) {
                    System.out.println("Could not determine fault type from description: " + faultDescription);
                }
            }
        }
    }

    private void reassignIncident(DroneInfo drone) {
        System.out.println("Reassigning incident for Drone " + drone.id);

        // Find the drone status
        DroneStatus status = allDrones.get(drone.id);
        if (status != null && status.currentIncident != null) {
            // Put the incident back in the queue
            pendingIncidents.add(status.currentIncident);
            status.currentIncident = null;
        }
    }

    private void forceNozzle(DroneInfo drone) {
        System.out.println("Forcing nozzle to work for Drone " + drone.id);
    }

    private void establishConnection(DroneInfo drone) {
        System.out.println("Re-establishing connection for Drone " + drone.id);
    }

    private void resetDroneToWorking(DroneInfo drone) {
        System.out.println("Resetting Drone " + drone.id + " to working state.");
        DroneStatus status = allDrones.get(drone.id);
        if (status != null) {
            status.faultMessage = null;
            status.state = "IDLE";
            status.isAvailable = true;

            // Send reactivation command to drone
            sendCountdownResetCommand(drone);

            // Add drone back to idle drones if not already there
            boolean droneFound = false;
            for (DroneInfo d : idleDrones) {
                if (d.id == drone.id) {
                    droneFound = true;
                    break;
                }
            }

            if (!droneFound) {
                idleDrones.add(drone);
            }
        }
    }

    // Add this new method to Scheduler.java
    private void sendCountdownResetCommand(DroneInfo drone) {
        try (DatagramSocket socket = new DatagramSocket()) {
            String message = "ResetCountdown";
            byte[] buffer = message.getBytes();

            DatagramPacket packet = new DatagramPacket(
                    buffer, buffer.length,
                    drone.address,
                    DRONE_PORT + drone.id
            );

            socket.send(packet);
            System.out.println("Sent countdown reset command to Drone " + drone.id);
        } catch (Exception e) {
            System.err.println("Error sending countdown reset to drone: " + e.getMessage());
        }
    }

    private void processPendingIncidents() {
        try {
            System.out.println("Incident processor started");

            while (shouldRun) {
                if (!pendingIncidents.isEmpty() && !idleDrones.isEmpty()) {
                    System.out.println("Processing pending incidents. Queue size: " + pendingIncidents.size() +
                            ", Available drones: " + idleDrones.size());

                    Incident incident = pendingIncidents.poll();
                    System.out.println("Processing incident in Zone " + incident.getZone());
                    assignDrone(incident);
                } else {
                    if (!pendingIncidents.isEmpty()) {
                        System.out.println("Waiting for available drones. Pending incidents: " +
                                pendingIncidents.size());
                    }
                }

                // Clean up stale drone registrations (older than 30 seconds)
                long currentTime = System.currentTimeMillis();
                idleDrones.removeIf(drone -> {
                    boolean isStale = (currentTime - drone.lastUpdateTime) > 30000;
                    if (isStale) {
                        System.out.println("Removing stale drone: Drone " + drone.id);

                        // Update status to offline
                        DroneStatus status = allDrones.get(drone.id);
                        if (status != null) {
                            status.state = "OFFLINE";
                            status.isAvailable = false;
                        }
                    }
                    return isStale;
                });

                Thread.sleep(5000);
            }
        } catch (InterruptedException e) {
            if (shouldRun) {
                System.err.println("Incident processor interrupted: " + e.getMessage());
            }
        }

        System.out.println("Incident processor stopped");
    }

    private void assignDrone(Incident incident) {
        Zone zone = getZoneById(incident.getZone());
        if (zone == null) {
            System.out.println("Zone ID " + incident.getZone() + " not found. Cannot assign drone.");
            return;
        }

        int targetX = zone.getCenterX();
        int targetY = zone.getCenterY();

        DroneInfo bestDrone = null;
        double bestDistance = Double.MAX_VALUE;

        for (DroneInfo drone : idleDrones) {
            double distance = Math.sqrt(Math.pow(drone.x - targetX, 2) + Math.pow(drone.y - targetY, 2));
            if (distance < bestDistance) {
                bestDistance = distance;
                bestDrone = drone;
            }
        }

        if (bestDrone != null) {
            idleDrones.remove(bestDrone);
            System.out.println("Assigning Drone " + bestDrone.id + " to incident in Zone " + incident.getZone());

            // Update drone status
            DroneStatus status = allDrones.get(bestDrone.id);
            if (status != null) {
                status.isAvailable = false;
                status.state = "ASSIGNED";
                status.currentIncident = incident;
            }

            sendDroneAssignment(bestDrone, incident, targetX, targetY);
        } else {
            System.out.println("No available drones for Zone " + incident.getZone());
        }
    }

    private void sendDroneAssignment(DroneInfo drone, Incident incident, int x, int y) {
        try (DatagramSocket socket = new DatagramSocket()) {
            String message = String.format("Assign,%d,%d,%d,%s,%s,%d,%s",
                    incident.getZone(), x, y, incident.getEventType(),
                    incident.getSeverity(), incident.getWaterAmountNeeded(), incident.getTime());

            byte[] buffer = message.getBytes();

            System.out.println("Sending assignment to Drone " + drone.id + " at " +
                    drone.address + " on port " + (DRONE_PORT + drone.id) +
                    ": " + message);

            DatagramPacket packet = new DatagramPacket(
                    buffer, buffer.length,
                    drone.address,
                    DRONE_PORT + drone.id
            );

            socket.send(packet);
            System.out.println("Assignment sent to Drone " + drone.id);
        } catch (Exception e) {
            System.err.println("Error sending assignment to drone: " + e.getMessage());
            e.printStackTrace();

            // Add drone back to idle list and update status
            DroneStatus status = allDrones.get(drone.id);
            if (status != null) {
                status.isAvailable = true;
                status.state = "IDLE";
                status.currentIncident = null;
            }

            idleDrones.add(drone);
        }
    }

    private Zone getZoneById(int id) {
        for (Zone zone : zones) {
            if (zone.getId() == id) {
                return zone;
            }
        }
        return null;
    }

    public void loadZones(String filename) {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line = br.readLine(); // Skip header
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                int zoneID = Integer.parseInt(parts[0].trim());

                String startCoords = parts[1].trim().replace("(", "").replace(")", "");
                String[] startXY = startCoords.split(";");
                int startX = Integer.parseInt(startXY[0].trim());
                int startY = Integer.parseInt(startXY[1].trim());

                String endCoords = parts[2].trim().replace("(", "").replace(")", "");
                String[] endXY = endCoords.split(";");
                int endX = Integer.parseInt(endXY[0].trim());
                int endY = Integer.parseInt(endXY[1].trim());

                zones.add(new Zone(zoneID, startX, startY, endX, endY));
            }

            System.out.println("Loaded " + zones.size() + " zones from " + filename);
        } catch (Exception e) {
            System.err.println("Failed to load zones: " + e.getMessage());
        }
    }

    public List<DroneInfo> getIdleDrones() {
        return idleDrones;
    }

    public Queue<Incident> getPendingIncidents() {
        return pendingIncidents;
    }

    public Map<Integer, DroneStatus> getAllDrones() {
        return allDrones;
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

    // New class to track drone status
    static class DroneStatus {
        DroneInfo droneInfo;
        String state = "UNKNOWN";
        boolean isAvailable = false;
        String faultMessage = null;
        Incident currentIncident = null;

        DroneStatus(DroneInfo droneInfo) {
            this.droneInfo = droneInfo;
        }
    }

    // You'll also need to add this Incident class if it's not already defined elsewhere
    static class Incident {
        private final String time;
        private final int zone;
        private final String eventType;
        private final String severity;
        private int waterAmountNeeded;

        public Incident(String time, int zone, String eventType, String severity) {
            this.time = time;
            this.zone = zone;
            this.eventType = eventType;
            this.severity = severity;
        }

        public String getTime() {
            return time;
        }

        public int getZone() {
            return zone;
        }

        public String getEventType() {
            return eventType;
        }

        public String getSeverity() {
            return severity;
        }

        public int getWaterAmountNeeded() {
            return waterAmountNeeded;
        }

        public void setWaterAmountNeeded(int waterAmountNeeded) {
            this.waterAmountNeeded = waterAmountNeeded;
        }
    }
}