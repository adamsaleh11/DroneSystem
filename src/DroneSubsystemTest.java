import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
