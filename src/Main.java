import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
        List<Zone> zones = loadZones("src/resources/Sample_zone_file.csv");
        System.out.println("Loaded zones: " + zones.size());
        LAN.setZones(zones);

        DroneSubsytem drone = new DroneSubsytem(LAN, 1);
        LAN.addDrone(drone);

        Thread scheduler = new Thread(new Scheduler(LAN));
        Thread fireIncidentSystem = new Thread(new FireIncidentSubsystem(LAN, "src/resources/Sample_event_file.csv"));
        Thread droneSubsystem = new Thread(drone);
        scheduler.start();
        fireIncidentSystem.start();
        droneSubsystem.start();
        System.out.println("The drone system has been deployed. Waiting on instructions to proceed further.\n");
    }

    private static List<Zone> loadZones(String zoneFile) {
        List<Zone> zones = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(zoneFile))) {
            String line = br.readLine();
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                int zoneID = Integer.parseInt(parts[0].trim());
                String startStr = parts[1].trim();
                startStr = startStr.substring(1, startStr.length() - 1);
                String[] startCoords = startStr.split(";");
                int startX = Integer.parseInt(startCoords[0].trim());
                int startY = Integer.parseInt(startCoords[1].trim());
                String endStr = parts[2].trim();
                endStr = endStr.substring(1, endStr.length() - 1);
                String[] endCoords = endStr.split(";");
                int endX = Integer.parseInt(endCoords[0].trim());
                int endY = Integer.parseInt(endCoords[1].trim());
                zones.add(new Zone(zoneID, startX, startY, endX, endY));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return zones;
    }
}