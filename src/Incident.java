import java.util.UUID;

public class Incident {
    private String time;
    private int zone;
    private String eventType;
    private String severity;
    private int waterAmountNeeded;
    private String incidentID;

    public Incident(String time, int zone, String eventType, String severity) {
        this.time = time;
        this.zone = zone;
        this.eventType = eventType;
        this.severity = severity;
        this.waterAmountNeeded = calculateWaterNeeded(severity);
        this.incidentID = UUID.randomUUID().toString().substring(0,8);
    }

    private int calculateWaterNeeded(String severity) {
        switch (severity) {
            case "Low": return 10;
            case "Moderate": return 20;
            case "High": return 30;
            default: return 15;
        }
    }

    public String getIncidentID() {
        return incidentID;
    }

    public void print() {
        System.out.println("Time: " + time);
        System.out.println("Zone Id: " + zone);
        System.out.println("Event type: " + eventType);
        System.out.println("Severity: " + severity);
        System.out.println("Water needed: " + waterAmountNeeded + "L");
    }

    public String getTime() {
        return time;
    }

    public int getZone() {
        return zone;
    }

    public String getEventType() {
        return eventType;
    }

    public String getSeverity() {
        return severity;
    }

    public int getWaterAmountNeeded() {
        return waterAmountNeeded;
    }

    public void setWaterAmountNeeded(int waterAmount) {
        this.waterAmountNeeded = waterAmount;
    }
}