package org.fog.test.perfeval;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.fog.application.Application;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.*;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementMapping;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.Logger;
import org.fog.utils.distribution.DeterministicDistribution;

import java.util.*;

public class HealthMonitoringFog {

    static List<FogDevice> fogDevices = new ArrayList<>();
    static List<Sensor> sensors = new ArrayList<>();
    static List<Actuator> actuators = new ArrayList<>();

    public static void main(String[] args) {
        Logger.ENABLED = true;
        System.out.println("Starting Health Monitoring Simulation...");

        try {
            int numUser = 1;
            Calendar calendar = Calendar.getInstance();
            boolean traceFlag = false;

            CloudSim.init(numUser, calendar, traceFlag);

            String appId = "health_app";

            FogBroker broker = new FogBroker("broker");
            Application application = createApplication(appId, broker.getId());

            createFogDevices(broker.getId(), appId);
            ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();
            moduleMapping.addModuleToDevice("processing-module", "gateway");
            moduleMapping.addModuleToDevice("storage-module", "cloud");

            Controller controller = new Controller("master-controller", fogDevices, sensors, actuators);
            controller.submitApplication(application, 0, new ModulePlacementMapping(fogDevices, application, moduleMapping));

            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            System.out.println("Health Monitoring Simulation finished!");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("An error occurred: " + e.getMessage());
        }
    }

    private static Application createApplication(String appId, int userId) {
        Application application = Application.createApplication(appId, userId);

        application.addAppModule("processing-module", 10);
        application.addAppModule("storage-module", 10);

        String[] sensorTypes = {"HEART_RATE", "BLOOD_PRESSURE", "SPO2", "ECG", "TEMPERATURE"};

        for (String sensorType : sensorTypes) {
            application.addAppEdge(sensorType, "processing-module", 1000, 200, sensorType, Tuple.UP, AppEdge.SENSOR);
            application.addTupleMapping("processing-module", sensorType, "PROCESSED_DATA", new FractionalSelectivity(1.0));
        }

        application.addAppEdge("processing-module", "storage-module", 1000, 200, "PROCESSED_DATA", Tuple.UP, AppEdge.MODULE);
        application.addTupleMapping("storage-module", "PROCESSED_DATA", "DISPLAY_DATA", new FractionalSelectivity(1.0));
        application.addAppEdge("storage-module", "display", 1000, 200, "DISPLAY_DATA", Tuple.DOWN, AppEdge.ACTUATOR);

        List<AppLoop> loops = new ArrayList<>();
        for (String sensorType : sensorTypes) {
            loops.add(new AppLoop(Arrays.asList(sensorType, "processing-module", "storage-module", "display")));
        }
        application.setLoops(loops);

        return application;
    }

    private static void createFogDevices(int userId, String appId) {
        FogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0, 0.01, 16 * 103, 16 * 83.25);
        FogDevice gateway = createFogDevice("gateway", 2800, 4000, 100, 10000, 1, 0.0, 107.339, 83.4333);
        FogDevice mobile = createFogDevice("mobile", 2800, 4000, 100, 270, 2, 0.0, 87.53, 82.44);

        cloud.setParentId(-1);
        gateway.setParentId(cloud.getId());
        mobile.setParentId(gateway.getId());

        fogDevices.add(cloud);
        fogDevices.add(gateway);
        fogDevices.add(mobile);

        createSensorsAndActuators(appId, userId, "mobile");
    }

    private static FogDevice createFogDevice(String nodeName, long mips, int ram, long upBw, long downBw,
                                             int level, double ratePerMips, double busyPower, double idlePower) {
        List<Pe> peList = new ArrayList<>();
        peList.add(new Pe(0, new PeProvisionerSimple(mips)));

        int hostId = FogUtils.generateEntityId();
        long storage = 1000000;
        int bw = 10000;

        PowerHost host = new PowerHost(
                hostId,
                new RamProvisionerSimple(ram),
                new BwProvisionerSimple(bw),
                storage,
                peList,
                new StreamOperatorScheduler(peList),
                new FogLinearPowerModel(busyPower, idlePower)
        );

        List<Host> hostList = new ArrayList<>();
        hostList.add(host);

        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                "x86", "Linux", "Xen", host, 10.0, 3.0, 0.05, 0.001, 0.0
        );

        FogDevice device = null;
        try {
            device = new FogDevice(nodeName, characteristics,
                    new AppModuleAllocationPolicy(hostList), new LinkedList<>(), 10, upBw, downBw, 0, ratePerMips);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (device != null) {
            device.setLevel(level);
        }
        return device;
    }

    private static void createSensorsAndActuators(String appId, int userId, String deviceName) {
        String[] sensorTypes = {"HEART_RATE", "BLOOD_PRESSURE", "SPO2", "ECG", "TEMPERATURE"};

        for (String sensorType : sensorTypes) {
            Sensor sensor = new Sensor(sensorType, sensorType, userId, appId, new DeterministicDistribution(5.0));
            sensor.setGatewayDeviceId(getDeviceByName(deviceName).getId());
            sensor.setLatency(1.0);
            sensors.add(sensor);
        }

        Actuator display = new Actuator("display", userId, appId, "DISPLAY_DATA");
        display.setGatewayDeviceId(getDeviceByName(deviceName).getId());
        display.setLatency(1.0);
        actuators.add(display);
    }

    private static FogDevice getDeviceByName(String deviceName) {
        for (FogDevice device : fogDevices) {
            if (device.getName().equals(deviceName)) {
                return device;
            }
        }
        return null;
    }
}
