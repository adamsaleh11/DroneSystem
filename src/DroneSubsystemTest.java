import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.*;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class DroneSubsystemTest {

    private InetAddress schedulerAddress;
    private DatagramSocket sendSocket;
    private DatagramSocket receiveSocket;
    private DroneSubsystem droneSubsystem;

    private static final int RECEIVE_PORT = 9877;  // Port for receiving test messages

    @BeforeEach
    void setUp() throws Exception {
        schedulerAddress = InetAddress.getLocalHost();
        sendSocket = new DatagramSocket();
        receiveSocket = new DatagramSocket(RECEIVE_PORT);
        droneSubsystem = new DroneSubsystem(1, 0, 0, schedulerAddress);
        droneSubsystem.sendSocket = sendSocket;
    }

    @Test
    void testInjectFaultWhenStateIsIdle() throws IOException {
        droneSubsystem.setState(DroneSubsystem.DroneState.IDLE);
        droneSubsystem.injectFault();
        String expectedMessage = "Drone 1 Fault: ERROR: Drone Connection Lost Via Packet Loss";
        verifyMessageSent(expectedMessage);
    }

    @Test
    void testInjectFaultWhenStateIsDroppingAgent() throws IOException {
        droneSubsystem.setState(DroneSubsystem.DroneState.DROPPING_AGENT);
        droneSubsystem.injectFault();
        String expectedMessage = "Drone 1 Fault: ERROR: Drone Nozzle Malfunction";
        verifyMessageSent(expectedMessage);
    }

    @Test
    void testInjectFaultWhenStateIsEnRoute() throws IOException {
        droneSubsystem.setState(DroneSubsystem.DroneState.EN_ROUTE);
        droneSubsystem.injectFault();
        String expectedMessage = "Drone 1 Fault: ERROR: Drone is stuck in flight.";
        verifyMessageSent(expectedMessage);
    }

    @Test
    void testInjectFaultWhenStateIsReturning() throws IOException {
        // Simulate the state being RETURNING
        droneSubsystem.setState(DroneSubsystem.DroneState.RETURNING);

        // Call the injectFault() method
        droneSubsystem.injectFault();

        // Capture the fault message sent to the scheduler
        String expectedMessage = "Drone 1 Fault: ERROR: Drone is stuck in flight.";

        // Check if the message was sent to the scheduler
        verifyMessageSent(expectedMessage);
    }

    private void verifyMessageSent(String expectedMessage) throws IOException {
        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        receiveSocket.receive(packet);
        String actualMessage = new String(packet.getData(), 0, packet.getLength());
        assertEquals(expectedMessage, actualMessage, "Fault message sent doesn't match expected message");
    }
}
