import org.junit.Before;
import org.junit.Test;
import java.net.InetAddress;
import static org.junit.Assert.*;

public class DroneSubsystemTest {

    private DroneSubsystem drone;
    private InetAddress schedulerAddress;

    @Before
    public void setUp() throws Exception {
        // Initialize the DroneSubsystem with a dummy scheduler address
        schedulerAddress = InetAddress.getLocalHost();
        drone = new DroneSubsystem(1, 10, 20, schedulerAddress);
    }

    @Test
    public void testInitialization() {
        // Verify that the drone is initialized correctly
        assertNotNull(drone);
        assertEquals(1, drone.getDroneID());
        assertEquals(10, drone.getXPosition());
        assertEquals(20, drone.getYPosition());
    }

    @Test
    public void testStop() {
        // Stop the drone
        drone.stop();

        // Verify that the drone is stopped
        assertFalse(drone.isRunning());
    }
}