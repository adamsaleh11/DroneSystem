/**
 * This is the main class simulating the beginning of the Drone system simulation. The user is prompted
 * how many drones they need.
 */
public class Main {
    /**
     *Start of all of the treads
     * @param args
     */
    public static void main(String[] args) {
        LocalAreaNetwork LAN = new LocalAreaNetwork();
        Thread scheduler = new Thread(new Scheduler(LAN));
        Thread fireIncidentSystem = new Thread(new FireIncidentSubsystem(LAN, "src/resources/Sample_event_file.csv"));
        Thread droneSubsystem = new Thread(new DroneSubsytem(LAN, 1));
        scheduler.start();
        fireIncidentSystem.start();
        droneSubsystem.start();
        System.out.println("The drone system has been deployed. Waiting on instructions to proceed further.\n");
    }
}