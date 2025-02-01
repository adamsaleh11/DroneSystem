public class Scheduler extends Thread{
    LocalAreaNetwork lan;

    public Scheduler(LocalAreaNetwork lan) {
        this.lan = lan;
    }

    @Override
    public void run() {
        while(true) {
            Incident incident = lan.getIncident();
            if(incident == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            String message = lan.getDroneMessage();
            if(message == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
