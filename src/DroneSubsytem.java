public class DroneSubsytem implements Runnable{
    private  LocalAreaNetwork lan = new LocalAreaNetwork();
    private int droneID;
    DroneSubsytem(LocalAreaNetwork lan, int droneID) {
        this.lan = lan;
        this.droneID = droneID;
    }


    @Override
    public void run() {
        while(true) {
            if (lan.getNumIncidents() != 0) {
                lan.removeFire();
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {

            }
        }
    }
}
