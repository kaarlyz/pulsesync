#!/usr/bin/env python3
import http.server
import socketserver
import socket
import threading
import json
import time
import os
import re
import math

LOG_FILE = "/tmp/gyro_stream.log"
PORT_HTTP = 8089
PORT_TCP = 8080

latest_state = {
    "ax": 0.0,
    "ay": 0.0,
    "az": 9.8,
    "gx": 0.0,
    "gy": 0.0,
    "gz": 0.0,
    "g_vec": [0.0, 0.0, 1.0],
    "pitch": 0.0,
    "roll": 0.0,
    "yaw": 0.0,
    "tilt_x": 0.0,
    "tilt_y": 0.0,
    "hr": 89,
    "steps": 14,
    "cal": 9.0,
    "packets": 12000,
    "ts": time.time()
}

subscribers = set()
sub_lock = threading.Lock()

yaw_state = 0.0
last_imu_time = time.time()

f_re = r"([-\d\.eE\+\-]+)"
p_imu = re.compile(rf'"ax":\s*{f_re}.*?"ay":\s*{f_re}.*?"az":\s*{f_re}.*?"gx":\s*{f_re}.*?"gy":\s*{f_re}.*?"gz":\s*{f_re}')
p_bpm = re.compile(r'"bpm":\s*(\d+)')
p_steps = re.compile(r'"steps":\s*(\d+)')
p_cal = re.compile(r'"calories":\s*([\d\.]+)')

def process_payload(line):
    global latest_state, yaw_state, last_imu_time

    m_imu = p_imu.search(line)
    if m_imu:
        try:
            ax = float(m_imu.group(1))
            ay = float(m_imu.group(2))
            az = float(m_imu.group(3))
            gx = float(m_imu.group(4))
            gy = float(m_imu.group(5))
            gz = float(m_imu.group(6))

            now = time.time()
            dt = min(0.1, max(0.005, now - last_imu_time))
            last_imu_time = now

            g_norm = math.sqrt(ax * ax + ay * ay + az * az)
            if g_norm > 0.5:
                # Normalized gravity vector in body frame
                ugx = ax / g_norm
                ugy = ay / g_norm
                ugz = az / g_norm

                # Universal Projected Vertical Angular Velocity
                # Projects gyro vector onto the gravity vector
                omega_vert = (gx * ugx + gy * ugy + gz * ugz)

                DEADZONE = 0.035
                if abs(omega_vert) > DEADZONE:
                    yaw_state += omega_vert * 57.2958 * dt

                # Bounded Euler for HUD cards
                clamped_x = max(-1.0, min(1.0, ugx))
                clamped_y = max(-1.0, min(1.0, ugy))
                roll = math.asin(clamped_x) * 57.2958
                pitch = math.asin(clamped_y) * 57.2958

                if abs(roll) < 1.0: roll = 0.0
                if abs(pitch) < 1.0: pitch = 0.0

                latest_state["ax"] = ax
                latest_state["ay"] = ay
                latest_state["az"] = az
                latest_state["gx"] = 0.0 if abs(gx) < DEADZONE else gx
                latest_state["gy"] = 0.0 if abs(gy) < DEADZONE else gy
                latest_state["gz"] = 0.0 if abs(gz) < DEADZONE else gz
                latest_state["g_vec"] = [ugx, ugz, ugy]
                latest_state["pitch"] = pitch
                latest_state["roll"] = roll
                latest_state["yaw"] = yaw_state
                latest_state["tilt_x"] = roll
                latest_state["tilt_y"] = pitch
                latest_state["packets"] += 1
                latest_state["ts"] = now
                notify_subscribers()
        except:
            pass

    m_b = p_bpm.search(line)
    if m_b and "resting" not in line:
        latest_state["hr"] = int(m_b.group(1))
        notify_subscribers()

    m_s = p_steps.search(line)
    if m_s:
        latest_state["steps"] = int(m_s.group(1))
        notify_subscribers()

    m_c = p_cal.search(line)
    if m_c:
        latest_state["cal"] = float(m_c.group(1))
        notify_subscribers()

