import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * FireIncidentSubsystem - Simulates fire incidents and interacts with the Scheduler.
 */
public class FireIncidentSubsystem implements Runnable {
    private static final int SCHEDULER_PORT = 5001; 
    private static final int INCIDENT_PORT = 5002; 
    private DatagramSocket socket;
    private CopyOnWriteArrayList<Incident> activeFires;
    private Map<Integer, String> fireZones;
    private final LocalAreaNetwork lan;
    private final String csvFile;
    
    public FireIncidentSubsystem(LocalAreaNetwork lan, String csvFile) {
        this.lan = lan;
        this.csvFile = csvFile;
        activeFires = new CopyOnWriteArrayList<>();
        fireZones = new HashMap<>();
        loadFireZones(csvFile);
        try {
            socket = new DatagramSocket(INCIDENT_PORT);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        readIncidentsFromCSV();
        generateFireIncidents();
        monitorFireSeverity();
    }

    /**
     * Loads fire zones from a CSV file.
     */
    private void loadFireZones(String filePath) {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 2) {
                    fireZones.put(Integer.parseInt(parts[0]), parts[1]);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Reads fire incidents from the CSV file and sends them to the scheduler.
     */
    private void readIncidentsFromCSV() {
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line;
            br.readLine(); // Skip header
            while ((line = br.readLine()) != null) {
                String[] data = line.split(",");
                if (data.length < 4) continue;

                String time = data[0];
                int zoneID = Integer.parseInt(data[1]);
                String eventType = data[2];
                String severity = data[3];

                Incident incident = new Incident(time, zoneID, eventType, severity);
                
                synchronized (lan) {
                    System.out.println("Reading report logs from CSV");
                    lan.addIncident(incident);
                    lan.notifyAll();
                }

                Thread.sleep(500); 
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends a fire alert to the Scheduler via UDP.
     */
    private void sendFireAlert(Incident fire) {
        try {
            InetAddress address = InetAddress.getLocalHost();
            String message = "FIRE," + fire.getZone() + "," + fire.getSeverity();
            byte[] buffer = message.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, SCHEDULER_PORT);
            socket.send(packet);
            System.out.println("[FireIncidentSubsystem] Sent fire alert: " + message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Simulates fire incidents occurring randomly.
     */
    public void generateFireIncidents() {
        new Thread(() -> {
            Random rand = new Random();
            while (true) {
                try {
                    Thread.sleep(rand.nextInt(10000) + 5000); 
                    int zoneId = (int) fireZones.keySet().toArray()[rand.nextInt(fireZones.size())];
                    String severity = String.valueOf(rand.nextInt(5) + 1); 
                    Incident fire = new Incident("Random", zoneId, "Fire", severity);
                    activeFires.add(fire);
                    sendFireAlert(fire);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * Monitors and escalates fire severity over time.
     */
    public void monitorFireSeverity() {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(5000); 
                    for (Incident fire : activeFires) {
                        if (fire.getSeverity() < 10) {
                            fire.increaseSeverity();
                            sendFireAlert(fire); 
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}
