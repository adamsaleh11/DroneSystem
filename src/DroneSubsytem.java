import java.util.List;

public class DroneSubsytem implements Runnable {
    private final LocalAreaNetwork lan;
    private final int droneID;
    private volatile boolean shouldRun = true;

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
                while (lan.cleanZone()) {
                    try {
                        lan.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (lan.sendDrone()) {
                    String message = "Drone: " + this.droneID + " has removed the fire/arrived to zone.\n";
                    lan.addDroneLog(message);
                }
                lan.notifyAll();
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