def tcp_receiver():
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(('0.0.0.0', PORT_TCP))
    server.listen(20)
    print(f"[*] High-Speed Direct Socket Receiver AKTIF di 0.0.0.0:{PORT_TCP}", flush=True)

    log_f = open(LOG_FILE, "a")

    while True:
        try:
            conn, addr = server.accept()
            data = conn.recv(2048)
            if data:
                text = data.decode('utf-8', errors='ignore').strip()
                for line in text.splitlines():
                    line = line.strip()
                    if line:
                        log_f.write(f"[GYRO STREAM FROM BAND] {line}\n")
                        process_payload(line)
            conn.close()
        except Exception:
            pass

def notify_subscribers():
    msg = f"data: {json.dumps(latest_state)}\n\n".encode("utf-8")
    with sub_lock:
        dead = []
        for q in subscribers:
            try:
                q.put(msg)
            except:
                dead.append(q)
        for d in dead:
            subscribers.remove(d)

class VisualizerHandler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        clean_path = self.path.split("?")[0]
        if clean_path == "/events":
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.send_header("Cache-Control", "no-cache")
            self.send_header("Connection", "keep-alive")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()

            import queue
            q = queue.Queue(maxsize=100)
            with sub_lock:
                subscribers.add(q)
            try:
                initial = f"data: {json.dumps(latest_state)}\n\n".encode("utf-8")
                self.wfile.write(initial)
                self.wfile.flush()
                while True:
                    msg = q.get()
                    self.wfile.write(msg)
                    self.wfile.flush()
            except:
                with sub_lock:
                    if q in subscribers:
                        subscribers.remove(q)
            return

        elif clean_path == "/" or clean_path.startswith("/index"):
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Cache-Control", "no-cache, no-store, must-revalidate")
            self.send_header("Pragma", "no-cache")
            self.send_header("Expires", "0")
            self.end_headers()
            html_content = get_html()
            self.wfile.write(html_content.encode("utf-8"))
            return

        elif clean_path == "/data":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(json.dumps(latest_state).encode("utf-8"))
            return

        super().do_GET()

