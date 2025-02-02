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
            if (!lan.cleanZone()) {
                lan.removeFire();
                System.out.println("Drone: " + this.droneID + "has removed the fire");
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
        }

    }
}
