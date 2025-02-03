import java.util.LinkedList;
import java.util.List;

public class LocalAreaNetwork {
    private List<Incident> incidents;
    private List<String> droneMessages;
    private List<Incident> droneQueue;

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

    public synchronized void addIncident(Incident incident) {
        incidents.addFirst(incident);
        System.out.println("##### Incident Added to scheduler ######");
        incident.print();
        notifyAll();
    }

    public synchronized Incident getIncident() {
        if (incidents.isEmpty()) {
            return null;
        }
        return incidents.removeLast();
    }

    public List<Incident> getIncidents() {
        return incidents;
    }

    public synchronized String getDroneMessage(){
        if (droneMessages.isEmpty()) {
            return null;
        }
        return droneMessages.getLast();
    }

    public synchronized void addDroneLog(String droneMessage) {
        droneMessages.add(droneMessage);
        notifyAll();
    }

    public synchronized void assignIncident(Incident incident) {
        System.out.println("Sending incident to available drone.");
        System.out.println("##### Incident Assigned to drone ######");
        incident.print();
        droneQueue.add(incident);
        notify();
    }

    public boolean checkIncident() {
        return incidents.isEmpty();
    }

    public synchronized boolean sendDrone(int droneId) {
        if (!droneQueue.isEmpty()) {
            Incident currentAssignment = droneQueue.removeLast();
            String droneMessage = printIncidentDetails(currentAssignment, droneId);
            droneMessages.add(droneMessage);
            System.out.println(droneMessage);
            notifyAll();
            return true;
        }
        return false;
    }

    public int getNumIncidents() {
        return incidents.size();
    }

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
                "Water Needed: " + incident.getWaterAmountNeeded() + "\n";
    }

    public synchronized String printDroneSuccess() {
        return "DRONE SUCCESSFULLY COMPLETED & RETURNED FROM INCIDENT\n";
    }

}
