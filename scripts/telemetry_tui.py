#!/usr/bin/env python3
import time
import os
import sys
import re

LOG_FILE = "/tmp/gyro_stream.log"

def clear():
    os.system('clear')

def main():
    print("\033[?25l", end="") # hide cursor
    last_hr = "--"
    last_resting_hr = "--"
    last_steps = "--"
    last_cal = "--"
    last_vitality = "--"
    last_momentum = "--"
    last_gyro = {"x": 0.0, "y": 0.0, "z": 0.0}
    recent_events = []

    # Read existing history
    if os.path.exists(LOG_FILE):
        with open(LOG_FILE, "r") as f:
            for line in f:
                line = line.strip()
                # Parse BPM
                m_bpm = re.search(r'"bpm":\s*(\d+)', line)
                if m_bpm:
                    if "resting_heart_rate" in line:
                        last_resting_hr = m_bpm.group(1)
                    else:
                        last_hr = m_bpm.group(1)
                # Parse Steps
                m_steps = re.search(r'"steps":\s*(\d+)', line)
                if m_steps:
                    last_steps = m_steps.group(1)
                # Parse Calories
                m_cal = re.search(r'"calories":\s*([\d\.]+)', line)
                if m_cal:
                    last_cal = m_cal.group(1)
                # Parse Vitality
                m_vit = re.search(r'"latest_accumulated_vitality":\s*(\d+)', line)
                if m_vit:
                    last_vitality = m_vit.group(1)
                # Parse Momentum
                m_mom = re.search(r'"body_momentum":\s*(\d+)', line)
                if m_mom:
                    last_momentum = m_mom.group(1)
                # Parse Gyro XYZ if valid floats
                m_gyro = re.search(r'"x":\s*([-\d\.]+).*?"y":\s*([-\d\.]+).*?"z":\s*([-\d\.]+)', line)
                if m_gyro:
                    try:
                        gx, gy, gz = float(m_gyro.group(1)), float(m_gyro.group(2)), float(m_gyro.group(3))
                        if abs(gx) < 500 and abs(gy) < 500 and abs(gz) < 500 and (gx != 0 or gy != 0 or gz != 0):
                            last_gyro = {"x": gx, "y": gy, "z": gz}
                    except:
                        pass

    # Open follow mode
    f = open(LOG_FILE, "r")
    f.seek(0, os.SEEK_END)

    while True:
        clear()
        print("\033[1;36m╔══════════════════════════════════════════════════════════════════════════════╗\033[0m")
        print("\033[1;36m║           PULSESYNC // HARDWARE TELEMETRY HUD (XIAOMI SMART BAND 10)         ║\033[0m")
        print("\033[1;36m╚══════════════════════════════════════════════════════════════════════════════╝\033[0m")
        print(f"\033[1;32m [STATUS]\033[0m SENSOR STREAMING AKTIF: MI FITNESS -> TCP 8080 (LAPTOP)")
        print()
        print(f"  \033[1;31m♥ HEART RATE\033[0m        : \033[1;37m{last_hr}\033[0m BPM   (Resting: \033[1;33m{last_resting_hr}\033[0m BPM)")
        print(f"  \033[1;33m⚡ VITALITY SCORE\033[0m   : \033[1;37m{last_vitality}\033[0m POIN  (Beban Latihan Aktif)")
        print(f"  \033[1;34m👟 STEPS & CALORIES\033[0m : \033[1;37m{last_steps}\033[0m LANGKAH | \033[1;37m{last_cal}\033[0m KKAL")
        print(f"  \033[1;32m🌊 BODY MOMENTUM\033[0m    : \033[1;37m{last_momentum}\033[0m KINETIC FORCE")
        print(f"  \033[1;35m📐 6-AXIS IMU (RAW)\033[0m : X: \033[1;36m{last_gyro.get('x', 0):+.2f}\033[0m  Y: \033[1;36m{last_gyro.get('y', 0):+.2f}\033[0m  Z: \033[1;36m{last_gyro.get('z', 0):+.2f}\033[0m")
        print()
        print("\033[1;30m────────────────────────────────────────────────────────────────────────────────\033[0m")
        print("\033[1;37m RECENT TELEMETRY EVENTS FROM SMARTBAND:\033[0m")
        for ev in recent_events[-6:]:
            print(f"  \033[1;30m>\033[0m {ev}")
        print("\033[1;30m────────────────────────────────────────────────────────────────────────────────\033[0m")
        print("\033[2m Tekan Ctrl+C untuk keluar\033[0m")

        for _ in range(5):
            line = f.readline()
            if line:
                line = line.strip()
                m_bpm = re.search(r'"bpm":\s*(\d+)', line)
                if m_bpm:
                    if "resting_heart_rate" in line:
                        last_resting_hr = m_bpm.group(1)
                        recent_events.append(f"\033[1;33m[RESTING HR]\033[0m Istirahat: {last_resting_hr} BPM")
                    else:
                        last_hr = m_bpm.group(1)
                        recent_events.append(f"\033[1;31m[HR]\033[0m Detak Jantung: {last_hr} BPM")

                m_steps = re.search(r'"steps":\s*(\d+)', line)
                if m_steps:
                    last_steps = m_steps.group(1)
                    recent_events.append(f"\033[1;34m[STEPS]\033[0m Langkah: {last_steps}")

                m_cal = re.search(r'"calories":\s*([\d\.]+)', line)
                if m_cal:
                    last_cal = m_cal.group(1)
                    recent_events.append(f"\033[1;33m[CAL]\033[0m Kalori: {last_cal} kkal")

                m_vit = re.search(r'"latest_accumulated_vitality":\s*(\d+)', line)
                if m_vit:
                    last_vitality = m_vit.group(1)
                    recent_events.append(f"\033[1;33m[VITALITY]\033[0m Skor: {last_vitality}")

                m_mom = re.search(r'"body_momentum":\s*(\d+)', line)
                if m_mom:
                    last_momentum = m_mom.group(1)
                    recent_events.append(f"\033[1;32m[MOMENTUM]\033[0m Gaya Gerak: {last_momentum}")

                m_gyro = re.search(r'"x":\s*([-\d\.]+).*?"y":\s*([-\d\.]+).*?"z":\s*([-\d\.]+)', line)
                if m_gyro:
                    try:
                        gx, gy, gz = float(m_gyro.group(1)), float(m_gyro.group(2)), float(m_gyro.group(3))
                        if abs(gx) < 500 and abs(gy) < 500 and abs(gz) < 500 and (gx != 0 or gy != 0 or gz != 0):
                            last_gyro = {"x": gx, "y": gy, "z": gz}
                            recent_events.append(f"\033[1;35m[GYRO]\033[0m X:{gx:+.2f} Y:{gy:+.2f} Z:{gz:+.2f}")
                    except:
                        pass
            time.sleep(0.2)

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\033[?25h")
