import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import java.net.*;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.Assert.*;

public class SchedulerTest {
    private Scheduler scheduler;
    private static final int TEST_DRONE_ID = 1;
    private static final InetAddress LOCALHOST;

    static {
        try {
            LOCALHOST = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    @Before
    public void setUp() throws Exception {
        scheduler = new Scheduler();
        scheduler.loadZones("src/resources/Sample_zone_file.csv");
        scheduler.start();
        Thread.sleep(500);
    }

    @After
    public void tearDown() throws Exception {
        scheduler.stop();
        Thread.sleep(500);
    }

    @Test
    public void testFireToSchedulerCommunication() throws Exception {
        DatagramSocket testSocket = new DatagramSocket();
        String testMessage = "Incident,1,100,200,Fire,High,500,12:00:00";
        testSocket.send(new DatagramPacket(
                testMessage.getBytes(), testMessage.getBytes().length,
                LOCALHOST, 4000));
        testSocket.close();

        Thread.sleep(500);
        assertFalse(scheduler.getPendingIncidents().isEmpty());
        assertEquals(1, scheduler.getPendingIncidents().peek().getZone());
    }

    @Test
    public void testSchedulerToDroneCommunication() throws Exception {
        DatagramSocket droneSocket = new DatagramSocket(6000 + TEST_DRONE_ID);
        droneSocket.setSoTimeout(2000);

        Scheduler.DroneInfo droneInfo = new Scheduler.DroneInfo(TEST_DRONE_ID, 0, 0, LOCALHOST);
        Scheduler.DroneStatus status = new Scheduler.DroneStatus(droneInfo);
        status.state = "IDLE";
        status.isAvailable = true;
        scheduler.getAllDrones().put(TEST_DRONE_ID, status);

        Incident incident = new Incident("12:00:00", 1, "Fire", "High");
        incident.setWaterAmountNeeded(500);
        scheduler.assignDrone(incident);

        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        droneSocket.receive(packet);
        String msg = new String(packet.getData(), 0, packet.getLength());
        assertTrue(msg.startsWith("Assign,1,"));
        droneSocket.close();
    }

    @Test
    public void testStuckDroneFaultHandling() throws Exception {
        Scheduler.DroneInfo droneInfo = new Scheduler.DroneInfo(TEST_DRONE_ID, 0, 0, LOCALHOST);
        Scheduler.DroneStatus status = new Scheduler.DroneStatus(droneInfo);
        status.state = "ASSIGNED";
        status.currentIncident = new Incident("12:00:00", 1, "Fire", "High");
        scheduler.getAllDrones().put(TEST_DRONE_ID, status);

        String faultMessage = "Drone " + TEST_DRONE_ID +
                " Fault: ERROR: Drone is stuck in flight";
        new DatagramSocket().send(new DatagramPacket(
                faultMessage.getBytes(), faultMessage.getBytes().length,
                LOCALHOST, 6000));

        Thread.sleep(1000);
        assertEquals(1, scheduler.getPendingIncidents().size());
        assertTrue(scheduler.getAllDrones().get(TEST_DRONE_ID).isAvailable);
    }

    @Test
    public void testElapsedTimeCalculation() throws Exception {
        var incidentField = Scheduler.class.getDeclaredField("firstIncidentReceived");
        incidentField.setAccessible(true);
        incidentField.set(scheduler, LocalDateTime.now().minusMinutes(2));

        String formatted = scheduler.getElapsedTimeFormatted();
        System.out.println("Formatted time: " + formatted);
        assertTrue(formatted.startsWith("Elapsed Time: 02"));
    }

    @Test
    public void testAssignDroneLogic() {
        Scheduler.DroneInfo droneInfo = new Scheduler.DroneInfo(TEST_DRONE_ID, 0, 0, LOCALHOST);
        Scheduler.DroneStatus status = new Scheduler.DroneStatus(droneInfo);
        status.state = "IDLE";
        status.isAvailable = true;
        scheduler.getAllDrones().put(TEST_DRONE_ID, status);
        Incident incident = new Incident("12:01:00", 1, "Fire", "Medium");
        incident.setWaterAmountNeeded(100);
        scheduler.assignDrone(incident);
        Scheduler.DroneStatus updated = scheduler.getAllDrones().get(TEST_DRONE_ID);
        assertFalse(updated.isAvailable);
        assertEquals(incident, updated.currentIncident);
    }
}
