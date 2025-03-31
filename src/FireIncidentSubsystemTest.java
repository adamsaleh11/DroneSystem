import org.junit.Test;
import java.net.*;
import static org.junit.Assert.*;

public class FireIncidentSubsystemTest {
    private static final int TEST_PORT = 4000;

    @Test
    public void testUDPConnection() throws Exception {
        DatagramSocket receiver = new DatagramSocket(TEST_PORT);
        receiver.setSoTimeout(3000);
        FireIncidentSubsystem fireSystem = new FireIncidentSubsystem("src/resources/Sample_event_file.csv", InetAddress.getLocalHost());
        new Thread(fireSystem).start();
        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        receiver.receive(packet);
        String msg = new String(packet.getData(), 0, packet.getLength());
        assertTrue("First message should start with 'Incident'", msg.startsWith("Incident"));
        fireSystem.stop();
        receiver.close();
    }
}