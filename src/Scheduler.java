public class Scheduler extends Thread{
    LocalAreaNetwork lan;

    public Scheduler(LocalAreaNetwork lan) {
        this.lan = lan;
    }

    @Override
    public void run() {
        while (true) {
            synchronized (lan) {
                while (lan.checkIncident()) {
                    try {
                        lan.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                Incident incident = lan.getIncident();
                if (incident != null) {
                    lan.assignIncident(incident);
                    System.out.println("Scheduler Thread Assigning Incident");
                }


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

                System.out.println("Scheduler received drone message.");
            }
        }
    }
}
