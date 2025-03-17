import java.io.*;
import java.net.*;

public class FireIncidentSubsystem implements Runnable {
    private static final int SCHEDULER_PORT = 4000; // Port where scheduler listens for incidents
    private final String csvFile;
    private final InetAddress schedulerAddress;
    private volatile boolean shouldRun = true;

    public FireIncidentSubsystem(String csvFile, InetAddress schedulerAddress) {
        this.csvFile = csvFile;
        this.schedulerAddress = schedulerAddress;
    }

    public void stop() {
        shouldRun = false;
    }

    public boolean isRunning() {
        return shouldRun;
    }

    @Override
    public void run() {
        if (shouldRun) {
            readIncidentsFromCSV();
        }
    }

    private void readIncidentsFromCSV() {
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line;
            br.readLine();
            while ((line = br.readLine()) != null && shouldRun) {
                String[] data = line.split(",");
                if (data.length < 4) continue;

                String time = data[0];
                int zoneID = Integer.parseInt(data[1]);
                String eventType = data[2];
                String severity = data[3];

                int x = 0, y = 0;
                if (data.length >= 6) {
                    x = Integer.parseInt(data[4]);
                    y = Integer.parseInt(data[5]);
                } else {

                    x = zoneID * 10;
                    y = zoneID * 5;
                }

                Incident incident = new Incident(time, zoneID, eventType, severity);

                System.out.println("Reading incident from CSV:");
                incident.print();

                sendIncidentToScheduler(zoneID, x, y);

                Thread.sleep(2000);
            }

            System.out.println("Finished reading all incidents from CSV file.");

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void sendIncidentToScheduler(int zoneID, int x, int y) {
        try {
            DatagramSocket socket = new DatagramSocket();

            String message = String.format("Incident,%d,%d,%d", zoneID, x, y);
            byte[] buffer = message.getBytes();

            DatagramPacket packet = new DatagramPacket(
                    buffer, buffer.length, schedulerAddress, SCHEDULER_PORT);

            socket.send(packet);
            System.out.println("Sent incident in Zone " + zoneID + " to scheduler at coordinates (" + x + ", " + y + ")");
            socket.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            InetAddress schedulerAddress = InetAddress.getLocalHost();
            FireIncidentSubsystem fireSystem = new FireIncidentSubsystem("src/resources/Sample_event_file.csv", schedulerAddress);
            new Thread(fireSystem).start();
            System.out.println("Started Fire Incident Subsystem");
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }
}