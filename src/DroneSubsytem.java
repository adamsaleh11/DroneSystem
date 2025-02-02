import java.util.List;

public class DroneSubsytem implements Runnable{
    private final LocalAreaNetwork lan;
    private final int droneID;
    DroneSubsytem(LocalAreaNetwork lan, int droneID) {
        this.lan = lan;
        this.droneID = droneID;
    }


    @Override
    public void run() {
        while(true) {
            synchronized (lan) {
                while(lan.cleanZone()) {
                    try {
                        lan.wait();
                    } catch(InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                if (!lan.cleanZone()) {
                    lan.removeFire(); // Removes the first event in `droneQueue`
                    lan.addDroneLog("Drone: " + this.droneID + " has removed an event \n");
                }
                lan.notifyAll();
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
