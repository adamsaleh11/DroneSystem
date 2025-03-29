import java.io.BufferedReader;
import java.io.FileReader;
import java.net.*;
import java.util.*;

public class Scheduler {
    private static final int FIRE_PORT = 5000;
    private static final int DRONE_PORT = 6000;
    private static final int SCHEDULER_PORT = 4000;

    private final List<DroneInfo> idleDrones = new ArrayList<>();
    private final Queue<Incident> pendingIncidents = new LinkedList<>();
    private final List<Zone> zones = new ArrayList<>();

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

                    String[] parts = message.split(",");
                    if (parts.length == 4 && parts[0].equals("Drone")) {
                        int id = Integer.parseInt(parts[1]);
                        int x = Integer.parseInt(parts[2]);
                        int y = Integer.parseInt(parts[3]);

                        boolean droneExists = false;
                        for (DroneInfo existingDrone : idleDrones) {
                            if (existingDrone.id == id) {
                                existingDrone.x = x;
                                existingDrone.y = y;
                                existingDrone.lastUpdateTime = System.currentTimeMillis();
                                droneExists = true;
                                System.out.println("Updated Drone " + id + " position: (" + x + ", " + y + ")");
                                break;
                            }
                        }

                        if (!droneExists) {
                            idleDrones.add(new DroneInfo(id, x, y, packet.getAddress()));
                            System.out.println("New Drone " + id + " registered at position (" + x + ", " + y + ")");
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
                idleDrones.removeIf(drone ->
                        (currentTime - drone.lastUpdateTime) > 30000
                );

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
}