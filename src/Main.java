/**
 * This is the main class simulating the beginning of the Drone system simulation. The user is prompted
 * how many drones they need.
 */
public class Main {
    /**
     *
     * @param args
     */
    public static void main(String[] args) {
        LocalAreaNetwork LAN = new LocalAreaNetwork();
//        Scanner scanner = new Scanner(System.in);
//        System.out.println("Thank you contacting Fighting Fires hotline. How many drones do you need?\n");
////        int numDrones = scanner.nextInt();
//        for (int i = 0; i < numDrones; i++) {
//          Thread DroneSubSystem = new Thread(new DroneSubsytem(LAN, i));
//          DroneSubSystem.start();
//        }
        Thread scheduler = new Thread(new Scheduler(LAN));
        Thread fireIncidentSystem = new Thread(new FireIncidentSubsystem(LAN, "resources/Sample_event_file.csv"));
        Thread droneSubsystem = new Thread(new DroneSubsytem(LAN, 1));
        scheduler.start();
        fireIncidentSystem.start();
        droneSubsystem.start();
        System.out.println("The drone system has been deployed. Waiting on instructions to proceed further.\n");
    }
}