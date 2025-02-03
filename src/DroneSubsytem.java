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
                if(lan.sendDrone(this.droneID)) {
                    try {
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
}
