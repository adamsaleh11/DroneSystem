import java.io.*;
import java.util.*;


public class FireIncidentSubsystem implements Runnable {
    private Scheduler scheduler;
    private String csvFile;

    /**
     * Constructor for FireIncidentSubsystem.
     */
    public FireIncidentSubsystem(Scheduler scheduler, String csvFile) {
        this.scheduler = scheduler;
        this.csvFile = csvFile;
    }

    @Override
    public void run() {
        System.out.println("FireIncidentSubsystem started...");
        readIncidentsFromCSV();
    }

    /**
     * Reads incidents from a CSV file and adds them to the Scheduler queue.
     */
    private void readIncidentsFromCSV() {
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] data = line.split(",");
                if (data.length < 4) continue; // Ignore malformed lines
                
                String time = data[0];
                int zoneID = Integer.parseInt(data[1]);
                String eventType = data[2];
                String severity = data[3];

                Incident incident = new Incident(time, zoneID, eventType, severity);
                scheduler.addIncident(incident); // Synchronization handled in Scheduler
                System.out.println("New incident added: " + incident.getEventType() + " at Zone " + incident.getZoneID());
            }
        } catch (IOException e) {
            System.err.println("Error reading incident CSV: " + e.getMessage());
        }
    }
