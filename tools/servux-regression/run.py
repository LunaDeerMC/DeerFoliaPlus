#!/usr/bin/env python3
"""Compile Servux fixes against a version's real runtime, then run bounded regressions."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import subprocess


HERE = Path(__file__).resolve().parent
DEFAULT_ROOT = HERE.parents[1]
DEFAULT_JDK = Path("/workspace/.cloud-setup/tools/jdk-25.0.4.1")


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def checked_command(command, cwd, log):
    result = subprocess.run(command, cwd=cwd, text=True, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, timeout=120)
    log.write_text(result.stdout)
    if result.returncode:
        print(result.stdout)
        raise SystemExit(result.returncode)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", type=Path, default=DEFAULT_ROOT)
    parser.add_argument("--runtime-dir", type=Path,
                        default=Path("/workspace/.cloud-setup/run/DeerFoliaPlus-cli"))
    parser.add_argument("--jar", type=Path)
    parser.add_argument("--java-home", type=Path,
                        default=Path(os.environ.get("JAVA_HOME", DEFAULT_JDK)))
    parser.add_argument("--compiler-home", type=Path, default=DEFAULT_JDK)
    parser.add_argument("--release", type=int, choices=(21, 25), default=25)
    parser.add_argument("--gradle-home", type=Path,
                        default=Path(os.environ.get("GRADLE_USER_HOME", "/workspace/.cloud-setup/gradle-home")))
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    root = args.source_root.resolve()
    runtime = args.runtime_dir.resolve()
    jars = sorted((runtime / "versions").rglob("*.jar"))
    if args.jar:
        jar = args.jar.resolve()
    elif len(jars) == 1:
        jar = jars[0]
    else:
        parser.error("Specify --jar when the runtime does not contain exactly one extracted server JAR.")
    libraries = sorted((runtime / "libraries").rglob("*.jar"))
    if not jar.is_file() or not libraries:
        parser.error("Supply an extracted server JAR and its version-matched libraries.")
    output = (args.output or root / "build/servux-regression" /
              datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")).resolve()
    output.mkdir(parents=True, exist_ok=False)
    fixed = output / "fixed-classes"
    tests = output / "test-classes"
    fixed.mkdir()
    tests.mkdir()
    classpath = os.pathsep.join(map(str, [jar, *libraries]))
    cache = args.gradle_home / "caches/modules-2/files-2.1"
    annotations = []
    for group, artifact in (("org.jetbrains", "annotations"), ("com.google.code.findbugs", "jsr305")):
        available = sorted((cache / group / artifact).rglob("*.jar"))
        if not available:
            parser.error(f"Missing cached compile-only dependency {group}:{artifact}")
        annotations.append(available[-1])
    compile_classpath = classpath + os.pathsep + os.pathsep.join(map(str, annotations))
    java = str(args.java_home / "bin/java")
    javac = str(args.compiler_home / "bin/javac")
    source_dir = root / "DeerFoliaPlus-server/src/main/java/org/leavesmc/leaves/protocol"
    sources = sorted((source_dir / "servux").glob("*.java"))
    sources.append(source_dir / "core/invoker/PlayerInvokerHolder.java")
    sources.append(source_dir / "fabric/FabricRecipeSyncProtocol.java")
    checked_command([javac, "--release", str(args.release), "-proc:none", "-implicit:none",
                     "-sourcepath", "", "-cp", compile_classpath, "-d", str(fixed), *map(str, sources)],
                    root, output / "compile-production.log")
    probes = sorted(HERE.glob("*.java"))
    checked_command([javac, "--release", str(args.release), "-proc:none", "-cp",
                     str(fixed) + os.pathsep + compile_classpath, "-d", str(tests), *map(str, probes)],
                    root, output / "compile-tests.log")
    source_names = [str(p.relative_to(root)) for p in sources]
    status = subprocess.check_output(["git", "status", "--porcelain=v1", "--", *source_names], cwd=root).decode()
    metadata = {
        "checkoutCommit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root).decode().strip(),
        "sourceRoot": str(root), "sourceStatus": status,
        "serverJar": str(jar), "serverJarSha256": sha256(jar),
        "javaVersion": subprocess.check_output([java, "-version"], stderr=subprocess.STDOUT).decode(),
        "compilerVersion": subprocess.check_output([javac, "-version"], stderr=subprocess.STDOUT).decode(),
        "release": args.release,
        "nettyJars": {str(p): sha256(p) for p in libraries if p.name.startswith(("netty-buffer-", "netty-common-"))},
        "compileOnlyJars": {str(p): sha256(p) for p in annotations},
        "sources": {str(p.relative_to(root)): sha256(p) for p in sources},
        "testSources": {p.name: sha256(p) for p in probes},
        "classFileMajorVersions": sorted({int.from_bytes(p.read_bytes()[6:8], "big") for p in fixed.rglob("*.class")}),
        "scope": "Version-specific modified production sources compiled against the extracted real runtime; component regressions, not a complete server build or world/client end-to-end test.",
        "jvmArgs": ["--enable-native-access=ALL-UNNAMED", "-Xmx256m", "-XX:MaxDirectMemorySize=128m"],
    }
    (output / "metadata.json").write_text(json.dumps(metadata, indent=2) + "\n")
    cp = os.pathsep.join(map(str, [tests, fixed])) + os.pathsep + classpath
    results = []
    for mode in ("wire", "pressure", "decode", "entity", "uploads", "splitter", "nbt", "lifecycle", "concurrent", "protocols"):
        case_dir = output / mode
        case_dir.mkdir()
        result = subprocess.run([java, *metadata["jvmArgs"], "-cp", cp, "ServuxRegression", mode],
                                cwd=case_dir, text=True, stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, timeout=60)
        (case_dir / "output.log").write_text(result.stdout)
        events = [json.loads(line[len("SERVUX_TEST "):]) for line in result.stdout.splitlines()
                  if line.startswith("SERVUX_TEST ")]
        results.append({"mode": mode, "exitCode": result.returncode, "events": events})
        (output / "results.json").write_text(json.dumps(results, indent=2) + "\n")
        print(f"{mode}: exit={result.returncode}, events={len(events)}", flush=True)
        if result.returncode or len(events) < 2:
            print(result.stdout)
            raise SystemExit(result.returncode or 1)
    print(f"Results: {output}")


if __name__ == "__main__":
    main()
