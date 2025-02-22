import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DroneSubsytemTest {
    private LocalAreaNetwork lan;
    private DroneSubsytem droneSubsystem;
    private Thread droneThread;

    @BeforeEach
    void setUp() {
        lan = new LocalAreaNetwork();
        droneSubsystem = new DroneSubsytem(lan, 1);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (droneThread != null && droneThread.isAlive()) {
            droneThread.interrupt();
            droneThread.join();
        }
    }

    @Test
    void testDroneRemovesFireAndLogsIt() throws InterruptedException {
        // Run actual methods
        Incident incident = new Incident("1",1,"1","1");
        DroneSubsytem drone = new DroneSubsytem(lan, 1);
        lan.assignIncident(drone,incident);

        droneThread = new Thread(droneSubsystem);
        droneThread.start();

        Thread.sleep(200);
        droneSubsystem.stop();
        droneThread.interrupt();
        droneThread.join();

        List<String> logs = lan.getDroneMessages();
        assertFalse(logs.isEmpty());
        assertTrue(logs.get(0).contains("Drone: 1 has removed the fire"));
    }
}