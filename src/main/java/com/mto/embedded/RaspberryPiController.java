package com.mto.embedded;

import com.google.gson.Gson;
import com.pi4j.component.sensor.Sensor;
import com.pi4j.component.sensor.SensorListener;
import com.pi4j.component.sensor.SensorStateChangeEvent;
import com.pi4j.component.sensor.impl.GpioSensorComponent;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.RaspiPin;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * @author <a href="hoang281283@gmail.com">Minh Hoang TO</a>
 * @date: 3/22/17
 */
public class RaspberryPiController {

    private final static int MQTT_QOS = 0;

    private final String mqttBroker;

    private final String mqttTopic;

    private GpioController gpio;

    private MqttAsyncClient asyncMqtt;

    private Sensor motionSensor;

    private final MotionSensorState msState;

    public RaspberryPiController() {
        this("tcp://localhost:1883", "mto/embedded/pi4j");
    }

    public RaspberryPiController(String _mqttBroker, String _mqttTopic) {
        mqttBroker = _mqttBroker;
        mqttTopic = _mqttTopic;

        try {
            asyncMqtt = new MqttAsyncClient(mqttBroker, MqttClient.generateClientId(), new MemoryPersistence());
            initializeMQTT();
        } catch (Exception ex) {
            //Throw un-check exception to avoid complex catching
            throw new RuntimeException("Problem connecting to MQTT broker", ex);
        }

        gpio = GpioFactory.getInstance();

        GpioPinDigitalInput msPin = gpio.provisionDigitalInputPin(RaspiPin.GPIO_04, PinPullResistance.PULL_DOWN);
        motionSensor = new GpioSensorComponent(msPin);
        motionSensor.addListener(new SensorListener() {
            @Override
            public void onStateChange(SensorStateChangeEvent e) {
                System.out.println("Pi4j sensor state event: " + e.getNewState());
                msState.tripped = motionSensor.isClosed();

                System.out.println("Publishing sensor state from SensorListener");
                publish(msState);
            }
        });

        msState = new MotionSensorState();
        msState.tripped = motionSensor.isClosed();

    }

    private void initializeMQTT() throws MqttException {
        MqttConnectOptions mqttOptions = new MqttConnectOptions();
        mqttOptions.setCleanSession(true);

        asyncMqtt.connect(mqttOptions, null, new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken iMqttToken) {
                RaspberryPiController.this.publish(msState);
            }

            @Override
            public void onFailure(IMqttToken iMqttToken, Throwable throwable) {

            }
        });
    }

    private void publish(MotionSensorState st) {
        try {
            String msg = new Gson().toJson(st);

            MqttMessage mqttMsg = new MqttMessage(msg.getBytes());
            mqttMsg.setRetained(true);
            mqttMsg.setQos(MQTT_QOS);

            asyncMqtt.publish(mqttTopic, mqttMsg);

            System.out.println("Published motion sensor state to MQTT: " + msg);
        } catch (MqttException ex) {
            ex.printStackTrace();
        }
    }

    public static void main(String[] args) {
        // Need to start application with a longlive Runnable to keep JVM live for long.
        // Nicer alternative would be to create GUI with Swing/JavaFX

        Runnable longTask = new Runnable() {
            @Override
            public void run() {
                new RaspberryPiController();

                while (!Thread.interrupted()) {
                    //TODO: Add heartbeat connection code here
                }
            }
        };

        Thread t = new Thread(longTask);
        t.setDaemon(true);

        t.start();
    }
}
