package vsse.test;

import vsse.proto.TestOuterClass.TestRegRequest;

import static vsse.proto.TestOuterClass.TestRegRequest.DeviceType;

public class Device {
    private String deviceName;
    private int deviceId;
    private DeviceType type;

    public Device(DeviceType type, String deviceName) {
        this.type = type;
        this.deviceName = deviceName;
    }

    public Device(TestRegRequest trr, int i) {
        this.deviceId = i;
        this.deviceName = trr.getDeviceName();
        this.type = trr.getDeviceType();
    }

    @Override
    public String toString() {
        return "<" + type + "," + deviceId + "," + deviceName + ">";
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public int getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(int deviceId) {
        this.deviceId = deviceId;
    }

    public DeviceType getType() {
        return type;
    }

    public void setType(DeviceType type) {
        this.type = type;
    }
}