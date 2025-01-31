import java.util.List;

public class LocalAreaNetwork {
    private List<Incident> incidents;

    public LocalAreaNetwork() {

    }

    public void addIncident(Incident incident) {
        incidents.addFirst(incident);
    }

    public Incident getIncident() {
        return incidents.getLast();
    }

}