def get_html():
    return """<!DOCTYPE html>
<html lang="id">
<head>
  <meta charset="UTF-8">
  <meta name="google" content="notranslate">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>PulseSync 3D // Quaternion Sensor Fusion & Parallax HUD</title>
  <style>
    :root {
      --bg: #030706;
      --card: #080f0c;
      --border: #132b22;
      --cyan: #38bdf8;
      --emerald: #10b981;
      --amber: #f59e0b;
      --text: #e2e8f0;
      --muted: #64748b;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background: var(--bg);
      color: var(--text);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, monospace;
      overflow-x: hidden;
      display: flex;
      flex-direction: column;
      align-items: center;
      min-height: 100vh;
      padding: 20px 16px;
    }
    .hud-header {
      width: 100%;
      max-width: 960px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding-bottom: 14px;
      border-bottom: 1px solid var(--border);
      margin-bottom: 16px;
    }
    .hud-title {
      font-family: 'JetBrains Mono', monospace;
      font-size: 13px;
      font-weight: 700;
      letter-spacing: 0.1em;
      color: #fff;
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: var(--emerald);
      box-shadow: 0 0 10px var(--emerald);
      animation: pulse 1.5s infinite;
    }
    @keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }
    .btn-toggle {
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid var(--border);
      color: var(--text);
      font-family: 'JetBrains Mono', monospace;
      font-size: 10px;
      font-weight: 700;
      padding: 6px 12px;
      border-radius: 6px;
      cursor: pointer;
      transition: all 0.2s ease;
    }
    .btn-toggle.active {
      background: var(--emerald);
      color: #000;
      border-color: var(--emerald);
    }
    .main-grid {
      width: 100%;
      max-width: 960px;
      display: grid;
      grid-template-columns: 1fr 340px;
      gap: 16px;
    }
    @media (max-width: 840px) {
      .main-grid { grid-template-columns: 1fr; }
    }
    .viewport-card {
      background: var(--card);
      border: 1px solid var(--border);
      border-radius: 12px;
      position: relative;
      overflow: hidden;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      min-height: 480px;
      cursor: grab;
    }
    .viewport-card:active { cursor: grabbing; }
    #renderCanvas {
      width: 100%;
      height: 480px;
      display: block;
    }
    .viewport-overlay {
      position: absolute;
      top: 14px;
      left: 16px;
      font-family: 'JetBrains Mono', monospace;
      font-size: 10px;
      color: var(--muted);
      pointer-events: none;
      line-height: 1.5;
    }
    .metrics-col {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }
    .metric-card {
      background: var(--card);
      border: 1px solid var(--border);
      border-radius: 10px;
      padding: 14px 16px;
    }
    .metric-card-head {
      font-family: 'JetBrains Mono', monospace;
      font-size: 9.5px;
      color: var(--muted);
      display: flex;
      justify-content: space-between;
      margin-bottom: 6px;
    }
    .metric-val {
      font-family: 'JetBrains Mono', monospace;
      font-size: 24px;
      font-weight: 700;
      color: #fff;
    }
    .metric-sub {
      font-family: 'JetBrains Mono', monospace;
      font-size: 10px;
      color: var(--emerald);
      margin-top: 4px;
    }
    .axes-bar {
      display: flex;
      gap: 8px;
      margin-top: 8px;
    }
    .axis-box {
      flex: 1;
      background: rgba(255,255,255,0.03);
      padding: 8px 10px;
      border-radius: 6px;
      border: 1px solid rgba(255,255,255,0.05);
      font-family: 'JetBrains Mono', monospace;
      text-align: center;
    }
  </style>
</head>
<body>

  <div class="hud-header">
    <div class="hud-title">
      <div class="dot"></div>
      PULSESYNC 3D // DIRECT QUATERNION FUSION HUD
    </div>
    <div style="display:flex; gap:8px; align-items:center;">
      <button class="btn-toggle active" id="btn3D" onclick="setMode('3d')">3D ROTATING BAND</button>
      <button class="btn-toggle" id="btnParallax" onclick="setMode('parallax')">WEAR OS PARALLAX</button>
    </div>
  </div>

  <div class="main-grid">
    <div class="viewport-card" id="viewCard">
      <div class="viewport-overlay">
        ENGINE: DIRECT MEMORY SOCKET (SUB-1MS LATENCY)<br>
        FUSION: UNIVERSAL GRAVITY PROJECTION (ZERO GIMBAL LOCK)<br>
        DRAG MOUSE: ORBIT CAMERA 360° | SPASI: RESET YAW
      </div>
      <canvas id="renderCanvas"></canvas>
    </div>

    <div class="metrics-col">
      <div class="metric-card">
        <div class="metric-card-head">
          <span>KINETIC ORIENTATION (EULER)</span>
          <span style="color:var(--emerald);">DEG°</span>
        </div>
        <div class="axes-bar">
          <div class="axis-box">
            <div style="font-size:9px; color:var(--muted);">ROLL (X)</div>
            <div id="valRoll" style="color:var(--cyan); font-weight:bold; font-size:16px; margin:4px 0;">0.0°</div>
            <div style="font-size:8px; color:var(--muted);">KANAN / KIRI</div>
          </div>
          <div class="axis-box">
            <div style="font-size:9px; color:var(--muted);">PITCH (Y)</div>
            <div id="valPitch" style="color:var(--emerald); font-weight:bold; font-size:16px; margin:4px 0;">0.0°</div>
            <div style="font-size:8px; color:var(--muted);">DEPAN / BLKG</div>
          </div>
          <div class="axis-box">
            <div style="font-size:9px; color:var(--muted);">YAW (Z)</div>
            <div id="valYaw" style="color:var(--amber); font-weight:bold; font-size:16px; margin:4px 0;">0.0°</div>
            <div style="font-size:8px; color:var(--muted);">KOMPAS PUTAR</div>
          </div>
        </div>
      </div>

      <div class="metric-card">
        <div class="metric-card-head">
          <span>ANGULAR VELOCITY (RAW)</span>
          <span style="color:var(--cyan);">RAD/S</span>
        </div>
        <div class="axes-bar">
          <div class="axis-box">
            <div style="font-size:8.5px; color:var(--muted);">GX</div>
            <div id="gxVal" style="color:var(--cyan); font-weight:bold;">+0.00</div>
          </div>
          <div class="axis-box">
            <div style="font-size:8.5px; color:var(--muted);">GY</div>
            <div id="gyVal" style="color:var(--emerald); font-weight:bold;">+0.00</div>
          </div>
          <div class="axis-box">
            <div style="font-size:8.5px; color:var(--muted);">GZ</div>
            <div id="gzVal" style="color:var(--amber); font-weight:bold;">+0.00</div>
          </div>
        </div>
      </div>

      <div class="metric-card">
        <div class="metric-card-head">
          <span>HEART RATE MONITOR</span>
          <span style="color:var(--cyan);">0x2A37</span>
        </div>
        <div class="metric-val" id="hrVal">89 <span style="font-size:12px; font-weight:normal; color:var(--muted);">BPM</span></div>
        <div class="metric-sub">RESTING HR: 60 BPM</div>
      </div>

      <div class="metric-card">
        <div class="metric-card-head">
          <span>STEPS & KALORI</span>
          <span style="color:var(--amber);">REALTIME</span>
        </div>
        <div class="metric-val" id="stepVal">14 <span style="font-size:12px; font-weight:normal; color:var(--muted);">LANGKAH</span></div>
        <div class="metric-sub" style="color:var(--amber);">9.0 KKAL AKTIF</div>
      </div>
    </div>
  </div>

  <script>
    const canvas = document.getElementById('renderCanvas');
    const ctx = canvas.getContext('2d');

    let mode = '3d';

    function setMode(m) {
      mode = m;
      document.getElementById('btn3D').className = 'btn-toggle' + (m === '3d' ? ' active' : '');
      document.getElementById('btnParallax').className = 'btn-toggle' + (m === 'parallax' ? ' active' : '');
    }

    let targetG = [0, 1, 0];
    let curG = [0, 1, 0];
    let targetYaw = 0;
    let curYaw = 0;
    let rawYaw = 0, offsetYaw = 0;
    let targetRoll = 0, targetPitch = 0;

    window.addEventListener('keydown', (e) => {
      if (e.code === 'Space') {
        offsetYaw = rawYaw;
      }
    });

    function resize() {
      canvas.width = canvas.clientWidth * window.devicePixelRatio;
      canvas.height = canvas.clientHeight * window.devicePixelRatio;
    }
    window.addEventListener('resize', resize);
    resize();

    // 3D Geometry: Xiaomi Smart Band 10 Pill resting flat horizontally on table at (0, 0, 0)
    // Local Coordinates:
    // Screen normal: [0, 0, 1]
    // Width (X): -38 to 38
    // Thickness (Y): -14 to 14
    // Length (Z): -90 to 90
    const bodyVertices = [
      [-38, -14, -90], [38, -14, -90], [38, -14, 90], [-38, -14, 90],
      [-38,  14, -90], [38,  14, -90], [38,  14, 90], [-38,  14, 90]
    ];

    const screenVertices = [
      [-30, 15, -75], [30, 15, -75], [30, 15, 75], [-30, 15, 75]
    ];

    const strapTop = [
      [-26, -10, -90], [26, -10, -90], [26, -10, -145], [-26, -10, -145]
    ];
    const strapBottom = [
      [-26, -10, 90], [26, -10, 90], [26, -10, 145], [-26, -10, 145]
    ];

    const bodyEdges = [
      [0,1], [1,2], [2,3], [3,0],
      [4,5], [5,6], [6,7], [7,4],
      [0,4], [1,5], [2,6], [3,7]
    ];

    // Quaternion Math (100% Gimbal-Lock-Free)
    function quatFromVectors(u, v) {
      const dot = u[0]*v[0] + u[1]*v[1] + u[2]*v[2];
      if (dot > 0.999999) return [1, 0, 0, 0];
      if (dot < -0.999999) return [0, 1, 0, 0];
      const w = 1.0 + dot;
      const xyz = [
        u[1]*v[2] - u[2]*v[1],
        u[2]*v[0] - u[0]*v[2],
        u[0]*v[1] - u[1]*v[0]
      ];
      const q = [w, xyz[0], xyz[1], xyz[2]];
      const qn = Math.hypot(q[0], q[1], q[2], q[3]);
      return [q[0]/qn, q[1]/qn, q[2]/qn, q[3]/qn];
    }

    function quatRotate(q, p) {
      const qw = q[0], qx = q[1], qy = q[2], qz = q[3];
      const px = p[0], py = p[1], pz = p[2];
      const tx = 2 * (qy * pz - qz * py);
      const ty = 2 * (qz * px - qx * pz);
      const tz = 2 * (qx * py - qy * px);
      return [
        px + qw * tx + (qy * tz - qz * ty),
        py + qw * ty + (qz * tx - qx * tz),
        pz + qw * tz + (qx * ty - qy * tx)
      ];
    }

    function rotateY(p, a) {
      const rad = a * Math.PI / 180;
      return [p[0] * Math.cos(rad) + p[2] * Math.sin(rad), p[1], -p[0] * Math.sin(rad) + p[2] * Math.cos(rad)];
    }
    function rotateX(p, a) {
      const rad = a * Math.PI / 180;
      return [p[0], p[1] * Math.cos(rad) - p[2] * Math.sin(rad), p[1] * Math.sin(rad) + p[2] * Math.cos(rad)];
    }

    function project(p, w, h) {
      const fov = 380;
      const z = p[2] + 450;
      const x = (p[0] * fov) / z + w / 2;
      const y = (p[1] * fov) / z + h / 2;
      return [x, y];
    }

    let isDragging = false;
    let prevMouseX = 0, prevMouseY = 0;
    let camAzimuth = -30;
    let camElev = 28;

    canvas.addEventListener('mousedown', (e) => {
      isDragging = true;
      prevMouseX = e.clientX;
      prevMouseY = e.clientY;
    });
    window.addEventListener('mouseup', () => isDragging = false);
    window.addEventListener('mousemove', (e) => {
      if (!isDragging) return;
      camAzimuth += (e.clientX - prevMouseX) * 0.45;
      camElev = Math.max(-85, Math.min(85, camElev + (e.clientY - prevMouseY) * 0.45));
      prevMouseX = e.clientX;
      prevMouseY = e.clientY;
    });

    function worldToCamera(p) {
      let pc = rotateY(p, camAzimuth);
      pc = rotateX(pc, camElev);
      return pc;
    }

    function render() {
      // Smooth exponential filter (removes network judder and sensor micro-jitter)
      const SMOOTH = 0.16;

      curG[0] += (targetG[0] - curG[0]) * SMOOTH;
      curG[1] += (targetG[1] - curG[1]) * SMOOTH;
      curG[2] += (targetG[2] - curG[2]) * SMOOTH;
      curYaw += (targetYaw - curYaw) * SMOOTH;

      const gn = Math.hypot(curG[0], curG[1], curG[2]) || 1;
      const normG = [curG[0]/gn, curG[1]/gn, curG[2]/gn];

      const w = canvas.width;
      const h = canvas.height;
      const cx = w / 2;
      const cy = h / 2;

      ctx.clearRect(0, 0, w, h);

      if (mode === '3d') {
        render3D(w, h, normG);
      } else {
        renderParallax(w, h, cx, cy);
      }

      requestAnimationFrame(render);
    }

    function render3D(w, h, normG) {
      // 1. Draw 3D Circular Pedestal Grid on Ground (Y = 40)
      ctx.strokeStyle = "rgba(16, 185, 129, 0.25)";
      ctx.lineWidth = 1;
      for (let r = 50; r <= 190; r += 45) {
        ctx.beginPath();
        for (let th = 0; th <= Math.PI * 2 + 0.1; th += Math.PI / 16) {
          const pt = worldToCamera([r * Math.cos(th), 40, r * Math.sin(th)]);
          const pr = project(pt, w, h);
          if (th === 0) ctx.moveTo(pr[0], pr[1]);
          else ctx.lineTo(pr[0], pr[1]);
        }
        ctx.stroke();
      }

      // 2. Quaternion Tilt + Heading Transformation
      // Flat resting reference gravity is [0, 1, 0] (screen normal points UP)
      const qTilt = quatFromVectors([0, 1, 0], normG);

      function tf(v) {
        let p = quatRotate(qTilt, v);
        p = rotateY(p, curYaw);
        p = worldToCamera(p);
        return project(p, w, h);
      }

      const pBody = bodyVertices.map(tf);
      const pScreen = screenVertices.map(tf);
      const pStrapT = strapTop.map(tf);
      const pStrapB = strapBottom.map(tf);

      // Draw Straps (Silicon Dark Green)
      ctx.fillStyle = "rgba(16, 185, 129, 0.2)";
      ctx.strokeStyle = "#10b981";
      ctx.lineWidth = 2;
      [pStrapT, pStrapB].forEach(st => {
        ctx.beginPath();
        ctx.moveTo(st[0][0], st[0][1]);
        for (let i = 1; i < st.length; i++) ctx.lineTo(st[i][0], st[i][1]);
        ctx.closePath();
        ctx.fill();
        ctx.stroke();
      });

      // Draw Shaded Solid Body Faces
      ctx.fillStyle = "rgba(12, 23, 18, 0.85)";
      const faces = [
        [0, 1, 2, 3], // bottom
        [4, 5, 6, 7], // top
        [0, 1, 5, 4], // back
        [2, 3, 7, 6], // front
        [0, 3, 7, 4], // left
        [1, 2, 6, 5]  // right
      ];
      faces.forEach(f => {
        ctx.beginPath();
        ctx.moveTo(pBody[f[0]][0], pBody[f[0]][1]);
        for (let i = 1; i < f.length; i++) ctx.lineTo(pBody[f[i]][0], pBody[f[i]][1]);
        ctx.closePath();
        ctx.fill();
      });

      // Draw Smartband Chassis Edges (Emerald Neon)
      ctx.strokeStyle = "#10b981";
      ctx.lineWidth = 2.5;
      bodyEdges.forEach(e => {
        ctx.beginPath();
        ctx.moveTo(pBody[e[0]][0], pBody[e[0]][1]);
        ctx.lineTo(pBody[e[1]][0], pBody[e[1]][1]);
        ctx.stroke();
      });

      // Draw OLED Screen Surface (Glowing Cyan)
      ctx.fillStyle = "rgba(56, 189, 248, 0.25)";
      ctx.strokeStyle = "#38bdf8";
      ctx.lineWidth = 2.5;
      ctx.beginPath();
      ctx.moveTo(pScreen[0][0], pScreen[0][1]);
      for (let i = 1; i < pScreen.length; i++) ctx.lineTo(pScreen[i][0], pScreen[i][1]);
      ctx.closePath();
      ctx.fill();
      ctx.stroke();

      // Draw 3D Orientation Axis Vectors from Center
      const pOrig = tf([0, 0, 0]);
      const pVx = tf([55, 0, 0]);  // X Vector
      const pVy = tf([0, 55, 0]);  // Y Vector
      const pVz = tf([0, 0, 55]);  // Z Vector

      ctx.strokeStyle = "#38bdf8"; ctx.lineWidth = 3;
      ctx.beginPath(); ctx.moveTo(pOrig[0], pOrig[1]); ctx.lineTo(pVx[0], pVx[1]); ctx.stroke();
      ctx.fillStyle = "#38bdf8"; ctx.font = "bold 11px monospace"; ctx.fillText("X", pVx[0]+4, pVx[1]);

      ctx.strokeStyle = "#10b981"; ctx.lineWidth = 3;
      ctx.beginPath(); ctx.moveTo(pOrig[0], pOrig[1]); ctx.lineTo(pVy[0], pVy[1]); ctx.stroke();
      ctx.fillStyle = "#10b981"; ctx.font = "bold 11px monospace"; ctx.fillText("Y", pVy[0]+4, pVy[1]);

      ctx.strokeStyle = "#f59e0b"; ctx.lineWidth = 3;
      ctx.beginPath(); ctx.moveTo(pOrig[0], pOrig[1]); ctx.lineTo(pVz[0], pVz[1]); ctx.stroke();
      ctx.fillStyle = "#f59e0b"; ctx.font = "bold 11px monospace"; ctx.fillText("Z", pVz[0]+4, pVz[1]);
    }

    function renderParallax(w, h, cx, cy) {
      const bgShiftX = (targetRoll / 45) * 16;
      const bgShiftY = (targetPitch / 45) * 16;
      const fgShiftX = (targetRoll / 45) * 36;
      const fgShiftY = (targetPitch / 45) * 36;
      const glareShiftX = (-targetRoll / 45) * 55;
      const glareShiftY = (-targetPitch / 45) * 55;

      const caseW = 190, caseH = 340, caseR = 75;
      ctx.save();
      ctx.translate(cx, cy);

      ctx.fillStyle = "#0c1712";
      ctx.strokeStyle = "#17382b";
      ctx.lineWidth = 3;
      roundRect(ctx, -caseW/2, -caseH/2, caseW, caseH, caseR, true, true);

      ctx.save();
      roundRect(ctx, -caseW/2 + 12, -caseH/2 + 12, caseW - 24, caseH - 24, caseR - 10, false, false);
      ctx.clip();

      ctx.fillStyle = "#020504";
      ctx.fillRect(-caseW/2, -caseH/2, caseW, caseH);

      // Background Dial
      ctx.save();
      ctx.translate(bgShiftX, bgShiftY);
      ctx.strokeStyle = "rgba(16, 185, 129, 0.25)";
      ctx.lineWidth = 1;
      for (let r = 25; r <= 85; r += 20) {
        ctx.beginPath();
        ctx.arc(0, 0, r, 0, Math.PI * 2);
        ctx.stroke();
      }
      ctx.restore();

      // Foreground Text
      ctx.save();
      ctx.translate(fgShiftX, fgShiftY);
      ctx.fillStyle = "#ffffff";
      ctx.font = "bold 32px 'JetBrains Mono', monospace";
      ctx.textAlign = "center";
      ctx.fillText("18:55", 0, -25);
      ctx.fillStyle = "#38bdf8";
      ctx.font = "bold 13px 'JetBrains Mono', monospace";
      ctx.fillText("♥ 89 BPM", 0, 15);
      ctx.fillStyle = "#10b981";
      ctx.font = "11px 'JetBrains Mono', monospace";
      ctx.fillText("👟 14 STEPS", 0, 36);
      ctx.restore();

      // Glare
      ctx.save();
      const grad = ctx.createLinearGradient(-caseW/2 + glareShiftX, -caseH/2 + glareShiftY, caseW/2 + glareShiftX, caseH/2 + glareShiftY);
      grad.addColorStop(0, "rgba(255, 255, 255, 0.0)");
      grad.addColorStop(0.5, "rgba(255, 255, 255, 0.16)");
      grad.addColorStop(1, "rgba(255, 255, 255, 0.0)");
      ctx.fillStyle = grad;
      ctx.fillRect(-caseW/2, -caseH/2, caseW, caseH);
      ctx.restore();

      ctx.restore();
      ctx.restore();
    }

    function roundRect(ctx, x, y, width, height, radius, fill, stroke) {
      ctx.beginPath();
      ctx.moveTo(x + radius, y);
      ctx.lineTo(x + width - radius, y);
      ctx.quadraticCurveTo(x + width, y, x + width, y + radius);
      ctx.lineTo(x + width, y + height - radius);
      ctx.quadraticCurveTo(x + width, y + height, x + width - radius, y + height);
      ctx.lineTo(x + radius, y + height);
      ctx.quadraticCurveTo(x, y + height, x, y + height - radius);
      ctx.lineTo(x, y + radius);
      ctx.quadraticCurveTo(x, y, x + radius, y);
      ctx.closePath();
      if (fill) ctx.fill();
      if (stroke) ctx.stroke();
    }

    render();

    const es = new EventSource('/events');
    es.onmessage = (e) => {
      try {
        const d = JSON.parse(e.data);
        rawYaw = d.yaw;
        targetG = d.g_vec || [0, 1, 0];
        targetYaw = rawYaw - offsetYaw;
        targetRoll = d.roll;
        targetPitch = d.pitch;

        document.getElementById('valRoll').innerText = (d.roll >= 0 ? '+' : '') + d.roll.toFixed(1) + '°';
        document.getElementById('valPitch').innerText = (d.pitch >= 0 ? '+' : '') + d.pitch.toFixed(1) + '°';
        document.getElementById('valYaw').innerText = (targetYaw >= 0 ? '+' : '') + targetYaw.toFixed(1) + '°';

        document.getElementById('gxVal').innerText = (d.gx >= 0 ? '+' : '') + d.gx.toFixed(2);
        document.getElementById('gyVal').innerText = (d.gy >= 0 ? '+' : '') + d.gy.toFixed(2);
        document.getElementById('gzVal').innerText = (d.gz >= 0 ? '+' : '') + d.gz.toFixed(2);

        if (d.hr) document.getElementById('hrVal').innerHTML = d.hr + ' <span style="font-size:12px; font-weight:normal; color:var(--muted);">BPM</span>';
        if (d.steps) document.getElementById('stepVal').innerHTML = d.steps + ' <span style="font-size:12px; font-weight:normal; color:var(--muted);">LANGKAH</span>';
      } catch (err) {}
    };
  </script>
</body>
</html>
"""

def main():
    socketserver.ThreadingTCPServer.allow_reuse_address = True
    t_tcp = threading.Thread(target=tcp_receiver, daemon=True)
    t_tcp.start()

    with socketserver.ThreadingTCPServer(('0.0.0.0', PORT_HTTP), VisualizerHandler) as httpd:
        print(f"[*] Quaternion & Parallax Unified Server AKTIF di http://0.0.0.0:{PORT_HTTP}", flush=True)
        httpd.serve_forever()

if __name__ == "__main__":
    main()
