import socket
import json
import sys

LOG_FILE = "/tmp/gyro_stream.log"

def main():
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(('0.0.0.0', 8080))
    server.listen(10)
    print("[*] Gyro Telemetry Receiver AKTIF di 0.0.0.0:8080", flush=True)

    with open(LOG_FILE, "a") as f:
        f.write("[*] Gyro Telemetry Receiver AKTIF\n")

    while True:
        try:
            conn, addr = server.accept()
            data = conn.recv(1024)
            if data:
                text = data.decode('utf-8', errors='ignore').strip()
                out = f"[GYRO STREAM FROM BAND] {text}\n"
                print(out, end="", flush=True)
                with open(LOG_FILE, "a") as f:
                    f.write(out)
            conn.close()
        except Exception as e:
            pass

if __name__ == '__main__':
    main()
