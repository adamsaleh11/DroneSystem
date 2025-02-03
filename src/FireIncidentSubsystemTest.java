import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
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
}

