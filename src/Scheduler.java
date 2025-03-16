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

        receiveIncidents.start();
        receiveDrones.start();

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

                String[] parts = message.split(",");
                if (parts.length == 5 && parts[0].equals("Incident")) {
                    String time = parts[1];
                    int zoneId = Integer.parseInt(parts[2]);
                    String eventType = parts[3];
                    String severity = parts[4];

                    Incident incident = new Incident(time, zoneId, eventType, severity);
                    assignDrone(incident);
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
                            existingDrone.x = x;
                            existingDrone.y = y;
                            droneExists = true;
                            break;
                        }
                    }

                    if (!droneExists) {
                        DroneInfo newDrone = new DroneInfo(id, x, y, packet.getAddress());
                        idleDrones.add(newDrone);
                        System.out.println("Added new drone: ID=" + id + ", position=(" + x + "," + y + ")");
                        assignPendingIncidents();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void assignDrone(Incident incident) {
        DroneInfo bestDrone = null;
        double bestDistance = Double.MAX_VALUE;

        for (DroneInfo drone : idleDrones) {
            double distance = Math.sqrt(Math.pow(drone.x - incident.getZone(), 2) + Math.pow(drone.y - incident.getWaterAmountNeeded(), 2));
            if (distance < bestDistance) {
                bestDistance = distance;
                bestDrone = drone;
            }
        }

        if (bestDrone != null) {
            idleDrones.remove(bestDrone);
            sendDroneAssignment(bestDrone, incident);
        } else {
            System.out.println("No available drones for Zone " + incident.getZone() + ", adding to pending incidents.");
            pendingIncidents.add(incident);
        }
    }

    private void assignPendingIncidents() {
        Iterator<Incident> iterator = pendingIncidents.iterator();
        while (iterator.hasNext() && !idleDrones.isEmpty()) {
            Incident incident = iterator.next();
            assignDrone(incident);
            iterator.remove();
        }
    }

    private void sendDroneAssignment(DroneInfo drone, Incident incident) {
        try (DatagramSocket socket = new DatagramSocket()) {
            String message = "Assign," + incident.getZone() + "," + incident.getWaterAmountNeeded();
            byte[] buffer = message.getBytes();

            DatagramPacket packet = new DatagramPacket(
                    buffer, buffer.length,
                    drone.address,
                    DRONE_PORT + drone.id
            );

            socket.send(packet);
            System.out.println("Assigned Drone " + drone.id + " to Zone " + incident.getZone());
        } catch (Exception e) {
            e.printStackTrace();
            idleDrones.add(drone);
        }
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
} 