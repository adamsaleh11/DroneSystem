import java.util.List;

/**
 * This class represents a drone subsystem. Its responsible for communicating to the scheduler
 * to see if any incidents are available.
 */
public class DroneSubsytem implements Runnable {
    private final LocalAreaNetwork lan;
    private final int droneID;
    private volatile boolean shouldRun = true;
    private String status = "IDLE";

    DroneSubsytem(LocalAreaNetwork lan, int droneID) {
        this.lan = lan;
        this.droneID = droneID;
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
                while (lan.cleanZone()) {
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
                        System.out.println(lan.getDroneMessage());
                        Thread.sleep(3500);
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
