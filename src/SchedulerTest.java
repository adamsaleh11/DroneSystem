import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SchedulerTest {
    private LocalAreaNetwork lan;
    private Scheduler scheduler;
    private Thread schedulerThread;

    @BeforeEach
    void setUp() {
        lan = new LocalAreaNetwork();
        scheduler = new Scheduler(lan);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (schedulerThread != null && schedulerThread.isAlive()) {
            schedulerThread.interrupt();
            schedulerThread.join();
        }
    }

    @Test
    void testSchedulerProcessesIncidentAndDroneMessage() throws InterruptedException {
        Incident incident = new Incident("13:00", 1, "Fire", "High");
        lan.addIncident(incident);
        lan.addDroneLog("Drone arrived at zone");
        schedulerThread = new Thread(scheduler);
        schedulerThread.start();
        Thread.sleep(200);
        schedulerThread.interrupt();
        schedulerThread.join();
        assertFalse(lan.getDroneMessages().isEmpty(), "Drone log should not be empty.");
        assertEquals("Drone arrived at zone", lan.getDroneMessages().get(0), "Expected drone message was not found.");
    }
}