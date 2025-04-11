import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.*;

class DroneSubsystemTest {

    private DroneSubsystem droneSubsystem;

    @BeforeEach
    void setUp() throws Exception {
        InetAddress schedulerAddress = InetAddress.getLocalHost();
        int uniqueDroneId = (int) (Math.random() * 1000 + 1);
        droneSubsystem = new DroneSubsystem(uniqueDroneId, 0, 0, schedulerAddress);
    }

    @AfterEach
    void tearDown() {
        if (droneSubsystem != null) {
            droneSubsystem.stop();
        }
    }

    @Test
    void testWaterCapacityResetsAfterSimulatedTravel() {
        Incident incident = new Incident("Fire", 1, "High", "12:00");
        incident.setWaterAmountNeeded(10);
        System.out.println("Initial water: " + droneSubsystem.getWaterCapacity());
        droneSubsystem.simulateTravel(incident, 10, 10);
        System.out.println("Water after travel: " + droneSubsystem.getWaterCapacity());
        assertEquals(40, droneSubsystem.getWaterCapacity(), "Water capacity should reset to 40 after travel");
        assertEquals(DroneSubsystem.DroneState.IDLE, droneSubsystem.getCurrentState(), "Drone should return to IDLE state");
    }

    @Test
    public void testDroneToSchedulerUDPMessage() throws Exception {
        int schedulerPort = 4000;
        String testMessage = "Drone,99,10,20,IDLE";

        Thread receiverThread = new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(schedulerPort)) {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.setSoTimeout(3000);
                socket.receive(packet);

                String received = new String(packet.getData(), 0, packet.getLength());
                System.out.println("Scheduler received: " + received);
                assertEquals(testMessage, received, "Received message should match sent message");
            } catch (Exception e) {
                fail("Receiver failed: " + e.getMessage());
            }
        });

        receiverThread.start();
        Thread.sleep(500);

        try (DatagramSocket sendSocket = new DatagramSocket()) {
            byte[] buffer = testMessage.getBytes();
            InetAddress address = InetAddress.getLocalHost();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, schedulerPort);
            sendSocket.send(packet);
        }

        receiverThread.join();
    }

    @Test
    void testWaterCapacityAlwaysResetsAfterIncident() {
        Incident incident = new Incident("Fire", 1, "High", "12:00");
        incident.setWaterAmountNeeded(15);
        System.out.println("Initial water: " + droneSubsystem.getWaterCapacity());
        droneSubsystem.simulateTravel(incident, 5, 5);
        System.out.println("Water after travel: " + droneSubsystem.getWaterCapacity());

        assertEquals(40, droneSubsystem.getWaterCapacity(), "Water should reset to 40 after mission completes");
    }

    @Test
    void testDistanceTraveledIncreases() {
        double before = droneSubsystem.getDistanceTraveled();

        Incident incident = new Incident("Fire", 1, "Medium", "15:30");
        incident.setWaterAmountNeeded(5);

        droneSubsystem.simulateTravel(incident, 6, 8);
        double after = droneSubsystem.getDistanceTraveled();

        assertTrue(after > before, "Distance traveled should increase");
    }

    @Test
    void testReturnToBaseAndReset() {
        droneSubsystem.setState(DroneSubsystem.DroneState.RETURNING);
        droneSubsystem.returnToBaseAndReset();

        assertEquals(0, droneSubsystem.getXPosition(), "X should be 0 after reset");
        assertEquals(0, droneSubsystem.getYPosition(), "Y should be 0 after reset");
        assertEquals(40, droneSubsystem.getWaterCapacity(), "Water should refill to 40");
        assertEquals(DroneSubsystem.DroneState.IDLE, droneSubsystem.getCurrentState(), "Drone should be IDLE after reset");
    }

    @Test
    void testInjectFaultSetsStateToFault() {
        droneSubsystem.setState(DroneSubsystem.DroneState.EN_ROUTE);
        droneSubsystem.injectFault();

        assertEquals(DroneSubsystem.DroneState.FAULT, droneSubsystem.getCurrentState(), "State should be FAULT after injectFault()");
    }
}
