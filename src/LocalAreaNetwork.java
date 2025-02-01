import java.util.LinkedList;
import java.util.List;

public class LocalAreaNetwork {
    private List<Incident> incidents;
    private List<String> droneMessage;
    private List<Incident> droneQueue;

    public LocalAreaNetwork() {
        this.incidents = new LinkedList<>();

    }

    public synchronized void addIncident(Incident incident) {
        incidents.addFirst(incident);
    }

    public synchronized Incident getIncident() {
        return incidents.getLast();
    }

    public String getDroneMessage(){
        return droneMessage.getLast();
    }

    public void assignIncident(Incident incident) {
        droneQueue.add(incident);
        notify();
    }

    public synchronized Incident removeFire() {
        while(cleanZone()) {
            try {
                wait();
            } catch (InterruptedException e){}
        }
        return incidents.removeLast();
    }

    public int getNumIncidents() {
        return incidents.size();
    }

    public boolean cleanZone() {
        return incidents.isEmpty();
    }

}
