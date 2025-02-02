import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
class LocalAreaNetworkTest {

    @Test
    void addingIncident() {
        var localAreaNetwork = new LocalAreaNetwork();
        Incident incident = new Incident("10",10,"10","10");
        localAreaNetwork.addIncident(incident);
        assertEquals(1, localAreaNetwork.getIncidents().size());
    }

    @Test
    void removeIncident() {
        var localAreaNetwork = new LocalAreaNetwork();
        Incident incident = new Incident("10",10,"10","10");
        localAreaNetwork.addIncident(incident);
        var incidents = localAreaNetwork.getIncident();
        assertEquals(0, localAreaNetwork.getIncidents().size());
    }

    @Test
    void testAssignIncident() {
        var localAreaNetwork = new LocalAreaNetwork();
        Incident incident = new Incident("10",10,"10","10");
        localAreaNetwork.assignIncident(incident);
        assertEquals(1, localAreaNetwork.getDroneQueue().size());
    }

    @Test
    void testSendDrone() {
        var localAreaNetwork = new LocalAreaNetwork();
        Incident incident = new Incident("10",10,"10","10");
        localAreaNetwork.assignIncident(incident);
        assertTrue(localAreaNetwork.sendDrone());
    }

    @Test
    void testFailedSendDrone(){
        var localAreaNetwork = new LocalAreaNetwork();
        assertFalse(localAreaNetwork.sendDrone());
    }

    @Test
    void testGetNumIncidentsEmpty(){
        var localAreaNetwork = new LocalAreaNetwork();
        assertEquals(0, localAreaNetwork.getNumIncidents());
    }

    @Test
    void testGetNumIncidentsOne(){
        var localAreaNetwork = new LocalAreaNetwork();
        Incident incident = new Incident("10",10,"10","10");
        localAreaNetwork.addIncident(incident);
        assertEquals(1, localAreaNetwork.getNumIncidents());
    }

    @Test
    void testAddDroneLog(){
        var localAreaNetwork = new LocalAreaNetwork();
        localAreaNetwork.addDroneLog("Message");
        assertEquals(1, localAreaNetwork.getDroneMessages().size());
    }
}