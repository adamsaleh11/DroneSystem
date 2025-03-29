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

    public DroneSubsystem(int droneID, int xPosition, int yPosition, InetAddress schedulerAddress) throws SocketException {
        this.droneID = droneID;
        this.xPosition = xPosition;
        this.yPosition = yPosition;
        this.schedulerAddress = schedulerAddress;
        this.receiveSocket = new DatagramSocket(DRONE_PORT + droneID);
        this.sendSocket = new DatagramSocket();
    }

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
        System.out.println("The drone system has been deployed. Waiting on instructions to proceed further.\n");

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
            System.out.println("Drone " + droneID + " listening for assignments on port " + (DRONE_PORT + droneID));
            while (shouldRun) {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    receiveSocket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength());
                    System.out.println("Drone " + droneID + " received message: " + message);

                    if (message.startsWith("Assign") && isAvailable.get()) {
                        isAvailable.set(false);
                        handleAssignment(message);
                        isAvailable.set(true);
                    }
                } catch (IOException e) {
                    if (shouldRun && !(e instanceof SocketTimeoutException)) {
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
            System.out.println("Sending status to scheduler: " + message);
            byte[] buffer = message.getBytes();
            DatagramPacket packet = new DatagramPacket(
                    buffer, buffer.length, schedulerAddress, DRONE_PORT);
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
            String eventType = parts.length > 4 ? parts[4] : "UNKNOWN";
            String severity = parts.length > 5 ? parts[5] : "Unknown";
            int waterNeeded = parts.length > 6 ? Integer.parseInt(parts[6]) : 0;
            String time = parts.length > 7 ? parts[7] : "Unknown";

            // Print incident details
            System.out.println("Incident assigned to drone.");
            System.out.println("Sending Drone " + droneID + " to: ");
            System.out.println("####INCIDENT####");
            System.out.println("Time: " + time);
            System.out.println("Zone ID: " + zoneId);
            System.out.println("Event Type: " + eventType);
            System.out.println("Severity: " + getSeverityLevel(severity));
            System.out.println("Water Needed: " + waterNeeded + "L\n");

            try {
                // EN_ROUTE phase
                setState(DroneState.EN_ROUTE);
                System.out.println("Drone " + droneID + " is EN_ROUTE to the incident location.");
                Thread.sleep(3500);

                // DROPPING_AGENT phase
                setState(DroneState.DROPPING_AGENT);
                System.out.println("Drone " + droneID + " is dropping fire suppression agent.");
                Thread.sleep(2000);

                // RETURNING phase
                setState(DroneState.RETURNING);
                System.out.println("Drone " + droneID + " is returning to base.");
                Thread.sleep(3500);

                // Completion
                setState(DroneState.IDLE);
                System.out.println("DRONE SUCCESSFULLY COMPLETED & RETURNED FROM INCIDENT\n");

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("UNABLE TO FULFILL REQUEST. RETURNING TO BASE.\n");
            }
        }
    }

    private void setState(DroneState newState) {
        this.currentState = newState;
        System.out.println("Drone " + droneID + " state changed to: " + newState);
    }

    private int getSeverityLevel(String severity) {
        switch (severity) {
            case "Low": return 1;
            case "Moderate": return 2;
            case "High": return 3;
            default: return 0;
        }
    }

    public static void main(String[] args) {
        System.out.println("=== DRONE SUBSYSTEM STARTING ===");
        System.out.println("The drone system has been deployed. Waiting on instructions from scheduler.");
        System.out.println();

        Scanner scanner = new Scanner(System.in);
        int droneId = 1;  // Default drone ID

        try {
            System.out.print("Enter Scheduler IP address (press Enter for localhost): ");
            String ipAddress = scanner.nextLine().trim();
            InetAddress schedulerAddress;

            if (ipAddress.isEmpty()) {
                schedulerAddress = InetAddress.getLocalHost();
                System.out.println("Using localhost for scheduler address: " + schedulerAddress.getHostAddress());
            } else {
                schedulerAddress = InetAddress.getByName(ipAddress);
            }

            System.out.print("Enter Drone ID (press Enter for default ID=1): ");
            String droneIdStr = scanner.nextLine().trim();
            if (!droneIdStr.isEmpty()) {
                droneId = Integer.parseInt(droneIdStr);
            }

            System.out.print("Enter initial X position (press Enter for 0): ");
            String xStr = scanner.nextLine().trim();
            int x = xStr.isEmpty() ? 0 : Integer.parseInt(xStr);

            System.out.print("Enter initial Y position (press Enter for 0): ");
            String yStr = scanner.nextLine().trim();
            int y = yStr.isEmpty() ? 0 : Integer.parseInt(yStr);

            System.out.println("Starting Drone " + droneId + " at position (" + x + ", " + y + ")");
            System.out.println("Connecting to scheduler at " + schedulerAddress.getHostAddress());

            DroneSubsystem drone = new DroneSubsystem(droneId, x, y, schedulerAddress);
            Thread droneThread = new Thread(drone);
            droneThread.start();

            System.out.println("Press Enter to stop the drone subsystem...");
            scanner.nextLine();

            drone.stop();
            droneThread.join();
            System.out.println("Drone subsystem has been stopped.");

        } catch (Exception e) {
            System.err.println("Error initializing drone: " + e.getMessage());
            e.printStackTrace();
        } finally {
            scanner.close();
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

    enum DroneState {
        IDLE,
        EN_ROUTE,
        DROPPING_AGENT,
        RETURNING
    }
}