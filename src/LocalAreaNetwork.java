import java.util.LinkedList;
import java.util.List;

public class LocalAreaNetwork {
    private List<Incident> incidents;
    private List<String> droneMessage;
    private List<Incident> droneQueue;

    public LocalAreaNetwork() {
        this.incidents = new LinkedList<>();
        this.droneMessage = new LinkedList<>();
        this.droneQueue = new LinkedList<>();

    }

    public synchronized void addIncident(Incident incident) {
        incidents.addFirst(incident);
        notifyAll();
    }

    public synchronized Incident getIncident() {
        return incidents.getLast();
    }

    public String getDroneMessage(){
        return droneMessage.getLast();
    }

    public void assignIncident(Incident incident) {
        droneQueue.add(incident);
        notifyAll();
    }

    public boolean checkIncident() {
        return incidents.isEmpty();
    }

    public synchronized Incident removeFire() {
        while(cleanZone()) {
            try {
                wait();
            } catch (InterruptedException e){}
        }
        return droneQueue.removeLast();
    }

    public int getNumIncidents() {
        return incidents.size();
    }

    public boolean cleanZone() {
        return droneQueue.isEmpty();
    }

}
