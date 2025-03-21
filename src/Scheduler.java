import java.net.*;
import java.util.*;

public class Scheduler {
    private static final int FIRE_PORT = 5000; // FireSubsystem sends incidents here
    private static final int DRONE_PORT = 6000; // Drones receive assignments here
    private static final int SCHEDULER_PORT = 4000; // Listening for incidents

    private final List<DroneInfo> idleDrones = new ArrayList<>();
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
    }

    private void listenForIncidents() {
        try (DatagramSocket socket = new DatagramSocket(SCHEDULER_PORT)) {
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

                    assignDrone(zoneId, zoneX, zoneY, packet.getAddress());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void listenForDroneUpdates() {
        try (DatagramSocket socket = new DatagramSocket(DRONE_PORT)) {

            while (shouldRun) {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());
                System.out.println("Received Drone Update: " + message);

                // Parse drone info (Example format: "Drone,ID,X,Y")
                String[] parts = message.split(",");
                if (parts.length == 4 && parts[0].equals("Drone")) {
                    int id = Integer.parseInt(parts[1]);
                    int x = Integer.parseInt(parts[2]);
                    int y = Integer.parseInt(parts[3]);

                    idleDrones.add(new DroneInfo(id, x, y, packet.getAddress()));
                }
            }
        } catch (Exception e) {
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
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, drone.address, DRONE_PORT);
            socket.send(packet);

            System.out.println("Assigned Drone " + drone.id + " to Zone " + zoneId);
        } catch (Exception e) {
            e.printStackTrace();
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