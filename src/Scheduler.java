public class Scheduler extends Thread {
    private final LocalAreaNetwork lan;
    private volatile boolean shouldRun = true;

    public Scheduler(LocalAreaNetwork lan) {
        this.lan = lan;
    }

    public void stopScheduler() {
        shouldRun = false;
    }

    @Override
    public void run() {
        while (shouldRun) {
            synchronized (lan) {
                while (lan.checkIncident()) {
                    try {
                        lan.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                Incident incident = lan.getIncident();
                if (incident != null) {
                    lan.assignIncident(incident);
                }
                lan.notifyAll();
            }

            synchronized (lan) {
                while (lan.getDroneMessage() == null) {
                    try {
                        lan.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                String droneMessage = lan.getDroneMessage();
                System.out.println("Scheduler: " + droneMessage);
                lan.notifyAll();
            }
        }
    }
}