import javax.swing.*;
import java.net.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class represents a drone subsystem. It's responsible for communicating with the scheduler
 * to register itself as available and receive incident assignments.
 */
public class DroneSubsystem implements Runnable {
    private static int DRONE_PORT = 6000;
    private static final int SCHEDULER_PORT = 4000;
    private final int droneID;
    private int xPosition;
    private int yPosition;
    private volatile boolean shouldRun = true;
    private final InetAddress schedulerAddress;
    private final AtomicBoolean isAvailable = new AtomicBoolean(true);
    private DatagramSocket receiveSocket;
    public DatagramSocket sendSocket;
    private DroneState currentState = DroneState.IDLE;
    private int countdownTime;
    private volatile boolean isCountdownActive = true;
    private static final int MAX_RETRY = 5;
    private volatile boolean faultInjected = false;
    private Incident currentIncident = null;
    private int waterCapacity;
    private double distanceTraveled;

    public DroneSubsystem(int droneID, int xPosition, int yPosition, InetAddress schedulerAddress) throws SocketException {
        this.droneID = droneID;
        this.xPosition = xPosition;
        this.yPosition = yPosition;
        this.schedulerAddress = schedulerAddress;
        this.receiveSocket = new DatagramSocket(DRONE_PORT + droneID);
        this.sendSocket = new DatagramSocket();
        Random rand = new Random();
        this.countdownTime = rand.nextInt(10) + 15;
        this.waterCapacity = 40;
        this.distanceTraveled = 0;
    }

    public void stop() {
        shouldRun = false;
        if (receiveSocket != null && !receiveSocket.isClosed()) receiveSocket.close();
        if (sendSocket != null && !sendSocket.isClosed()) sendSocket.close();
    }

    /**
     * Function invoked when thread is intialized
     */
    @Override
    public void run() {
        System.out.println("The drone system has been deployed. Waiting on instructions to proceed further.\n");
        Thread listenerThread = new Thread(this::listenForAssignments);
        listenerThread.start();
        sendStatusUpdate();
        try {
            listenerThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * The function is used to wait for messages from the scheduler. Once it receives an assignment
     * it calls helper function to update its movement and state
     */
    private void listenForAssignments() {
        try {
            System.out.println("Drone " + droneID + " listening for assignments on port " + (DRONE_PORT + droneID));
            while (shouldRun) {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    receiveSocket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength());
                    Thread.sleep(1000);

                    if (message.startsWith("ResetCountdown")) {
                        System.out.println("Drone " + droneID + " received RESET. Returning to base.");
                        returnToBaseAndReset();
                    } else if (message.startsWith("Assign") && isAvailable.get()) {
                        isAvailable.set(false);
                        String[] parts = message.split(",");
                        // parts[0] is "Assign"
                        int zone = Integer.parseInt(parts[1]);
                        int targetX = Integer.parseInt(parts[2]);
                        int targetY = Integer.parseInt(parts[3]);
                        Incident incident = new Incident(parts[7], zone, parts[4], parts[5]);
                        incident.setWaterAmountNeeded(Integer.parseInt(parts[6]));
                        handleAssignment(incident, targetX, targetY);
                        isAvailable.set(true);
                    } else if (message.startsWith("Reassign")) {
                        String[] parts = message.split(",");
                        int zone = Integer.parseInt(parts[1]);
                        int targetX = Integer.parseInt(parts[2]);
                        int targetY = Integer.parseInt(parts[3]);
                        Incident incident = new Incident(parts[7], zone, parts[4], parts[5]);
                        incident.setWaterAmountNeeded(Integer.parseInt(parts[6]));
                        System.out.println("Drone " + droneID + " received REASSIGNMENT to new incident.");
                        handleAssignment(incident, targetX, targetY);
                    }
                } catch (IOException e) {
                    if (shouldRun && !(e instanceof SocketTimeoutException)) e.printStackTrace();
                }
            }
        } catch (Exception e) {
            if (shouldRun) e.printStackTrace();
        }
    }

    /**
     * This helper function is called whenever an assignment has been to the drone
     * @param incident
     * @param targetX
     * @param targetY
     */
    private void handleAssignment(Incident incident, int targetX, int targetY) {
        if (incident != null && waterCapacity >= incident.getWaterAmountNeeded()) {
            this.currentIncident = incident;
            simulateTravel(incident, targetX, targetY);
        } else {
            System.out.printf("Drone %d has insufficient water. Needs %dL, has %dL.\n",
                    droneID, incident.getWaterAmountNeeded(), waterCapacity);
            sendReassignRequest(incident);
        }
    }

