public class Scheduler extends Thread{
    LocalAreaNetwork lan;

    public Scheduler(LocalAreaNetwork lan) {
        this.lan = lan;
    }

    @Override
    public void run() {
        while (true) {
            synchronized (lan) {
                while (lan.cleanZone()) {
                    try {
                        lan.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                Incident incident = lan.getIncident();
                lan.assignIncident(incident);
                lan.notifyAll();
            }

            synchronized (lan) {
                while (lan.getDroneMessage() == null) {
                    try {
                        lan.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }
}
