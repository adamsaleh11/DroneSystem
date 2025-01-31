public class Incident {
    private int time;
    private int zone;
    private String eventType;
    private String Severity;

    public Incident(int time, int zone, String eventType, String severity) {
        this.time = time;
        this.zone = zone;
        this.eventType = eventType;
        Severity = severity;
    }

    public int getTime() {
        return time;
    }

    public int getZone() {
        return zone;
    }

    public String getEventType() {
        return eventType;
    }

    public String getSeverity() {
        return Severity;
    }
}