    /**
     * This function simulates travelling from the drones current position to the assignment position
     * We use pythogoreon theorum to simulate the distance between coordinates. We assume that each coordinate
     * based system is to a ratio of 1.
     * @param incident
     * @param targetX
     * @param targetY
     */
    public void simulateTravel(Incident incident, int targetX, int targetY) {
        double distance = Math.hypot(targetX - xPosition, targetY-yPosition);
        distance *=2;
        distanceTraveled += distance;
        double speedMps = 200 * 1000 / 3600.0; // 30 km/h -> meters per second (≈8.33 m/s)
        int travelTimeMs = (int) (distance / speedMps * 1000); // total time in milliseconds
        try {
            if (faultInjected) {
                System.out.println("Fault injected before travel began.");
                abortMissionDueToFault();
                return;
            }

            Random random = new Random();
            int travelTime = (random.nextInt(7) + 3) * 1000;
            int steps = 10;
            setState(DroneState.EN_ROUTE);
            sendStatusUpdate();
            for (int i = 1; i <= steps; i++) {
                if (faultInjected) {
                    System.out.println("Fault injected mid-travel.");
                    abortMissionDueToFault();
                    return;
                }
                // Move toward the target coordinates provided by the scheduler
                xPosition += (targetX - xPosition) / (steps - i + 1);
                yPosition += (targetY - yPosition) / (steps - i + 1);
                sendStatusUpdate();
                Thread.sleep(travelTimeMs/steps);
                if (!waitOrPause(travelTime / steps)) return;
            }

            setState(DroneState.DROPPING_AGENT);
            sendStatusUpdate();
            for (int i = 0; i <= steps; i++) {
                if (faultInjected) {
                    System.out.println("Fault injected after agent drop.");
                    abortMissionDueToFault();
                    return;
                }
                Thread.sleep(300);
            }
            waterCapacity -=incident.getWaterAmountNeeded();
            sendCompletionMessage(incident);
            if (!waitOrPause(travelTime)) return;

            setState(DroneState.RETURNING);
            sendStatusUpdate();
            for (int i = 1; i <= steps; i++) {
                if (faultInjected) {
                    System.out.println("Fault injected during return.");
                    abortMissionDueToFault();
                    return;
                }
                xPosition -= xPosition / (steps - i + 1);
                yPosition -= yPosition / (steps - i + 1);
                sendStatusUpdate();
                Thread.sleep(travelTimeMs/steps);
                if (!waitOrPause(travelTime / steps)) return;
            }

            waterCapacity = 40;
            setState(DroneState.IDLE);
            sendStatusUpdate();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Interrupted. Returning to base.");
        }
    }

    /**
     * This function is called when a fault is injected into a drone. It aborts
     * the current assignment if its called.
     */
    private void abortMissionDueToFault() {
        System.out.println("Drone " + droneID + " aborting mission due to fault.");

        // Drop the current incident immediately.
        Incident incidentToReassign = currentIncident;
        currentIncident = null;

        // If an incident was in progress, send a reassign request to ensure it is handled by another drone.
        if (incidentToReassign != null) {
//            sendReassignRequest(incidentToReassign);
        }

        // Change state so that the drone is not available for new assignments until it returns.
        // Then return to base (0,0).
        returnToBaseAndReset();

        // Reset the fault flag and send a final status update.
        faultInjected = false;
        sendStatusUpdate();
    }

