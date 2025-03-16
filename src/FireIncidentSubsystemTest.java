import org.junit.Before;
import org.junit.Test;
import java.net.InetAddress;
import static org.junit.Assert.*;

public class FireIncidentSubsystemTest {

    private FireIncidentSubsystem fireSystem;

    @Before
    public void setUp() throws Exception {
        // Initialize the FireIncidentSubsystem with a dummy CSV file and localhost address
        fireSystem = new FireIncidentSubsystem("dummy.csv", InetAddress.getLocalHost());
    }

    @Test
    public void testInitialization() {
        // Verify that the subsystem is initialized correctly
        assertNotNull(fireSystem);
    }

    @Test
    public void testStop() {
        // Stop the subsystem
        fireSystem.stop();

        // Verify that the subsystem is stopped
        assertFalse(fireSystem.isRunning());
    }
}