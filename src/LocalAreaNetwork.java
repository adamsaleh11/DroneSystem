import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * This class represents the shared resource between the 3 threads. In holds all of the data structures
 * and service calls to manipulate the data structures. Each thread uses this local area network to communicate
 * with eachother.
 */
public class LocalAreaNetwork {
    private List<Incident> incidents; //queue for Incidents from fire incident subsystem
    private List<String> droneMessages; //queue for drone logs
    private List<Incident> droneQueue;//queue for incidents assigned to drones
    private List<DroneSubsytem> drones; // index will be for drone id, strings will be status: IDLE, WORKING, MOVING.
    private List<Zone> zones;

    public LocalAreaNetwork() {
        this.incidents = new LinkedList<>();
        this.droneMessages = new LinkedList<>();
        this.droneQueue = new LinkedList<>();

    }

    public List<String> getDroneMessages() {
        return droneMessages;
    }

    public List<Incident> getDroneQueue() {
        return droneQueue;
    }

    public void setZones(List<Zone> zones) {
        this.zones = zones;
    }

    /**
     * method to add incident to incidents queue
     *
     * @param incident
     */
    public synchronized void addIncident(Incident incident) {
        incidents.addFirst(incident);
        System.out.println("##### Incident Added to scheduler ######");
        incident.print();
        notifyAll();
    }

    /**
     * method to take one incident form the incidents queue
     *
     * @return
     */
    public synchronized Incident getIncident() {
        if (incidents.isEmpty()) {
            return null;
        }
        Incident highestSeverityIncident = incidents.getLast();
        int highestIndex = 0;

        for (int i = 1; i < incidents.size(); i++){
            if(incidents.get(i).getSeverityNum() > highestSeverityIncident.getSeverityNum()) {
                highestSeverityIncident = incidents.get(i);
                highestIndex = i;
            }
        }
        return incidents.remove(highestIndex);
    }

    public List<Incident> getIncidents() {
        return incidents;
    }

    /**method to get drone message from drone message queue
     *
     * @return
     */
    public synchronized String getDroneMessage(){
        if (droneMessages.isEmpty()) {
            return null;
        }
        return droneMessages.getLast();
    }

    /**method to add drone log to drone message queue
     *
     * @param droneMessage
     */
    public synchronized void addDroneLog(String droneMessage) {
        droneMessages.add(droneMessage);
        notifyAll();
    }

    /**method to assign incident to drone
     *
     * @param incident
     */
    public synchronized void assignIncident(DroneSubsytem drone, Incident incident) {
        System.out.println("Sending incident to available drone.");
        System.out.println("##### Incident Assigned to drone ######");
        incident.print();
        droneQueue.add(drone.getDroneID(), incident);
        notify();
    }

    /**method tocheck if incidents queue is empty
     *
     * @return
     */
    public boolean checkIncident() {
        return incidents.isEmpty();
    }

    /**
     * Method to simulate sending a drone to an incident and removing the fire
     * @param droneId
     * @return
     */
    public synchronized boolean sendDrone(int droneId) {
        if (!droneQueue.isEmpty()) {
            Incident currentAssignment = droneQueue.removeLast();
            String droneMessage = printIncidentDetails(currentAssignment, droneId);
            droneMessages.add(droneMessage);
            //System.out.println(droneMessage);
            notifyAll();
            return true;
        }
        return false;
    }

    public List<DroneSubsytem> getIdleDrone() {
        List<DroneSubsytem> idleDroneIds = new ArrayList<>();

        for (int i = 0; i < drones.size(); i++) {
            if ("IDLE".equals(drones.get(i).getCurrentState())) {  // Improved string comparison
                idleDroneIds.add(drones.get(i));
            }
        }

        return idleDroneIds;
    }

    /**method to get number of incidents
     *
     * @return
     */
    public int getNumIncidents() {
        return incidents.size();
    }

    /**method for to check if a zone is clear of fires.
     *
     * @return
     */
    public synchronized boolean cleanZone() {
        return droneQueue.isEmpty();
    }

    public String printIncidentDetails(Incident incident, int droneID) {
        return  "Sending Drone "+ droneID + " to: \n" +
                "####INCIDENT####\n" +
                "Time: " + incident.getTime() + "\n" +
                "Zone ID: " + incident.getZone() + "\n" +
                "Event Type: " + incident.getEventType() + "\n" +
                "Severity: " + incident.getSeverity() + "\n" +
                "Water Needed: " + incident.getWaterAmountNeeded() + "L\n";
    }
    public synchronized String printDroneSuccess() {
        return "DRONE SUCCESSFULLY COMPLETED & RETURNED FROM INCIDENT\n";
    }

    public Zone getZone(int id) {
        for(int i = 0; i < zones.size(); i++) {
            if(zones.get(i).getId() == id) {
                return zones.get(i);
            }
        }
        return null;
    }
}