    /**
     * This function is called to simualte to drone relaying back to the scheduler to reassign
     * an assignment if it doesnt have enough water capacity or if a fault is injected
     * @param incident
     */
    private void sendReassignRequest(Incident incident) {
        try {
            String message = String.format("Reassign,%d,%d,%s,%s,%d,%s",
                    droneID,
                    incident.getZone(),
                    incident.getEventType(),
                    incident.getSeverity(),
                    incident.getWaterAmountNeeded(),
                    incident.getTime());

            byte[] buffer = message.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, schedulerAddress, DRONE_PORT);
            sendSocket.send(packet);

            System.out.println("Drone " + droneID + " sent REASSIGN request for zone " + incident.getZone());
        } catch (IOException e) {
            System.err.println("Failed to send reassign request: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * This helper function is called to simulate the drone returning back to the origin base
     */
    void returnToBaseAndReset() {
        try {
            // Set state to RETURNING to block new assignments.
            setState(DroneState.RETURNING);
            int steps = 10;
            int travelTime = 3000;
            for (int i = 1; i <= steps; i++) {
                // Update position gradually to simulate travel.
                xPosition -= xPosition / (steps - i + 1);
                yPosition -= yPosition / (steps - i + 1);
                sendStatusUpdate();
                Thread.sleep(travelTime / steps);
            }
            // Ensure drone is exactly at the base.
            xPosition = 0;
            yPosition = 0;
            waterCapacity = 40;
            // Now set state to IDLE so the drone becomes available for assignments.
            setState(DroneState.IDLE);
            sendStatusUpdate();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Interrupted while resetting to base.");
        }
    }

    /**
     * This function is used to help the drone assess whether it needs to inject or fault
     * or not
     * @param duration
     * @return
     * @throws InterruptedException
     */
    private boolean waitOrPause(int duration) throws InterruptedException {
        long end = System.currentTimeMillis() + duration;
        while (System.currentTimeMillis() < end) {
            if (!isCountdownActive) Thread.sleep(100);
            else {
                long remaining = end - System.currentTimeMillis();
                if (remaining > 0) Thread.sleep(remaining);
                return true;
            }
        }
        forceRecovery();
        return false;
    }

    /**
     * Forces the drone to return to idle state if it recovers from a fault
     */
    private void forceRecovery() {
        System.out.println("Drone " + droneID + " FORCE-RECOVERING from fault state.");
        setState(DroneState.IDLE);
        enableCountdown();
    }

    /**
     * Helper function used to inject the fault
     */
    public void injectFault() {
        String faultMessage = null;
        switch (currentState) {
            case DROPPING_AGENT -> faultMessage = "ERROR: Drone Nozzle Malfunction";
            case RETURNING, EN_ROUTE -> faultMessage = "ERROR: Drone is stuck in flight.";
            case IDLE -> faultMessage = "ERROR: Drone Connection Lost Via Packet Loss";
        }

        if (faultMessage != null) {
            faultInjected = true;
            setState(DroneState.FAULT);
            sendFaultMessageToScheduler(faultMessage);
        }
    }

    /**
     * Helper function used to send a status update via UDP to the scheduler
     */
    private void sendStatusUpdate() {
        try {
            String message = String.format("Drone,%d,%d,%d,%s", droneID, xPosition, yPosition, currentState);
            byte[] buffer = message.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, schedulerAddress, DRONE_PORT);
            sendSocket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Helper function used to send a message update via UDP to the scheduler
     * @param faultMessage
     */
    private void sendFaultMessageToScheduler(String faultMessage) {
        try {
            String message = String.format("Drone %d Fault: %s", droneID, faultMessage);
            byte[] buffer = message.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, schedulerAddress, DRONE_PORT);
            sendSocket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Helper function used to send a completion update via UDP to the scheduler
     * @param incident
     */
    private void sendCompletionMessage(Incident incident) {
        try {
            String message = String.format("Complete,%d,%d,%s,%s,%s", droneID, incident.getZone(),
                    incident.getEventType(), incident.getSeverity(), incident.getTime());
            byte[] buffer = message.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, schedulerAddress, DRONE_PORT);
            sendSocket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Helper function to set the state of the drone
     * @param newState
     */
    public void setState(DroneState newState) {
        this.currentState = newState;
        sendStatusUpdate();
    }

    public void enableCountdown() {
        isCountdownActive = true;
    }

    /**
     * states of the drone
     */
    public enum DroneState {
        IDLE, EN_ROUTE, DROPPING_AGENT, RETURNING, OFFLINE, FAULT
    }

    public static void main(String[] args) {
        System.out.println("=== DRONE SUBSYSTEM STARTING ===");
        System.out.println("The drone system has been deployed. Waiting on instructions from scheduler.");
        System.out.println();

        Scanner scanner = new Scanner(System.in);
        try {
            InetAddress schedulerAddress = InetAddress.getLocalHost();
            System.out.println("Using localhost for scheduler address: " + schedulerAddress.getHostAddress());
            System.out.print("Enter number of drones: ");
            int numDrones = Integer.parseInt(scanner.nextLine().trim());

            List<DroneSubsystem> drones = new ArrayList<>();
            for (int i = 0; i < numDrones; i++) {
                int x = 0;
                int y = 0;
                DroneSubsystem drone = new DroneSubsystem(i + 1, x, y, schedulerAddress);
                drones.add(drone);
                new Thread(drone).start();
            }

            Map<Integer, DroneSubsystem> droneMap = new HashMap<>();
            for (DroneSubsystem drone : drones) {
                droneMap.put(drone.getDroneID(), drone);
            }

            DroneControlPanel.addControlPanelToExistingApplication(droneMap);

        } catch (Exception e) {
            System.err.println("Error initializing drone: " + e.getMessage());
            e.printStackTrace();
        } finally {
            scanner.close();
        }
    }

    /**
     * Helper functions used to retrieve private members of the drone
     * @return
     */
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
    public int getWaterCapacity() {
        return waterCapacity;
    }
    public double getDistanceTraveled() {
        return distanceTraveled;
    }
}
