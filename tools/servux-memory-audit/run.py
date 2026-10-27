#!/usr/bin/env python3
"""Bounded, offline probes against a real server JAR and unchanged checkout sources."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime-dir", type=Path,
                        default=Path("/workspace/.cloud-setup/run/DeerFoliaPlus-cli"))
    parser.add_argument("--jar", type=Path)
    parser.add_argument("--java-home", type=Path,
                        default=Path(os.environ.get("JAVA_HOME", "/workspace/.cloud-setup/tools/jdk-25.0.4.1")))
    parser.add_argument("--output", type=Path)
    parser.add_argument("--gradle-home", type=Path,
                        default=Path(os.environ.get("GRADLE_USER_HOME", "/workspace/.cloud-setup/gradle-home")))
    args = parser.parse_args()
    jar = (args.jar or args.runtime_dir / "versions/26.2/DeerFoliaPlus-26.2.jar").resolve()
    libraries = sorted((args.runtime_dir / "libraries").rglob("*.jar"))
    if not jar.is_file() or not libraries:
        parser.error("Supply an extracted server JAR and its runtime libraries.")
    output = (args.output or ROOT / "build/servux-memory-audit" /
              datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")).resolve()
    output.mkdir(parents=True, exist_ok=False)
    classes = output / "probe-classes"
    current = output / "current-source-classes"
    classes.mkdir()
    current.mkdir()
    classpath = os.pathsep.join(map(str, [jar, *libraries]))
    cache = args.gradle_home / "caches/modules-2/files-2.1"
    annotations = []
    for group, artifact in (("org.jetbrains", "annotations"), ("com.google.code.findbugs", "jsr305")):
        available = sorted((cache / group / artifact).rglob("*.jar"))
        if not available:
            parser.error(f"Missing compile-only dependency {group}:{artifact} under {cache}")
        annotations.append(available[-1])
    compile_classpath = classpath + os.pathsep + os.pathsep.join(map(str, annotations))
    javac = str(args.java_home / "bin/javac")
    java = str(args.java_home / "bin/java")
    probes = sorted(HERE.glob("*.java"))
    subprocess.run([javac, "-proc:none", "-cp", classpath, "-d", str(classes),
                    *map(str, probes)], cwd=ROOT, check=True)
    source_dir = ROOT / "DeerFoliaPlus-server/src/main/java/org/leavesmc/leaves/protocol/servux"
    sources = sorted(source_dir.glob("*.java"))
    subprocess.run([javac, "-proc:none", "-implicit:none", "-sourcepath", "", "-cp", compile_classpath,
                    "-d", str(current), *map(str, sources)], cwd=ROOT, check=True)
    metadata = {
        "checkoutCommit": git("rev-parse", "HEAD").decode().strip(),
        "serverJar": str(jar), "serverJarSha256": sha256(jar),
        "javaVersion": subprocess.check_output([java, "-version"], stderr=subprocess.STDOUT).decode(),
        "nettyJars": {str(p): sha256(p) for p in libraries if p.name.startswith(("netty-buffer-", "netty-common-"))},
        "compileOnlyJars": {str(p): sha256(p) for p in annotations},
        "probeSources": {str(p.relative_to(ROOT)): sha256(p) for p in probes},
        "sources": {str(p.relative_to(ROOT)): sha256(p) for p in sources},
        "servuxCommitsUnchangedSinceF24ea8b": not git("diff", "f24ea8b", "HEAD", "--", str(source_dir)),
        "servuxWorktreeMatchesHead": not git("diff", "HEAD", "--", str(source_dir)),
        "scope": "Actual cached server JAR; then unmodified current Servux sources compiled against that runtime. No complete 26.3 build, world, production server, or production JAR.",
        "jvmArgs": ["--enable-native-access=ALL-UNNAMED", "-Xmx256m", "-XX:MaxDirectMemorySize=128m"],
    }
    (output / "metadata.json").write_text(json.dumps(metadata, indent=2) + "\n")
    results = []
    for target in ("jar", "current-source"):
        prefix = [classes] if target == "jar" else [classes, current]
        cp = os.pathsep.join(map(str, prefix)) + os.pathsep + classpath
        cases = [("MemoryProbe", mode) for mode in ("allocator", "structures", "control", "equivalence", "splitter")]
        if (HERE / "SplitterProbe.java").exists():
            cases.append(("SplitterProbe", None))
        for probe, mode in cases:
            name = f"{target}-{probe}-{mode or 'boundaries'}"
            case_dir = output / name
            case_dir.mkdir()
            command = [java, *metadata["jvmArgs"], "-cp", cp, probe]
            if mode:
                command.append(mode)
            result = subprocess.run(command, cwd=case_dir, text=True,
                                    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=60)
            (case_dir / "output.log").write_text(result.stdout)
            events = [json.loads(line[6:]) for line in result.stdout.splitlines() if line.startswith("AUDIT ")]
            results.append({"target": target, "probe": probe, "mode": mode,
                            "exitCode": result.returncode, "events": events})
            (output / "results.json").write_text(json.dumps(results, indent=2) + "\n")
            print(f"{name}: exit={result.returncode}, events={len(events)}", flush=True)
            if result.returncode or not events:
                print(result.stdout)
                raise SystemExit(result.returncode or 1)
    print(f"Results: {output}")


if __name__ == "__main__":
    main()
