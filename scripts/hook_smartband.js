// Frida Hook for Mi Fitness (com.mi.health)
// Intercept real-time Gyroscope / Accelerometer from Xiaomi Smart Band 10

Java.perform(function () {
    console.log("[*] Injected into Mi Fitness process!");

    // 1. Hook Sensor Stream (Gyro & Accel)
    try {
        const SportDataServer = Java.use("com.xiaomi.fitness.sport_manager.server.SportDataServer");
        const SensorData = Java.use("com.xiaomi.fitness.sport_xms.data.SensorData");

        SportDataServer.onReceiveWearSensorData.implementation = function (wearSensorData) {
            if (wearSensorData != null) {
                const gyroList = wearSensorData.getGyro();
                if (gyroList != null && gyroList.size() > 0) {
                    const latest = Java.cast(gyroList.get(gyroList.size() - 1), SensorData);
                    const x = latest.getX();
                    const y = latest.getY();
                    const z = latest.getZ();
                    console.log(`[BAND GYRO LIVE] X: ${x.toFixed(2)} | Y: ${y.toFixed(2)} | Z: ${z.toFixed(2)}`);

                    // Forward to Arch Linux terminal via send()
                    send({
                        type: "GYRO_STREAM",
                        x: x,
                        y: y,
                        z: z,
                        timestamp: latest.getTimestamp()
                    });
                }
            }
            return this.onReceiveWearSensorData(wearSensorData);
        };
        console.log("[+] Hook onReceiveWearSensorData AKTIF!");
    } catch (e) {
        console.log("[-] Error hook sensor: " + e);
    }

    // 2. Export Helper Function: Trigger Getar Jam dari Terminal
    rpc.exports = {
        shakeBand: function (vibrateLevel) {
            Java.perform(function () {
                Java.choose("com.xiaomi.fitness.sport_xms.SportXmsApiImpl", {
                    onMatch: function (instance) {
                        console.log("[+] Memanggil shake(" + vibrateLevel + ") pada Smartband...");
                        instance.shake(vibrateLevel);
                    },
                    onComplete: function () {
                        console.log("[*] Selesai memicu getar.");
                    }
                });
            });
        }
    };
});
