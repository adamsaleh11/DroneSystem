import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.*;

class SchedulerTest {
    private LocalAreaNetwork lan;
    private Scheduler scheduler;
    private Thread schedulerThread;
//    private Scheduler scheduler;
    private static final int TEST_DRONE_ID = 1;
    private static final InetAddress LOCALHOST;
    static {
        try {
            LOCALHOST = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }
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
//        incident.setWaterAmountNeeded(500);
        scheduler.assignDrone(incident);

        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        droneSocket.receive(packet);
        String msg = new String(packet.getData(), 0, packet.getLength());
        assertTrue(msg.startsWith("Assign,1,"));
        droneSocket.close();
    }
}