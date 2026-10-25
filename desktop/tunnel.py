import os
import re
import shutil
import subprocess
import sys
import threading
import time

class MultiTunnelManager:
    """
    Robust multi-provider tunnel manager.
    Tries Cloudflare Quick Tunnel first. If rate-limited (status 429) or unavailable,
    automatically falls back to localhost.run (via built-in SSH reverse port forwarding).
    If both fail, gracefully notifies caller with empty string so the UI operates in
    Local Wi-Fi Mode without hanging.
    """
    def __init__(self, target_port: int = 27615):
        self.target_port = target_port
        self.process: subprocess.Popen | None = None
        self.public_url: str = ""
        self.is_running = False
        self.provider: str = ""
        self._thread: threading.Thread | None = None
        self._on_url_ready_cb = None

    def get_cloudflared_path(self) -> str | None:
        exe_name = "cloudflared.exe" if sys.platform == "win32" else "cloudflared"
        local_bin = os.path.abspath(os.path.join(os.path.dirname(__file__), "bin", exe_name))
        if os.path.exists(local_bin):
            return local_bin
        system_bin = shutil.which("cloudflared") or shutil.which("cloudflared.exe")
        if system_bin:
            return system_bin
        return None

    def _cleanup_stale_processes(self):
        try:
            if sys.platform == "win32":
                subprocess.run(["taskkill", "/F", "/IM", "cloudflared.exe", "/T"], capture_output=True, creationflags=0x08000000)
            else:
                subprocess.run(["pkill", "-9", "-f", "cloudflared"], capture_output=True)
        except Exception:
            pass
        if self.process:
            try:
                self.process.terminate()
                self.process.wait(timeout=1.0)
            except Exception:
                try:
                    self.process.kill()
                except Exception:
                    pass
            self.process = None

    def _try_cloudflared(self) -> str:
        bin_path = self.get_cloudflared_path()
        if not bin_path:
            return ""

        cmd = [
            bin_path,
            "tunnel",
            "--url", f"http://127.0.0.1:{self.target_port}",
            "--no-autoupdate"
        ]
        kwargs = {}
        if sys.platform == "win32":
            kwargs["creationflags"] = 0x08000000
        try:
            proc = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                bufsize=1,
                **kwargs
            )
            self.process = proc
            url_pattern = re.compile(r"https://[a-zA-Z0-9-]+\.trycloudflare\.com")
            
            start_time = time.time()
            # Give cloudflared up to 4.5 seconds to establish tunnel
            while time.time() - start_time < 4.5 and self.is_running:
                if proc.poll() is not None:
                    # Process exited early (e.g. 429 rate limit)
                    break
                line = proc.stdout.readline() if proc.stdout else ""
                if not line:
                    time.sleep(0.1)
                    continue
                match = url_pattern.search(line)
                if match:
                    self.provider = "cloudflare"
                    return match.group(0)

            # Failed or timed out
            try:
                proc.terminate()
                proc.wait(timeout=1.0)
            except Exception:
                try:
                    proc.kill()
                except Exception:
                    pass
            self.process = None
            return ""
        except Exception as e:
            print(f"[Tunnel] cloudflared exception: {e}")
            return ""

    def _try_ssh_tunnel(self) -> str:
        ssh_bin = shutil.which("ssh") or shutil.which("ssh.exe")
        if not ssh_bin:
            return ""

        cmd = [
            ssh_bin,
            "-o", "StrictHostKeyChecking=no",
            "-o", "ServerAliveInterval=15",
            "-o", "ServerAliveCountMax=3",
            "-o", "ConnectTimeout=6",
            "-R", f"80:127.0.0.1:{self.target_port}",
            "nokey@localhost.run"
        ]
        kwargs = {}
        if sys.platform == "win32":
            kwargs["creationflags"] = 0x08000000
        try:
            proc = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                bufsize=1,
                **kwargs
            )
            self.process = proc
            url_pattern = re.compile(r"https://[a-zA-Z0-9-]+\.lhr\.life")
            
            start_time = time.time()
            # Give localhost.run up to 6 seconds to provide URL
            while time.time() - start_time < 6.0 and self.is_running:
                if proc.poll() is not None:
                    break
                line = proc.stdout.readline() if proc.stdout else ""
                if not line:
                    time.sleep(0.1)
                    continue
                match = url_pattern.search(line)
                if match:
                    self.provider = "localhost.run"
                    return match.group(0)

            try:
                proc.terminate()
                proc.wait(timeout=1.0)
            except Exception:
                try:
                    proc.kill()
                except Exception:
                    pass
            self.process = None
            return ""
        except Exception as e:
            print(f"[Tunnel] ssh tunnel exception: {e}")
            return ""

    def start(self, on_url_ready=None):
        if on_url_ready:
            self._on_url_ready_cb = on_url_ready

        self.stop()
        self.is_running = True

        def run_tunnel():
            print("[Tunnel] Attempting Cloudflare Tunnel...")
            url = self._try_cloudflared()

            if not url and self.is_running:
                print("[Tunnel] Cloudflare tunnel unavailable or rate-limited. Falling back to localhost.run SSH tunnel...")
                url = self._try_ssh_tunnel()

            if url and self.is_running:
                self.public_url = url
                print(f"[Tunnel] Connected successfully via {self.provider}: {url}")
                if self._on_url_ready_cb:
                    self._on_url_ready_cb(url)

                # Keep connection alive while is_running is True
                while self.is_running and self.process:
                    if self.process.poll() is not None:
                        print("[Tunnel] Active tunnel process closed.")
                        break
                    time.sleep(1.0)
            else:
                self.public_url = ""
                print("[Tunnel] Tunnels unavailable. Operating in Local Wi-Fi Mode.")
                if self._on_url_ready_cb:
                    self._on_url_ready_cb("")

        self._thread = threading.Thread(target=run_tunnel, daemon=True)
        self._thread.start()
        return self.public_url

    def stop(self):
        self.is_running = False
        self.public_url = ""
        self.provider = ""
        self._cleanup_stale_processes()

    def is_alive(self) -> bool:
        return self.is_running and self.process is not None and self.process.poll() is None

CloudflareTunnelManager = MultiTunnelManager
tunnel_manager = MultiTunnelManager(27615)
