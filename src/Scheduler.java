import java.util.List;

public class Scheduler extends Thread {
    private final LocalAreaNetwork lan; //Shared Memory for Threads
    private volatile boolean shouldRun = true; //Flag for testing

    public Scheduler(LocalAreaNetwork lan) {
        this.lan = lan;
    }

    /**method to stop thread for testing
     *
     */
    public void stopScheduler() {
        shouldRun = false;
    }

    public void scheduleDrone(Incident incident) {
        List<DroneSubsytem> drones = lan.getIdleDrone();
        DroneSubsytem bestDrone = null;
        int zoneId = incident.getZone();
        Zone zone = lan.getZone(zoneId);
        if (zone == null) {
            System.out.println("No zone found for Zone ID: " + zoneId + ". Cannot schedule incident.");
            return;
        }
        int zoneCenterX = (zone.getStartX() + zone.getEndX()) / 2;
        int zoneCenterY = (zone.getStartY() + zone.getEndY()) / 2;
        double bestDistance = Double.MAX_VALUE;
        for (DroneSubsytem drone : drones) {
            int droneX = drone.getXCord();
            int droneY = drone.getYCord();
            double distance = Math.sqrt(Math.pow(droneX - zoneCenterX, 2) + Math.pow(droneY - zoneCenterY, 2));
            if (distance < bestDistance) {
                bestDistance = distance;
                bestDrone = drone;
            }
        }
        if(bestDrone != null) {
            lan.assignIncident(bestDrone, incident);
        } else {
            System.out.println("No idle drone available to assign the incident.");
        }
    }

    /**This method runs the scheduler thread, it switches
     * between checking for incidents and checking for drone messages
     */
    @Override
    public void run() {
        while (shouldRun) {
            synchronized (lan) {
                /**
                 * Waits until an incident is added to the incident queue
                 */
                while (lan.checkIncident()) {
                    try {
                        lan.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                /**
                 * Once an incident is retreived from the fire subsystem. It assigns it to the drone queue
                 */
                Incident incident = lan.getIncident();
                if (incident != null) {
                    scheduleDrone(incident);
                }
                lan.notifyAll();
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}