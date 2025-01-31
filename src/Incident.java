public class Incident {
    private String time;
    private int zoneID;
    private String eventType;
    private String severity;
    private int waterAmountNeeded;

    Incident(String time, int zoneID, String eventType, String severity) {
        this.time = time;
        this.zoneID = zoneID;
        this.eventType = eventType;
        this.severity = severity;
    }

    public String getTime() {
        return time;
    }

    public int getZoneID() {
        return zoneID;
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
}