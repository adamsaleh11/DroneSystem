SYSC3303 A2 - 9
Martin Skachkov 101150140

No setup specific setup required just load up the folder and run the main function.

File Names:
DroneSystem : Drone Subsystem Thread responsible for dealing with fires
FireIncidentSubsystem : Fire Incident Subsystem thread, responsible for reporting incidents to the Local Area Netowrk
Scheduler : Scheduler Responsible for scheduling drones based on incidents reported by Fire Incident Subsystem
Incident : Basic Class holding information regarding incidents.
LocalAreaNework : Sharred Memory for threads with different queues for message streams between threads.

Unit Test Files
DroneSystemTest
FireIncidentSubsystemTest
SchedulerTest
LocalAreaNeworkTest
