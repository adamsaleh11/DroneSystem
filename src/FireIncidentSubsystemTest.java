import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FireIncidentSubsystemTest {

    private LocalAreaNetwork mockLan;
    private Path tempFile;

    @AfterEach
    public void tearDown() throws IOException {
        Files.deleteIfExists(tempFile);
    }

    @Test
    public void testReadIncidentsFromCSV() throws IOException {
        // Write sample CSV data
        var mockLan = new LocalAreaNetwork();
        tempFile = Files.createTempFile("test_incidents", ".csv");
        String csvData = "Time,ZoneID,EventType,Severity\n" +
                "12:00,1,Fire,High\n"+
                "12:00,1,Fire,High\n";
        Files.write(tempFile, csvData.getBytes());

        // Create subsystem and call the method
        FireIncidentSubsystem subsystem = new FireIncidentSubsystem(mockLan, tempFile.toString());
        subsystem.run(); // Calls readIncidentsFromCSV()

        assertEquals(2, mockLan.getNumIncidents());
    }

    @Test
    public void testUDPConnection() throws Exception {
        DatagramSocket receiver = new DatagramSocket(TEST_PORT);
        receiver.setSoTimeout(3000);
        FireIncidentSubsystem fireSystem = new FireIncidentSubsystem("src/resources/Sample_event_file.csv", InetAddress.getLocalHost());
        new Thread(fireSystem).start();
        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        receiver.receive(packet);
        String msg = new String(packet.getData(), 0, packet.getLength());
        assertTrue("First message should start with 'Incident'", msg.startsWith("Incident"));
        fireSystem.stop();
        receiver.close();
    }
}

