#!/usr/bin/env python3
"""End-to-end check on an Android emulator: installs the APK, connects for real through the
Aether core, sends traffic through the VPN and through the proxy, and takes screenshots of
every screen in both languages and both themes. Writes results to $SHOTS/results.md.

Usage: tools/e2e.py <apk>
"""
import os
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(__file__))
import ui  # noqa: E402

PKG = ui.PKG
ACT = f"{PKG}/.ui.MainActivity"
results = []
failed_required = False


def record(name, ok, detail="", required=True):
    global failed_required
    results.append((name, ok, detail, required))
    print(f"[{'PASS' if ok else 'FAIL'}] {name} {detail}", flush=True)
    if required and not ok:
        failed_required = True


def trace_through_vpn():
    """Sends HTTP from the adb shell, which is not excluded from the VPN, by name (so DNS is tested too)."""
    cmd = ("printf 'GET /cdn-cgi/trace HTTP/1.0\\r\\nHost: www.cloudflare.com\\r\\n\\r\\n' "
           "| nc -w 20 www.cloudflare.com 80")
    return ui.shell(cmd, check=False)


def trace_through_proxy(port):
    subprocess.run(["adb", "forward", f"tcp:{port + 10000}", f"tcp:{port}"], check=True)
    r = subprocess.run(
        ["curl", "-sS", "--max-time", "30", "--socks5-hostname", f"127.0.0.1:{port + 10000}",
         "https://www.cloudflare.com/cdn-cgi/trace"],
        capture_output=True, text=True,
    )
    return r.stdout + r.stderr


def field(text, key):
    for line in text.splitlines():
        if line.startswith(key + "="):
            return line.split("=", 1)[1].strip()
    return ""


def connect_and_wait(connected_text, timeout, shot_prefix):
    ui.shell(f"am start -n {ACT} --ez connect true")
    time.sleep(4)
    ui.shot(f"{shot_prefix}-connecting")
    t0 = time.time()
    found = ui.wait_text([connected_text], timeout)
    return found is not None, int(time.time() - t0)


def disconnect(idle_text, disconnect_label):
    ui.tap(disconnect_label, exact=True)
    return ui.wait_text([idle_text], 30) is not None


def main(apk):
    ui.adb("install", "-r", "-g", apk)
    ui.shell(f"appops set {PKG} ACTIVATE_VPN allow")
    ui.shell("cmd uimode night no", check=False)
    ui.shell("settings put global window_animation_scale 0", check=False)
    ui.shell("settings put global transition_animation_scale 0", check=False)
    ui.adb("logcat", "-c", check=False)

    direct = trace_through_vpn()
    direct_ip = field(direct, "ip")
    record("network reachable without the app", bool(direct_ip), f"ip={direct_ip} warp={field(direct, 'warp')}")

    ui.shell(f"am start -n {ACT}")
    record("app opens", ui.wait_text(["وصل نیست"], 30) is not None)
    ui.shot("01-home-idle-fa")

    # ---- MASQUE through the VPN (Persian, light) ----
    ok, secs = connect_and_wait("وصل شد", 300, "02-masque")
    record("MASQUE connects (VPN mode)", ok, f"in {secs}s")
    if ok:
        time.sleep(12)  # the exit check runs right after connecting
        ui.shot("03-home-connected-fa")
        out = trace_through_vpn()
        ip, warp = field(out, "ip"), field(out, "warp")
        record("traffic and DNS go through the VPN", warp in ("on", "plus") and ip != direct_ip,
               f"ip={ip} warp={warp} loc={field(out, 'loc')}")
        out = trace_through_proxy(1819)
        record("SOCKS5 proxy works while in VPN mode", field(out, "warp") in ("on", "plus"), f"ip={field(out, 'ip')}")

    ui.tap("گزارش", exact=True)
    ui.shot("04-log-fa")
    ui.tap("تنظیمات", exact=True)
    ui.shot("05-settings-fa")
    ui.page_down()
    ui.shot("06-settings-fa-2")

    # ---- English and dark theme ----
    ui.tap("زبان", exact=True, scroll=True)
    ui.tap("English", exact=True)
    time.sleep(2)
    ui.tap("Theme", exact=True, scroll=True)
    ui.tap("Dark", exact=True)
    ui.to_top()
    ui.shot("07-settings-en-dark")
    ui.tap("Home", exact=True)
    time.sleep(2)
    ui.shot("08-home-en-dark")

    if ok:
        record("disconnects", disconnect("Not connected", "Disconnect"))
        time.sleep(2)
        ui.shot("09-home-en-dark-idle")

    # ---- WireGuard in proxy mode ----
    ui.tap("Proxy", exact=True)
    ui.tap("WireGuard", exact=True)
    ok, secs = connect_and_wait("Connected", 300, "10-wireguard")
    record("WireGuard connects (proxy mode)", ok, f"in {secs}s", required=False)
    if ok:
        time.sleep(8)
        ui.shot("11-home-wireguard-proxy")
        out = trace_through_proxy(1819)
        record("WireGuard proxy carries traffic", field(out, "warp") in ("on", "plus"), f"ip={field(out, 'ip')}", required=False)
        disconnect("Not connected", "Disconnect")

    # ---- Psiphon in VPN mode ----
    ui.tap("VPN", exact=True)
    ui.tap("Psiphon", exact=True)
    ok, secs = connect_and_wait("Connected", 240, "12-psiphon")
    record("Psiphon connects (VPN mode)", ok, f"in {secs}s", required=False)
    if ok:
        time.sleep(8)
        ui.shot("13-home-psiphon")
        out = trace_through_vpn()
        record("Psiphon carries VPN traffic", bool(field(out, "ip")) and field(out, "ip") != direct_ip,
               f"ip={field(out, 'ip')} loc={field(out, 'loc')}", required=False)
        disconnect("Not connected", "Disconnect")
    else:
        ui.tap("Log", exact=True)
        ui.shot("12-psiphon-log")
        ui.tap("Home", exact=True)

    # Back to the defaults a new user sees, and a light-theme English shot for the README.
    ui.tap("MASQUE", exact=True)
    ui.tap("Settings", exact=True)
    ui.tap("Theme", exact=True, scroll=True)
    ui.tap("Light", exact=True)
    ui.tap("Home", exact=True)
    ui.shot("14-home-en-light-idle")


if __name__ == "__main__":
    try:
        main(sys.argv[1])
    except Exception as e:  # keep the screenshots and logs of a run that broke half way
        record("script ran to the end", False, repr(e))
        try:
            ui.shot("zz-failure")
        except Exception:
            pass
    finally:
        os.makedirs(ui.SHOTS, exist_ok=True)
        with open(os.path.join(ui.SHOTS, "logcat.txt"), "w") as f:
            subprocess.run(["adb", "logcat", "-d", "-v", "time", "NimbusCore:V", "AndroidRuntime:E", "*:S"],
                           stdout=f, timeout=60)
        with open(os.path.join(ui.SHOTS, "results.md"), "w") as f:
            f.write("| check | result | detail |\n|---|---|---|\n")
            for name, ok, detail, req in results:
                f.write(f"| {name}{'' if req else ' (optional)'} | {'pass' if ok else 'FAIL'} | {detail} |\n")
        print(open(os.path.join(ui.SHOTS, "results.md")).read())
    sys.exit(1 if failed_required else 0)
