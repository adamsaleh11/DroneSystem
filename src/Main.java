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
                String startCoords = parts[1].trim();
                startCoords = startCoords.substring(1, startCoords.length() - 1);
                String[] startingCoords = startCoords.split(";");
                int startX = Integer.parseInt(startingCoords[0].trim());
                int startY = Integer.parseInt(startingCoords[1].trim());
                String endCoords = parts[2].trim();
                endCoords = endCoords.substring(1, endCoords.length() - 1);
                String[] endingCoords = endCoords.split(";");
                int endX = Integer.parseInt(endingCoords[0].trim());
                int endY = Integer.parseInt(endingCoords[1].trim());
                zones.add(new Zone(zoneID, startX, startY, endX, endY));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return zones;
    }
}