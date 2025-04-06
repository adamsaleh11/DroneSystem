//import org.junit.Before;
//import org.junit.After;
//import org.junit.Test;
//import java.net.*;
//import static org.junit.Assert.*;
//
//public class SchedulerTest {
//    private Scheduler scheduler;
//    private static final int TEST_DRONE_ID = 1;
//    private static final InetAddress LOCALHOST;
//
//    static {
//        try {
//            LOCALHOST = InetAddress.getLocalHost();
//        } catch (UnknownHostException e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    @Before
//    public void setUp() throws Exception {
//        scheduler = new Scheduler();
//        scheduler.loadZones("src/resources/Sample_zone_file.csv");
//        scheduler.start();
//        Thread.sleep(500);
//    }
//
//    @After
//    public void tearDown() throws Exception {
//        scheduler.stop();
//        Thread.sleep(500);
//    }
//
//    @Test
//    public void testFireToSchedulerCommunication() throws Exception {
//        DatagramSocket testSocket = new DatagramSocket();
//        String testMessage = "Incident,1,100,200,Fire,High,500,12:00:00";
//        testSocket.send(new DatagramPacket(
//                testMessage.getBytes(), testMessage.getBytes().length,
//                LOCALHOST, 4000));
//        testSocket.close();
//
//        Thread.sleep(500);
//        assertFalse(scheduler.getPendingIncidents().isEmpty());
//        assertEquals(1, scheduler.getPendingIncidents().peek().getZone());
//    }
//
//    @Test
//    public void testSchedulerToDroneCommunication() throws Exception {
//        DatagramSocket droneSocket = new DatagramSocket(6000 + TEST_DRONE_ID);
//        droneSocket.setSoTimeout(2000);
//
//        scheduler.getIdleDrones().add(new Scheduler.DroneInfo(
//                TEST_DRONE_ID, 0, 0, LOCALHOST));
//        scheduler.assignDrone(new Scheduler.Incident(
//                "12:00:00", 1, "Fire", "High"));
//
//        byte[] buffer = new byte[1024];
//        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
//        droneSocket.receive(packet);
//        assertTrue(new String(packet.getData(), 0, packet.getLength())
//                .startsWith("Assign,1,"));
//        droneSocket.close();
//    }
//
//    @Test
//    public void testStuckDroneFaultHandling() throws Exception {
//        Scheduler.DroneInfo testDrone = new Scheduler.DroneInfo(
//                TEST_DRONE_ID, 0, 0, LOCALHOST);
//        scheduler.getIdleDrones().add(testDrone);
//
//        Scheduler.DroneStatus status = new Scheduler.DroneStatus(testDrone);
//        status.state = "ASSIGNED";
//        status.currentIncident = new Scheduler.Incident(
//                "12:00:00", 1, "Fire", "High");
//        scheduler.getAllDrones().put(TEST_DRONE_ID, status);
//
//        String faultMessage = "Drone " + TEST_DRONE_ID +
//                " Fault: ERROR: Drone is stuck in flight";
//        new DatagramSocket().send(new DatagramPacket(
//                faultMessage.getBytes(), faultMessage.getBytes().length,
//                LOCALHOST, 6000));
//
//        Thread.sleep(500);
//        assertEquals(1, scheduler.getPendingIncidents().size());
//        assertEquals("IDLE", scheduler.getAllDrones().get(TEST_DRONE_ID).state);
//    }
//
//    public static void main(String[] args) throws Exception {
//        SchedulerTest test = new SchedulerTest();
//
//        System.out.println("1. Testing Fire->Scheduler communication...");
//        test.setUp();
//        test.testFireToSchedulerCommunication();
//        test.tearDown();
//
//        System.out.println("\n2. Testing Scheduler->Drone communication...");
//        test.setUp();
//        test.testSchedulerToDroneCommunication();
//        test.tearDown();
//
//        System.out.println("\n3. Testing fault handling...");
//        test.setUp();
//        test.testStuckDroneFaultHandling();
//        test.tearDown();
//
//        System.out.println("\nAll tests completed.");
//    }
//}