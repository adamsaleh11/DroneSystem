import java.io.*;
import java.net.*;
import java.util.Scanner;

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
            // Skip header
            br.readLine();
            while ((line = br.readLine()) != null && shouldRun) {
                String[] data = line.split(",");
                if (data.length < 4) continue;

                String time = data[0];
                int zoneID = Integer.parseInt(data[1]);
                String eventType = data[2];
                String severity = data[3];

                // Create an incident object
                Incident incident = new Incident(time, zoneID, eventType, severity);

                System.out.println("Reading report logs from csv");
                System.out.println("##### Incident Added to scheduler ######");
                incident.print();

                // Send incident to scheduler with additional information
                sendIncidentToScheduler(incident);

                // Wait a moment before processing next incident
                Thread.sleep(7000);
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void sendIncidentToScheduler(Incident incident) {
        try {
            DatagramSocket socket = new DatagramSocket();

            String message = String.format("Incident,%d,%d,%d,%s,%s,%d,%s",
                    incident.getZone(),
                    incident.getZone() * 10, // X coordinate based on zone
                    incident.getZone() * 5,  // Y coordinate based on zone
                    incident.getEventType(),
                    incident.getSeverity(),
                    incident.getWaterAmountNeeded(),
                    incident.getTime());

            byte[] buffer = message.getBytes();

            DatagramPacket packet = new DatagramPacket(
                    buffer, buffer.length, schedulerAddress, SCHEDULER_PORT);

            socket.send(packet);
            socket.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        System.out.println("=== FIRE INCIDENT SUBSYSTEM STARTING ===");
        System.out.println("This subsystem will read incidents from a CSV file and send them to the scheduler.");
        System.out.println();

        Scanner scanner = new Scanner(System.in);

        try {
            System.out.print("Enter Scheduler IP address (press Enter for localhost): ");
            String ipAddress = scanner.nextLine().trim();
            InetAddress schedulerAddress;

            if (ipAddress.isEmpty()) {
                schedulerAddress = InetAddress.getLocalHost();
                System.out.println("Using localhost for scheduler address: " + schedulerAddress.getHostAddress());
            } else {
                schedulerAddress = InetAddress.getByName(ipAddress);
            }

            System.out.print("Enter CSV file path (press Enter for default 'src/resources/Sample_event_file.csv'): ");
            String csvPath = scanner.nextLine().trim();
            if (csvPath.isEmpty()) {
                csvPath = "src/resources/Sample_event_file.csv";
            }

            System.out.println("Using CSV file: " + csvPath);
            System.out.println("Connecting to scheduler at " + schedulerAddress.getHostAddress());

            FireIncidentSubsystem fireSystem = new FireIncidentSubsystem(csvPath, schedulerAddress);
            Thread fireThread = new Thread(fireSystem);
            fireThread.start();

            System.out.println("Fire Incident Subsystem is now running. Press Enter to stop.");
            scanner.nextLine();

            fireSystem.stop();
            fireThread.join();
            System.out.println("Fire Incident Subsystem has been stopped.");

        } catch (Exception e) {
            System.err.println("Error initializing Fire Incident Subsystem: " + e.getMessage());
            e.printStackTrace();
        } finally {
            scanner.close();
        }
    }
}