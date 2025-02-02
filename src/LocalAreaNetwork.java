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

    public synchronized void addIncident(Incident incident) {
        incidents.addFirst(incident);
        notifyAll();
    }

    public synchronized Incident getIncident() {
        if (incidents.isEmpty()) {
            return null;
        }
        return incidents.getLast();
    }

    public synchronized String getDroneMessage(){
        if (droneMessages.isEmpty()) {
            return null;
        }
        return droneMessages.removeLast();
    }

    public synchronized void addDroneLog(String droneMessage) {
        droneMessages.add(droneMessage);
        notifyAll();
    }

    public synchronized void assignIncident(Incident incident) {
        droneQueue.add(incident);
        notifyAll();
    }

    public boolean checkIncident() {
        return incidents.isEmpty();
    }

    public synchronized void removeFire() {
        if (!incidents.isEmpty()) {
            Incident incident = incidents.removeLast();
            System.out.println("Fire removed at Zone " + incident.getZone());
            notifyAll();
        }
    }

    public int getNumIncidents() {
        return incidents.size();
    }

    public synchronized boolean cleanZone() {
        return droneQueue.isEmpty();
    }

}
