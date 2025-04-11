import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
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

}