# WirelessSensorNetwork_frontend

This repository includes the implementation of an Android application that acts as a frontend of a monitoring framework which combines a Wireless Sensor Network (WSN) and mobile networks, employing a publish/subscribe mediation platform. To this end, the app will connect through a publish/subscribe protocol (in our case MQTT) to the server responsible for sending the messages. After connection established, it will be able to subscribe to a specific channel for the reception of such events. In addition, the application must store the activations produced by each sensor as a historical record.

The backend of the system is implemented in Java (available at https://github.com/fjordonez/WirelessSensorNetwork_backend).
