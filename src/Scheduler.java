import java.net.*;
import java.util.*;

public class Scheduler {
    private static final int FIRE_PORT = 5000;
    private static final int DRONE_PORT = 6000;
    private static final int SCHEDULER_PORT = 4000;

    private final List<DroneInfo> idleDrones = new ArrayList<>();
    private final Queue<Incident> pendingIncidents = new LinkedList<>();
    private boolean shouldRun = true;
    public static void main(String[] args) {
        Scheduler scheduler = new Scheduler();
        scheduler.start();
    }
    public void start() {
        Thread receiveIncidents = new Thread(this::listenForIncidents);
        Thread receiveDrones = new Thread(this::listenForDroneUpdates);
        Thread processIncidents = new Thread(this::processPendingIncidents);

        receiveIncidents.start();
        receiveDrones.start();
        processIncidents.start();

        System.out.println("Scheduler started and listening for incidents and drone updates");
    }
    private void listenForIncidents() {
        try (DatagramSocket socket = new DatagramSocket(SCHEDULER_PORT)) {
            System.out.println("Incident listener started on port " + SCHEDULER_PORT);
            while (shouldRun) {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength());
                System.out.println("Received Incident: " + message);

                // Parse incident (Example format: "Incident,ZoneID,X,Y")
                String[] parts = message.split(",");
                if (parts.length == 4 && parts[0].equals("Incident")) {
                    int zoneId = Integer.parseInt(parts[1]);
                    int zoneX = Integer.parseInt(parts[2]);
                    int zoneY = Integer.parseInt(parts[3]);

                    // Add the incident to the pending queue
                    pendingIncidents.add(new Incident(zoneId, zoneX, zoneY, packet.getAddress()));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void listenForDroneUpdates() {
        try (DatagramSocket socket = new DatagramSocket(DRONE_PORT)) {
            System.out.println("Drone listener started on port " + DRONE_PORT);

            while (shouldRun) {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());
                System.out.println("Received Drone Update: " + message);

                String[] parts = message.split(",");
                if (parts.length == 4 && parts[0].equals("Drone")) {
                    int id = Integer.parseInt(parts[1]);
                    int x = Integer.parseInt(parts[2]);
                    int y = Integer.parseInt(parts[3]);

                    boolean droneExists = false;
                    for (DroneInfo existingDrone : idleDrones) {
                        if (existingDrone.id == id) {
                            // Update position
                            existingDrone.x = x;
                            existingDrone.y = y;
                            droneExists = true;
                            break;
                        }
                    }

                    if (!droneExists) {
                        idleDrones.add(new DroneInfo(id, x, y, packet.getAddress()));
                        System.out.println("Added new drone: ID=" + id + ", position=(" + x + "," + y + ")");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void processPendingIncidents() {
        try {
            while (shouldRun) {
                // Try to assign drones to incidents when drones become available
                synchronized (this) {
                    if (!pendingIncidents.isEmpty() && !idleDrones.isEmpty()) {
                        Incident incident = pendingIncidents.poll();
                        assignDrone(incident.zoneId, incident.zoneX, incident.zoneY, incident.fireAddress);
                    }
                }

                // Sleep to avoid busy-waiting
                Thread.sleep(500);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void assignDrone(int zoneId, int zoneX, int zoneY, InetAddress fireAddress) {
        DroneInfo bestDrone = null;
        double bestDistance = Double.MAX_VALUE;

        for (DroneInfo drone : idleDrones) {
            double distance = Math.sqrt(Math.pow(drone.x - zoneX, 2) + Math.pow(drone.y - zoneY, 2));
            if (distance < bestDistance) {
                bestDistance = distance;
                bestDrone = drone;
            }
        }

        if (bestDrone != null) {
            idleDrones.remove(bestDrone);
            sendDroneAssignment(bestDrone, zoneId, zoneX, zoneY);
        } else {
            System.out.println("No available drones for Zone " + zoneId);
        }
    }

    private void sendDroneAssignment(DroneInfo drone, int zoneId, int x, int y) {
        try (DatagramSocket socket = new DatagramSocket()) {
            String message = "Assign," + zoneId + "," + x + "," + y;
            byte[] buffer = message.getBytes();

            DatagramPacket packet = new DatagramPacket(
                    buffer, buffer.length,
                    drone.address,
                    DRONE_PORT + drone.id
            );

            socket.send(packet);
            System.out.println("Assigned Drone " + drone.id + " to Zone " + zoneId);
        } catch (Exception e) {
            e.printStackTrace();
            idleDrones.add(drone);
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

        DroneInfo(int id, int x, int y, InetAddress address) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.address = address;
        }
    }

    static class Incident {
        int zoneId, zoneX, zoneY;
        InetAddress fireAddress;

        Incident(int zoneId, int zoneX, int zoneY, InetAddress fireAddress) {
            this.zoneId = zoneId;
            this.zoneX = zoneX;
            this.zoneY = zoneY;
            this.fireAddress = fireAddress;
        }
    }
}
