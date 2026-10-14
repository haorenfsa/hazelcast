#!/usr/bin/env python3
"""Usage: verify-rolling.py JAVA CLASSES ORIGINAL_JAR PATCHED_CLASSPATH OUTPUT_DIR"""
import pathlib
import selectors
import subprocess
import sys
import time
import uuid

java, classes, original, patched, out = sys.argv[1:]
out = pathlib.Path(out)
out.mkdir(parents=True, exist_ok=True)
cluster = "backport-" + uuid.uuid4().hex
nodes = []

def response(p):
    selector = selectors.DefaultSelector()
    selector.register(p.stdout, selectors.EVENT_READ)
    deadline = time.monotonic() + 90
    try:
        while time.monotonic() < deadline:
            if not selector.select(max(0, deadline - time.monotonic())):
                break
            line = p.stdout.readline()
            if not line:
                raise RuntimeError("member exited: " + str(p.poll()))
            if line.startswith("RESULT "):
                return line.strip()[7:]
        raise TimeoutError("member response")
    finally:
        selector.close()

def start(port, cp, label):
    log = open(out / (label + ".log"), "w")
    p = subprocess.Popen([java, "-Xmx256m", "-XX:ActiveProcessorCount=2", "-cp", classes + ":" + cp,
                          "forkverification.RollingMember", str(port), cluster],
                         stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=log, text=True, bufsize=1)
    nodes.append((p, log))
    assert response(p) == "ready"
    return p

def call(p, cmd):
    p.stdin.write(cmd + "\n")
    p.stdin.flush()
    return response(p)

def check_members(members):
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        if all(call(p, "size") == str(len(members)) for p in members):
            return
        time.sleep(0.3)
    raise AssertionError("cluster did not converge")

def verify(members):
    check_members(members)
    for p in members:
        assert call(p, "read") == "owner"
        assert call(p, "claim") == "owner", "exclusive IMap claim was lost"
    before = [int(call(p, "received")) for p in members]
    assert call(members[0], "publish") == "sent"
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        if all(int(call(p, "received")) > count for p, count in zip(members, before)):
            return
        time.sleep(0.2)
    raise AssertionError("topic delivery missing")

try:
    a = start(15971, original, "old-a")
    b = start(15972, patched, "patched-b")
    c = start(15973, original, "old-c")
    check_members([a, b, c])
    assert call(a, "claim") == "null"
    verify([a, b, c])
    print("PASS mixed original/patched 5.5: membership, IMap claim, read, topic", flush=True)
    assert call(a, "quit") == "stopped"
    a.wait(timeout=30)
    check_members([b, c])
    a = start(15971, patched, "patched-a")
    verify([a, b, c])
    print("PASS first rolling replacement preserves state", flush=True)
    assert call(c, "quit") == "stopped"
    c.wait(timeout=30)
    check_members([a, b])
    c = start(15973, patched, "patched-c")
    verify([a, b, c])
    print("PASS final rolling replacement preserves state and topic delivery", flush=True)
finally:
    for p, log in nodes:
        if p.poll() is None:
            try:
                call(p, "quit")
                p.wait(timeout=30)
            except Exception:
                p.terminate()
                try:
                    p.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    p.kill()
                    p.wait()
        log.close()
