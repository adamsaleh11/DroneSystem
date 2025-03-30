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
    private static final int MAX_RETRY = 5;

    public DroneSubsystem(int droneID, int xPosition, int yPosition, InetAddress schedulerAddress) throws SocketException {
        this.droneID = droneID;
        this.xPosition = xPosition;
        this.yPosition = yPosition;
        this.schedulerAddress = schedulerAddress;
        this.receiveSocket = new DatagramSocket(DRONE_PORT + droneID);
        this.sendSocket = new DatagramSocket();
        Random rand = new Random();
        this.countdownTime = rand.nextInt(10) + 5;
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
                    String[] parts = message.split(",");
                    System.out.println("Drone " + droneID + " received message: RESET");
                    if (message.startsWith("ResetCountdown")) {
                        enableCountdown();
                    }
                    else if (message.startsWith("Assign") && isAvailable.get()) {
                        isAvailable.set(false);
                        TempIncident incident = new TempIncident(
                                parts[0],
                                Integer.parseInt(parts[1]),
                                Integer.parseInt(parts[2]),
                                Integer.parseInt(parts[3]),
                                parts[4],
                                parts[5],
                                Integer.parseInt(parts[6]),
                                parts[7]
                        );
                        handleAssignment(incident);
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
                countdownTime--;
                Thread.sleep(1000);
            }

            if (countdownTime == 0) {
                isCountdownActive = false;
                injectFault();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void injectFault() {
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

    public void enableCountdown() throws InterruptedException {
        isCountdownActive = true;
        countdownTime = new Random().nextInt(5) + 60; //
        Thread.sleep(1000);
        System.out.println("Drone " + droneID + " reactivated.");
    }

    private void sendFaultMessageToScheduler(String faultMessage) {
        try {
            String message = String.format("Drone %d Fault: %s", droneID, faultMessage);
            System.out.println("######ERROR######\nSending fault message to scheduler: " + message);
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
    private void handleAssignment(TempIncident incident) {
        if (incident != null) {
            int zoneId = incident.getId();
            int zoneX = incident.getX();
            int zoneY = incident.getY();
            String eventType = incident.getType() != null ? incident.getType() : "UNKNOWN";
            String severity = incident.getSeverity() != null ? incident.getSeverity() : "Unknown";
            int waterNeeded = incident.getPriority(); // Assuming priority represents water needed
            String time = incident.getTime() != null ? incident.getTime() : "Unknown";

            System.out.println("Incident assigned to drone.");
            System.out.println("Sending Drone " + droneID + " to: ");
            System.out.println("####INCIDENT####");
            System.out.println("Time: " + time);
            System.out.println("Zone ID: " + zoneId);
            System.out.println("Event Type: " + eventType);
            System.out.println("Severity: " + getSeverityLevel(severity));
            System.out.println("Water Needed: " + waterNeeded + "L\n");

            simulateTravel();
        }
    }

    public void simulateTravel() {
        try {
            Random random = new Random();
            int travelTime = (random.nextInt(7) + 3) * 1000; // Ensure multiplication is correct

            setState(DroneState.EN_ROUTE);
            System.out.println("Drone " + droneID + " is EN_ROUTE to the incident location.");
            if (!waitOrPause(travelTime)) return;

            setState(DroneState.DROPPING_AGENT);
            System.out.println("Drone " + droneID + " is dropping fire suppression agent.");
            if (!waitOrPause(travelTime)) return;

            setState(DroneState.RETURNING);
            System.out.println("Drone " + droneID + " is returning to base.");
            if (!waitOrPause(travelTime)) return;

            setState(DroneState.IDLE);
            System.out.println("DRONE SUCCESSFULLY COMPLETED & RETURNED FROM INCIDENT\n");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("UNABLE TO FULFILL REQUEST. RETURNING TO BASE.\n");
        }
    }

    private boolean waitOrPause(int duration) throws InterruptedException {
        int interval = 500; // Check every 500ms
        int waited = 0;
        int count = 0;
        while (waited < duration) {
            while (!isCountdownActive) {
                Thread.sleep(500);
                count++;
                if (count >= MAX_RETRY) {
                    System.out.println("Setting drone " + this.droneID +" to OFFLINE and shutting down...");
                    setDroneOffline();
                    return false;
                }
            }
            Thread.sleep(Math.min(interval, duration - waited));
            waited += interval;
        }
        return true;
    }

    private void setState(DroneState newState) {
        this.currentState = newState;
        System.out.println("Drone " + droneID + " state changed to: " + newState);
    }

    private void setDroneOffline() {
        this.currentState = DroneState.OFFLINE;
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
        int droneId = 1;
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
        RETURNING,
        OFFLINE
    }

}

class TempIncident {
    private String name;
    private int id;
    private int x;
    private int y;
    private String type;
    private String severity;
    private int priority;
    private String time;

    public TempIncident(String name, int id, int x, int y, String type, String severity, int priority, String time) {
        this.name = name;
        this.id = id;
        this.x = x;
        this.y = y;
        this.type = type;
        this.severity = severity;
        this.priority = priority;
        this.time = time;
    }

    @Override
    public String toString() {
        return "IncidentAssign{name='" + name + "', id=" + id +
                ", x=" + x + ", y=" + y + ", type='" + type +
                "', severity='" + severity + "', priority=" + priority +
                ", time='" + time + "'}";
    }

    public String getName() {
        return name;
    }

    public int getId() {
        return id;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public String getType() {
        return type;
    }

    public String getSeverity() {
        return severity;
    }

    public int getPriority() {
        return priority;
    }

    public String getTime() {
        return time;
    }
}