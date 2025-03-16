import org.junit.Before;
import org.junit.Test;
import java.util.List;
import java.util.Queue;
import static org.junit.Assert.*;

public class SchedulerTest {

    private Scheduler scheduler;

    @Before
    public void setUp() {
        // Initialize the Scheduler before each test
        scheduler = new Scheduler();
    }

    @Test
    public void testSchedulerInitialization() {
        // Verify that the idleDrones list is empty
        assertNotNull(scheduler.getIdleDrones());
        assertTrue(scheduler.getIdleDrones().isEmpty());

        // Verify that the pendingIncidents queue is empty
        assertNotNull(scheduler.getPendingIncidents());
        assertTrue(scheduler.getPendingIncidents().isEmpty());
    }
}