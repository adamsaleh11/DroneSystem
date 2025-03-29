import java.net.*;
import java.io.IOException;
import java.util.Scanner;
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
    private DroneState currentState = DroneState.IDLE;

    /**
     * Constructor for DroneSubsystem
     * @param droneID Unique identifier for this drone
     * @param xPosition Initial X coordinate of the drone
     * @param yPosition Initial Y coordinate of the drone
     * @param schedulerAddress IP address of the scheduler
     */
    public DroneSubsystem(int droneID, int xPosition, int yPosition, InetAddress schedulerAddress) throws SocketException {
        this.droneID = droneID;
        this.xPosition = xPosition;
        this.yPosition = yPosition;
        this.schedulerAddress = schedulerAddress;
        this.receiveSocket = new DatagramSocket(DRONE_PORT + droneID);
        this.sendSocket = new DatagramSocket();
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
                    receiveSocket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength());
                    if (message.startsWith("Assign") && isAvailable.get()) {
                        isAvailable.set(false);
                        handleAssignment(message);
                        isAvailable.set(true);
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
                    buffer, buffer.length, schedulerAddress, SCHEDULER_PORT);
            sendSocket.send(packet);
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

            System.out.printf("Drone %d assigned to Zone %d at (%d, %d)%n",
                    droneID, zoneId, zoneX, zoneY);

            setState(DroneState.EN_ROUTE);
            System.out.println("Drone " + droneID + " is EN_ROUTE to the incident location.");

            try {
                Thread.sleep(3500);

                setState(DroneState.DROPPING_AGENT);
                System.out.println("Drone " + droneID + " is dropping fire suppression agent.");

                Thread.sleep(2000);

                setState(DroneState.RETURNING);
                System.out.println("Drone " + droneID + " is returning to base.");

                Thread.sleep(3500);

                setState(DroneState.IDLE);
                System.out.println("Drone " + droneID + " has successfully completed the mission.\n");

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("UNABLE TO FULFILL REQUEST. RETURNING TO BASE.\n");
            }
        }
    }

    /**
     * Sets the current state of the drone.
     *
     * @param newState The new state to transition to.
     */
    private void setState(DroneState newState) {
        this.currentState = newState;
        System.out.println("Drone " + droneID + " state changed to: " + newState);
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter the number of drones to deploy: ");
        int numDrones = 1;

        try {
            numDrones = scanner.nextInt();
            if (numDrones < 1) {
                System.out.println("Invalid input. Defaulting to 1 drone.");
                numDrones = 1;
            }
        } catch (Exception e) {
            System.out.println("Invalid input. Defaulting to 1 drone.");
        } finally {
            scanner.close();
        }

        try {
            InetAddress schedulerAddress = InetAddress.getLocalHost();
            Random random = new Random();

            for (int i = 1; i <= numDrones; i++) {
                int x = random.nextInt(100);
                int y = random.nextInt(100);
                DroneSubsystem drone = new DroneSubsystem(i, x, y, schedulerAddress);
                new Thread(drone).start();
                System.out.printf("Drone %d started at position (%d, %d)%n", i, x, y);
                Thread.sleep(200); // Stagger initialization
            }
        } catch (Exception e) {
            System.err.println("Error initializing drones: " + e.getMessage());
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

    public DroneState getCurrentState() {
        return currentState;
    }

    /**
     * Enum representing the possible states of a drone.
     */
    enum DroneState {
        IDLE,
        EN_ROUTE,
        DROPPING_AGENT,
        RETURNING
    }
}