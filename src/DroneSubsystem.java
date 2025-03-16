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
                    registerWithScheduler();
                }

                Thread.sleep(5000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void registerWithScheduler() {
        try {
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

    private void handleAssignment(String assignmentMessage) {
        String[] parts = assignmentMessage.split(",");
        if (parts.length >= 4 && parts[0].equals("Assign")) {
            int zoneId = Integer.parseInt(parts[1]);
            int zoneX = Integer.parseInt(parts[2]);
            int zoneY = Integer.parseInt(parts[3]);

            System.out.println("Drone " + droneID + " assigned to Zone " + zoneId +
                    " at location (" + zoneX + ", " + zoneY + ")");

            try {
                double distance = Math.sqrt(Math.pow(xPosition - zoneX, 2) + Math.pow(yPosition - zoneY, 2));
                long travelTime = Math.max(1000, (long)(distance * 100)); // Scale factor for simulation

                System.out.println("Drone " + droneID + " traveling to Zone " + zoneId + "...");
                Thread.sleep(travelTime);

                this.xPosition = zoneX;
                this.yPosition = zoneY;

                System.out.println("Drone " + droneID + " arrived at Zone " + zoneId + ". Addressing incident...");
                Thread.sleep(2000);

                System.out.println("Drone " + droneID + " completed mission. Returning to base...");

                int baseX = droneID * 10;
                int baseY = droneID * 10;

                double returnDistance = Math.sqrt(Math.pow(xPosition - baseX, 2) + Math.pow(yPosition - baseY, 2));
                long returnTime = Math.max(1000, (long)(returnDistance * 100));
                Thread.sleep(returnTime);

                this.xPosition = baseX;
                this.yPosition = baseY;

                System.out.println("Drone " + droneID + " has returned to base after addressing Zone " + zoneId);

                registerWithScheduler();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }


    public static void main(String[] args) {
        try {
            InetAddress schedulerAddress = InetAddress.getLocalHost();

            Random random = new Random();
            for (int i = 1; i <= 1; i++) {
                int x = random.nextInt(100);
                int y = random.nextInt(100);

                DroneSubsystem drone = new DroneSubsystem(i, x, y, schedulerAddress);
                new Thread(drone).start();

                System.out.println("Started Drone " + i + " at position (" + x + ", " + y + ")");

                Thread.sleep(500);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isRunning() {
        return shouldRun;
    }

    public int getDroneID() {
        return droneID;
    }

    public int getXPosition() {
        return xPosition;
    }

    public int getYPosition() {
        return yPosition;
    }
}