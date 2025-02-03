import java.util.LinkedList;
import java.util.List;

public class LocalAreaNetwork {
    private List<Incident> incidents; //queue for Incidents from fire incident subsystem
    private List<String> droneMessages; //queue for drone logs
    private List<Incident> droneQueue; //queue for incidents assigned to drones

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

    //method to add incident to incidents queue
    public synchronized void addIncident(Incident incident) {
        incidents.addFirst(incident);
        System.out.println("##### Incident Added to scheduler ######");
        incident.print();
        notifyAll();
    }

    //method to take one incident form the incidents queue
    public synchronized Incident getIncident() {
        if (incidents.isEmpty()) {
            return null;
        }
        return incidents.removeLast();
    }

    public List<Incident> getIncidents() {
        return incidents;
    }

    //method to get drone message from drone message queue
    public synchronized String getDroneMessage(){
        if (droneMessages.isEmpty()) {
            return null;
        }
        return droneMessages.getLast();
    }

    //method to add drone log to drone message queue
    public synchronized void addDroneLog(String droneMessage) {
        droneMessages.add(droneMessage);
        notifyAll();
    }

    //method to assign incident to drone
    public synchronized void assignIncident(Incident incident) {
        System.out.println("Sending incident to available drone.");
        System.out.println("##### Incident Assigned to drone ######");
        incident.print();
        droneQueue.add(incident);
        notifyAll();
    }

    //method tocheck if incidents queue is empty
    public boolean checkIncident() {
        return incidents.isEmpty();
    }

    //method to send drone to a fire
    public synchronized boolean sendDrone() {
        if (!droneQueue.isEmpty()) {
            droneQueue.removeLast();
            notifyAll();
            return true;
        }
        return false;
    }

    //method to get number of incidents
    public int getNumIncidents() {
        return incidents.size();
    }

    //method for to check if a zone is clear of fires.
    public synchronized boolean cleanZone() {
        return droneQueue.isEmpty();
    }

}
