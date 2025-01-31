import java.util.List;

public class LocalAreaNetwork {
    private List<Incident> incidents;

    public LocalAreaNetwork() {

    }

    public synchronized void addIncident(Incident incident) {
        incidents.addFirst(incident);
    }

    public synchronized Incident removeFire() {
        return incidents.removeLast();
    }

    public int getNumIncidents() {
        return incidents.size();
    }

}
