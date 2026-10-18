#!/usr/bin/env python3
"""PulseSync relay.

UDP :8080 (binary frames from SensorBridge) -> WebSocket :8089/ws (frames forwarded untouched)
and serves viewer.html at :8089/. No parsing on the hot path; fusion runs in the browser.

Deps: python-aiohttp  (pacman -S python-aiohttp  |  pip install aiohttp)
"""
import asyncio
import logging
import socket
import struct
from pathlib import Path

from aiohttp import web

UDP_PORT = 8080
HTTP_PORT = 8089
MAGIC, VERSION = 0x5350, 1
HEADER = struct.Struct("<HBBIQ")  # magic, version, type, seq, tUs  (16 bytes)
VIEWER = Path(__file__).with_name("viewer.html")

log = logging.getLogger("pulsesync")


class Relay(asyncio.DatagramProtocol):
    def __init__(self, clients: set):
        self.clients = clients

    def datagram_received(self, data: bytes, addr) -> None:
        if len(data) < HEADER.size:
            return
        magic, version, *_ = HEADER.unpack_from(data)
        if magic != MAGIC or version != VERSION:
            return
        for q in self.clients:
            try:
                q.put_nowait(data)
            except asyncio.QueueFull:  # slow client: drop oldest, never build latency
                try:
                    q.get_nowait()
                    q.put_nowait(data)
                except (asyncio.QueueEmpty, asyncio.QueueFull):
                    pass


async def ws_handler(request: web.Request) -> web.WebSocketResponse:
    ws = web.WebSocketResponse(heartbeat=15, compress=False)
    await ws.prepare(request)
    q: asyncio.Queue = asyncio.Queue(maxsize=256)
    clients: set = request.app["clients"]
    clients.add(q)

    async def pump() -> None:
        try:
            while True:
                await ws.send_bytes(await q.get())
        except (ConnectionResetError, asyncio.CancelledError):
            pass

    sender = asyncio.create_task(pump())
    log.info("viewer connected: %s", request.remote)
    try:
        async for _ in ws:  # returns when the client closes
            pass
    finally:
        sender.cancel()
        clients.discard(q)
        log.info("viewer disconnected: %s", request.remote)
    return ws


async def index_handler(_: web.Request) -> web.StreamResponse:
    return web.FileResponse(VIEWER, headers={"Cache-Control": "no-store"})


async def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(message)s")
    if not VIEWER.is_file():
        raise SystemExit(f"missing {VIEWER}")

    clients: set = set()
    loop = asyncio.get_running_loop()
    transport, _ = await loop.create_datagram_endpoint(
        lambda: Relay(clients), local_addr=("0.0.0.0", UDP_PORT)
    )
    sock = transport.get_extra_info("socket")
    if sock is not None:
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, 1 << 20)

    app = web.Application()
    app["clients"] = clients
    app.add_routes([web.get("/", index_handler), web.get("/ws", ws_handler)])
    runner = web.AppRunner(app, access_log=None)
    await runner.setup()
    await web.TCPSite(runner, "0.0.0.0", HTTP_PORT).start()
    log.info("UDP :%d -> http://localhost:%d", UDP_PORT, HTTP_PORT)

    try:
        await asyncio.Event().wait()
    finally:
        transport.close()
        await runner.cleanup()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
