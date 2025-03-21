/**
 * This class is a simple object class that creates an incident. Used all throughout the project
 */
public class Incident {
    private String time;
    private int zoneID;
    private String eventType;
    private String severity;
    private int waterAmountNeeded;

    /**
     * Initializing the incident object from the csv data
     * @param time
     * @param zoneID
     * @param eventType
     * @param severity
     */
    Incident(String time, int zoneID, String eventType, String severity) {
        this.time = time;
        this.zoneID = zoneID;
        this.eventType = eventType;
        this.severity = severity;
        //Apply logic to determine amount of water needed. Could be in data or set based on severity level.
        switch (this.severity){
            case "Low":
                this.waterAmountNeeded = 10;
                break;
            case "Moderate":
                this.waterAmountNeeded = 20;
                break;
            case "High":
                this.waterAmountNeeded = 30;
                break;
        }
    }
    public String getTime() {
        return this.time;
    }

    public int getZone() {
        return this.zoneID;
    }

    public String getEventType() {
        return this.eventType;
    }
    public String getSeverity() {
        return this.severity;
    }
    public int getWaterAmountNeeded() {
        return this.waterAmountNeeded;
    }
    public void print() {
        System.out.println("Time: "+this.getTime()+
                "\nZone Id: " +this.getZone()+
                "\nEvent type: "+this.getEventType() +
                "\nSeverity: "+ this.getSeverity() +
                "\nWater needed: "+this.getWaterAmountNeeded() + "L\n");
    }
}

