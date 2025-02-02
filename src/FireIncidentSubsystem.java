import java.io.*;

public class FireIncidentSubsystem implements Runnable {
    private final LocalAreaNetwork lan;
    private final String csvFile;

    public FireIncidentSubsystem(LocalAreaNetwork lan, String csvFile) {
        this.lan = lan;
        this.csvFile = csvFile;
    }

    @Override
    public void run() {
        readIncidentsFromCSV();
    }

    private void readIncidentsFromCSV() {
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line;
            br.readLine();
            while ((line = br.readLine()) != null) {
                String[] data = line.split(",");
                if (data.length < 4) continue;

                String time = data[0];
                int zoneID = Integer.parseInt(data[1]);
                String eventType = data[2];
                String severity = data[3];

                Incident incident = new Incident(time, zoneID, eventType, severity);

                synchronized (lan) {
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


