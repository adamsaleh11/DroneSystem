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
    private int countdownTime;
    private volatile boolean isCountdownActive = true;

    public DroneSubsystem(int droneID, int xPosition, int yPosition, InetAddress schedulerAddress) throws SocketException {

        this.droneID = droneID;

        this.xPosition = xPosition;

        this.yPosition = yPosition;

        this.schedulerAddress = schedulerAddress;

        this.receiveSocket = new DatagramSocket(DRONE_PORT + droneID);

        this.sendSocket = new DatagramSocket();



        Random rand = new Random();

        this.countdownTime = rand.nextInt(10) + 3;

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
        Thread countdownThread = new Thread(this::startCountdown);
        listenerThread.start();
        statusThread.start();
        countdownThread.start();
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

                    if (message.startsWith("ResetCountdown")) {
                        enableCountdown();
                    }
                    else if (message.startsWith("Assign") && isAvailable.get()) {
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

    private void startCountdown() {
        try {
            while (shouldRun && countdownTime > 0) {
                System.out.println("Drone " + droneID + " countdown: " + countdownTime + " seconds.");
                countdownTime--;
                Thread.sleep(1000);
            }

            if (countdownTime == 0) {
                System.out.println("Drone " + droneID + " countdown reached zero. Stopping updates.");
                isCountdownActive = false;
                injectFault();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }



    private void injectFault() {
        System.out.println("Drone " + droneID + " state at countdown end: " + currentState);
        String faultMessage = "No fault detected.";
        boolean isFaultDetected = false;
        switch (currentState) {
            case DROPPING_AGENT:
                faultMessage = "ERROR: Drone Nozzle Malfunction";
                isFaultDetected = true;
                break;
            case RETURNING, EN_ROUTE:
                faultMessage = "ERROR: Drone is stuck in flight.";
                isFaultDetected = true;
                break;
            case IDLE:
                faultMessage = "ERROR: Drone Connection Lost Via Packet Loss";
                isFaultDetected = true;
                break;
            default:
                faultMessage = "No fault detected in current state.";
                break;
        }

        if (isFaultDetected) {
            sendFaultMessageToScheduler(faultMessage);
        }
    }

    public void enableCountdown() {
        isCountdownActive = true;
        countdownTime = new Random().nextInt(5) + 60; // Reset countdown timer
        System.out.println("Drone " + droneID + " countdown reactivated.");
    }

    private void sendFaultMessageToScheduler(String faultMessage) {
        try {
            String message = String.format("Drone %d Fault: %s", droneID, faultMessage);
            System.out.println("Sending fault message to scheduler: " + message);
            byte[] buffer = message.getBytes();
            DatagramPacket packet = new DatagramPacket(
                    buffer, buffer.length, schedulerAddress, DRONE_PORT);
            sendSocket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendPeriodicStatusUpdates() {
        try {
            while (shouldRun) {
                if (isAvailable.get() && isCountdownActive) {
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
            System.out.println("Incident assigned to drone.");
            System.out.println("Sending Drone " + droneID + " to: ");
            System.out.println("####INCIDENT####");
            System.out.println("Time: " + time);
            System.out.println("Zone ID: " + zoneId);
            System.out.println("Event Type: " + eventType);
            System.out.println("Severity: " + getSeverityLevel(severity));
            System.out.println("Water Needed: " + waterNeeded + "L\n");
            try {
                Random random = new Random();
                int travelTime = random.nextInt(7)+3 * 1000;
                setState(DroneState.EN_ROUTE);
                System.out.println("Drone " + droneID + " is EN_ROUTE to the incident location.");
                Thread.sleep(travelTime);
                setState(DroneState.DROPPING_AGENT);
                System.out.println("Drone " + droneID + " is dropping fire suppression agent.");
                Thread.sleep(2000);
                setState(DroneState.RETURNING);
                System.out.println("Drone " + droneID + " is returning to base.");
                Thread.sleep(travelTime);
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
            System.out.print("Enter number of drones: ");
            int numDrones = Integer.parseInt(scanner.nextLine().trim());
            Random rand = new Random();
            Thread[] droneThreads = new Thread[numDrones];
            for (int i = 0; i < numDrones; i++) {
                int x = 0;
                int y = 0;
                DroneSubsystem drone = new DroneSubsystem(i + 1, x, y, schedulerAddress);
                droneThreads[i] = new Thread(drone);
                droneThreads[i].start();
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
    public void enableDrone() {
        isCountdownActive = true;
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