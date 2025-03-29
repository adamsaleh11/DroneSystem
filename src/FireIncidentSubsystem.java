import java.io.*;

/**
 * This class represents the fire incidents system. Its responsible for reading data, initializing it
 * as an incident and then adding the incident to the share ressource.
 */
public class FireIncidentSubsystem implements Runnable {
    private final LocalAreaNetwork lan;
    private final String csvFile;
    // Shares incidents with the system.
    //Stores the path to the CSV file containing incident reports.
    public FireIncidentSubsystem(LocalAreaNetwork lan, String csvFile) {
        this.lan = lan;
        this.csvFile = csvFile;
    }
    //Enables communication with other system components.
    // Path to the CSV file containing fire incidents.

    @Override
    public void run() {
        readIncidentsFromCSV();
    }
    //This method is executed when the thread starts.

    private void readIncidentsFromCSV() {
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) { //Opens the CSV file and initializes a BufferedReader.
            String line;
            br.readLine();
            /**
             * This block reads a row from the csv and sends it to the scheduler
             */
            while ((line = br.readLine()) != null) { //Reads each line from the CSV file.
                String[] data = line.split(","); // Parses incident details:
                                               //Time, Zone ID, Event Type, Severity.
                if (data.length < 4) continue;

                String time = data[0];
                int zoneID = Integer.parseInt(data[1]);
                String eventType = data[2];
                String severity = data[3];

                Incident incident = new Incident(time, zoneID, eventType, severity); // Creates an Incident object using the extracted data.

                synchronized (lan) { //Ensures thread safety while modifying the LocalAreaNetwork (LAN).
                    System.out.println("Reading report logs from csv");
                    lan.addIncident(incident);
                    lan.notifyAll();
                }

                Thread.sleep(500); //  time delay
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
