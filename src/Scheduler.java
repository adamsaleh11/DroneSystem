public class Scheduler extends Thread {
    private final LocalAreaNetwork lan; //Sharred Memory for Threads
    private volatile boolean shouldRun = true; //Flag for testing

    public Scheduler(LocalAreaNetwork lan) {
        this.lan = lan;
    }
    //method to stop thread for testing
    public void stopScheduler() {
        shouldRun = false;
    }
    //This method runs the scheduler thread, it switches between checking for incidents and checking for drone messages
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
                lan.notifyAll();
            }
        }
    }
}