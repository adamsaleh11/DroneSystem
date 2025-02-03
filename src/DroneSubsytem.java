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
                    System.out.println(lan.printDroneSuccess());
                } else {
                    System.out.println("FAILED");
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                lan.notifyAll();
            }
        }
    }
}
