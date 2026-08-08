"""Locate a plains village, force-gen via /embedize loadchunks, count structure blocks.

RCON: 25575 / embedize

IMPORTANT: ``fill … replace same`` returns 0 / \"No blocks were filled\" on this Paper
build even when blocks exist — do NOT use it. Prefer ``/embedize scan`` (Bukkit) or
``clone … filtered``.
"""
from __future__ import annotations

import argparse
import re
import socket
import struct as st
import time


def rcon_session(password: str = "embedize", host: str = "127.0.0.1", port: int = 25575):
    s = socket.socket()
    s.settimeout(180)
    s.connect((host, port))

    def send(req_id: int, req_type: int, payload: str) -> None:
        body = payload.encode("utf-8")
        data = st.pack("<ii", req_id, req_type) + body + b"\x00\x00"
        s.sendall(st.pack("<i", len(data)) + data)

    def recv() -> str | None:
        raw = s.recv(4)
        if not raw:
            return None
        (n,) = st.unpack("<i", raw)
        data = b""
        while len(data) < n:
            chunk = s.recv(n - len(data))
            if not chunk:
                break
            data += chunk
        return data[8:-2].decode("utf-8", "replace")

    send(1, 3, password)
    if recv() is None:
        raise SystemExit("RCON auth failed")

    def cmd(command: str, timeout: float = 90.0) -> str:
        send(2, 2, command)
        s.settimeout(timeout)
        try:
            return recv() or ""
        except Exception as ex:
            return f"ERR:{ex}"

    return s, cmd


def wait_dimension(cmd, dim: str, structure: str, stable: int = 3) -> str:
    ok = 0
    last = ""
    for i in range(90):
        time.sleep(2)
        out = cmd(f"execute in {dim} run locate structure {structure}", timeout=45)
        if "Unknown dimension" in out or out.startswith("ERR:"):
            ok = 0
            if i % 5 == 0:
                print(f"wait dim i={i}: {out.strip()[:100]}")
            continue
        if re.search(r"\[(-?\d+),\s*~,\s*(-?\d+)\]", out):
            ok += 1
            last = out
            print(f"locate ok {ok}: {out.strip()[:120]}")
            if ok >= stable:
                return last
    raise SystemExit("dimension/locate never stable")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--world", default="resource")
    ap.add_argument("--dim", default="minecraft:resource")
    ap.add_argument("--structure", default="minecraft:village_plains")
    ap.add_argument("--radius", type=int, default=4)
    ap.add_argument("--scan-radius", type=int, default=40)
    ap.add_argument("--from-pos", default="", help="optional 'x,z' seed for locate via positioned")
    args = ap.parse_args()

    sock, cmd = rcon_session()
    try:
        print("PING:", cmd("list").strip()[:80])
        if args.from_pos:
            x0, z0 = (int(p) for p in args.from_pos.split(","))
            out = cmd(
                f"execute in {args.dim} positioned {x0} 80 {z0} run locate structure {args.structure}",
                timeout=90,
            )
        else:
            out = wait_dimension(cmd, args.dim, args.structure)
        print("LOCATE:", out.strip())
        m = re.search(r"\[(-?\d+),\s*~,\s*(-?\d+)\]", out)
        if not m:
            raise SystemExit("no coords from locate")
        x, z = int(m.group(1)), int(m.group(2))
        print(f"coords {x} {z} chunk {x // 16} {z // 16}")

        print("DIAG:", cmd(f"embedize diagnose {args.world} {x} {z}", timeout=60).strip())
        load = cmd(f"embedize loadchunks {args.world} {x} {z} {args.radius}", timeout=180)
        print("LOADCHUNKS:", load.strip().replace("\n", " | "))
        print("DIAG2:", cmd(f"embedize diagnose {args.world} {x} {z}", timeout=60).strip())

        # Surface band + buried band (wrong heightmap buries jigsaw near Y=0)
        for ymin, ymax in ((40, 120), (-20, 40)):
            scan = cmd(
                f"embedize scan {args.world} {x} {z} {args.scan_radius} {ymin} {ymax}",
                timeout=180,
            )
            print(f"SCAN y={ymin}..{ymax}:", scan.strip())

        m_hits = re.search(r"HITS=(\d+)", scan)
        hits = int(m_hits.group(1)) if m_hits else 0
        # Also parse earlier surface scan from printed lines is awkward; re-scan surface for exit code
        surface = cmd(
            f"embedize scan {args.world} {x} {z} {args.scan_radius} 40 120",
            timeout=180,
        )
        print("SCAN_SURFACE:", surface.strip())
        m2 = re.search(r"HITS=(\d+)", surface)
        surface_hits = int(m2.group(1)) if m2 else 0
        buried = cmd(
            f"embedize scan {args.world} {x} {z} {args.scan_radius} -20 40",
            timeout=180,
        )
        print("SCAN_BURIED:", buried.strip())
        mb = re.search(r"HITS=(\d+)", buried)
        buried_hits = int(mb.group(1)) if mb else 0

        total = surface_hits + buried_hits
        print(f"TOTAL_HITS surface={surface_hits} buried={buried_hits} sum={total}")
        # Trees alone (oak_log) are not a village — require characteristic materials.
        village_ok = bool(
            re.search(r"oak_planks=\d*[1-9]", surface, re.I)
            or re.search(r"dirt_path=\d*[1-9]", surface, re.I)
            or re.search(r"hay_block=\d*[1-9]", surface, re.I)
            or re.search(r"white_bed=\d*[1-9]", surface, re.I)
            or re.search(r"bell=\d*[1-9]", surface, re.I)
            or re.search(r"cobblestone=\d*[1-9]", surface, re.I)
        )
        if surface_hits <= 0 or not village_ok:
            raise SystemExit(
                "SURFACE village HITS failed — need oak_planks/dirt_path/etc. "
                f"(surface={surface_hits} buried={buried_hits}; "
                "buried>0 with surface=0 means heightmap/jigsaw still wrong)"
            )
        print("PASS village materials visible at surface")
    finally:
        sock.close()


if __name__ == "__main__":
    main()
