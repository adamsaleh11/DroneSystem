import java.net.*;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class represents a drone subsystem. It's responsible for communicating with the scheduler
 * to register itself as available and receive incident assignments.
 */
public class DroneSubsystem implements Runnable {
    private static final int DRONE_PORT = 6000; // Port to receive assignments from scheduler
    private static final int SCHEDULER_PORT = 4000; // Port to send availability to scheduler

    private final int droneID;
    private int xPosition;
    private int yPosition;
    private volatile boolean shouldRun = true;
    private final InetAddress schedulerAddress;
    private final AtomicBoolean isAvailable = new AtomicBoolean(true);
    private DatagramSocket receiveSocket;
    private DatagramSocket sendSocket;

    /**
     * Constructor for DroneSubsystem
     * @param droneID Unique identifier for this drone
     * @param xPosition Initial X coordinate of the drone
     * @param yPosition Initial Y coordinate of the drone
     * @param schedulerAddress IP address of the scheduler
     */
    public DroneSubsystem(int droneID, int xPosition, int yPosition, InetAddress schedulerAddress) {
        this.droneID = droneID;
        this.xPosition = xPosition;
        this.yPosition = yPosition;
        this.schedulerAddress = schedulerAddress;

        try {
            this.receiveSocket = new DatagramSocket(DRONE_PORT + droneID); // Use unique port for receiving
            this.sendSocket = new DatagramSocket(); // Socket for sending availability updates
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    /**
     * Stops the drone's operation
     */
    public void stop() {
        shouldRun = false;
        if (receiveSocket != null && !receiveSocket.isClosed()) {
            receiveSocket.close();
        }
        if (sendSocket != null && !sendSocket.isClosed()) {
            sendSocket.close();
        }
    }

    @Override
    public void run() {
        // Start two threads: one for listening for assignments, one for periodic status updates
        Thread listenerThread = new Thread(this::listenForAssignments);
        Thread statusThread = new Thread(this::sendPeriodicStatusUpdates);

        listenerThread.start();
        statusThread.start();

        try {
            listenerThread.join();
            statusThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Continuously listens for assignment messages from the scheduler
     */
    private void listenForAssignments() {
        try {
            while (shouldRun) {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                try {
                    // Wait for an assignment message
                    receiveSocket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength());

                    // Process assignment message
                    if (message.startsWith("Assign") && isAvailable.get()) {
                        isAvailable.set(false); // Mark as busy
                        handleAssignment(message);
                        isAvailable.set(true); // Mark as available again
                    }
                } catch (IOException e) {
                    if (shouldRun) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            if (shouldRun) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Periodically sends status updates to the scheduler
     */
    private void sendPeriodicStatusUpdates() {
        try {
            while (shouldRun) {
                if (isAvailable.get()) {
                    // Only register if drone is available
                    registerWithScheduler();
                }

                // Send status update every few seconds
                Thread.sleep(10000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Registers this drone as available with the scheduler
     */
    private void registerWithScheduler() {
        try {
            // Format: "Drone,ID,X,Y"
            String message = String.format("Drone,%d,%d,%d", droneID, xPosition, yPosition);
            byte[] buffer = message.getBytes();

            DatagramPacket packet = new DatagramPacket(
                    buffer, buffer.length, schedulerAddress, DRONE_PORT);

            sendSocket.send(packet);
            System.out.println("Drone " + droneID + " registered as available at position (" +
                    xPosition + ", " + yPosition + ")");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Handles an assignment from the scheduler
     * @param assignmentMessage The assignment message from the scheduler
     */
    private void handleAssignment(String assignmentMessage) {
        String[] parts = assignmentMessage.split(",");
        if (parts.length >= 4 && parts[0].equals("Assign")) {
            int zoneId = Integer.parseInt(parts[1]);
            int zoneX = Integer.parseInt(parts[2]);
            int zoneY = Integer.parseInt(parts[3]);

            System.out.println("Drone " + droneID + " assigned to Zone " + zoneId +
                    " at location (" + zoneX + ", " + zoneY + ")");

            // Simulate the drone traveling to the location
            try {
                // Calculate simulated travel time based on distance
                double distance = Math.sqrt(Math.pow(xPosition - zoneX, 2) + Math.pow(yPosition - zoneY, 2));
                long travelTime = Math.max(1000, (long)(distance * 100)); // Scale factor for simulation

                System.out.println("Drone " + droneID + " traveling to Zone " + zoneId + "...");
                Thread.sleep(travelTime);

                // Update position to incident location
                this.xPosition = zoneX;
                this.yPosition = zoneY;

                // Simulate the operation at the site
                System.out.println("Drone " + droneID + " arrived at Zone " + zoneId + ". Addressing incident...");
                Thread.sleep(2000);

                // Simulate the return journey
                System.out.println("Drone " + droneID + " completed mission. Returning to base...");

                // Return to a base position
                int baseX = droneID * 10; // Simple logic for base positions
                int baseY = droneID * 10;

                double returnDistance = Math.sqrt(Math.pow(xPosition - baseX, 2) + Math.pow(yPosition - baseY, 2));
                long returnTime = Math.max(1000, (long)(returnDistance * 100));
                Thread.sleep(returnTime);

                // Update position to base location
                this.xPosition = baseX;
                this.yPosition = baseY;

                System.out.println("Drone " + droneID + " has returned to base after addressing Zone " + zoneId);

                // Send an immediate availability update after completing mission
                registerWithScheduler();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Main method for testing the drone subsystem
     */
    public static void main(String[] args) {
        try {
            // Get local scheduler address - replace with actual scheduler address in production
            InetAddress schedulerAddress = InetAddress.getLocalHost();

            // Create a few test drones with random positions
            Random random = new Random();
            for (int i = 1; i <= 3; i++) {
                int x = random.nextInt(100);
                int y = random.nextInt(100);

                DroneSubsystem drone = new DroneSubsystem(i, x, y, schedulerAddress);
                new Thread(drone).start();

                System.out.println("Started Drone " + i + " at position (" + x + ", " + y + ")");

                // Stagger drone startup slightly
                Thread.sleep(500);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}