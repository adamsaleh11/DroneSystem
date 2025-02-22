import java.util.List;

/**
 * This class represents a drone subsystem. Its responsible for communicating to the scheduler
 * to see if any incidents are available.
 */
public class DroneSubsytem implements Runnable {
    private final LocalAreaNetwork lan;
    private final int droneID;
    private volatile boolean shouldRun = true;
    private DroneState currentState = DroneState.IDLE;
    private int xCord;
    private int yCord;

    DroneSubsytem(LocalAreaNetwork lan, int droneID) {
        this.lan = lan;
        this.droneID = droneID;
        this.xCord = 0;
        this.yCord = 0;

    }

    public void stop() {
        shouldRun = false;
    }

    @Override
    public void run() {
        while (shouldRun) {
            synchronized (lan) {
                /**
                 * This block code checks to see if incidents are available. If not it will wait()
                 * but if there are it will send the drone to the location and print a message
                 */
                while (lan.cleanZone() && currentState == DroneState.IDLE) {
                    try {
                        lan.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                /**
                 * Drone id is used to differeniate threads and incidents
                 */
                if(lan.sendDrone(this.droneID)) {
                    try {
                        /**
                         * Simulate the drone going to the location and back. Round trip
                         */
                        setState(DroneState.EN_ROUTE);
                        System.out.println(lan.getDroneMessage());
                        Thread.sleep(3500);
                        setState(DroneState.DROPPING_AGENT);
                        Thread.sleep(2000);
                        setState(DroneState.RETURNING);
                        Thread.sleep(3500);
                        setState(DroneState.IDLE);
                        System.out.println(lan.printDroneSuccess());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    System.out.println("UNABLE TO FULFILL REQUEST. RETURNING TO BASE.\n");
                }
                lan.notifyAll();
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
    public int getXCord() {return  xCord;}
    public int getYCord() {return  yCord;}
    public DroneState getCurrentState() { return  currentState;}

    /**
     * Enum representing the possible states of a drone.
     */
    enum DroneState {
        IDLE,           // Drone is waiting for assignments
        EN_ROUTE,       // Drone is traveling to the incident location
        DROPPING_AGENT, // Drone is dropping fire suppression agent
        RETURNING       // Drone is returning to base
    }
}
