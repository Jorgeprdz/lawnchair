"""Only reuse APKs when the compiled app and instrumentation sources are unchanged."""
import json
import os
import pathlib
import re
import subprocess

run_id = os.environ["ONEUI_APK_RUN"]
if not re.fullmatch(r"[0-9]+", run_id):
    raise SystemExit("APK run must be a numeric GitHub Actions run ID")
repo = os.environ["GITHUB_REPOSITORY"]
head = os.environ["GITHUB_SHA"]

def api(path):
    return json.loads(subprocess.check_output(["gh", "api", f"repos/{repo}/{path}"]))

run = api(f"actions/runs/{run_id}")
if run["event"] not in ("push", "workflow_dispatch") or run["head_branch"] != "feature/oneui-enhancements":
    raise SystemExit("Reuse requires a build from the feature branch, not a PR merge build")
if run["head_repository"]["full_name"] != repo:
    raise SystemExit("APK must come from this repository")
comparison = api(f"compare/{run['head_sha']}...{head}")
if comparison["status"] not in ("ahead", "identical") or comparison["merge_base_commit"]["sha"] != run["head_sha"]:
    raise SystemExit("APK source commit must be an ancestor of this revision")
allowed = {
    ".github/workflows/ci.yml",
    ".github/scripts/oneui-emulator-smoke.sh",
    ".github/scripts/verify-oneui-apk.py",
    ".codex/oneui-progress.md",
}
changed = comparison.get("files", [])
if len(changed) >= 300 or any(f["filename"] not in allowed or
        (f.get("previous_filename") and f["previous_filename"] not in allowed) for f in changed):
    raise SystemExit("Application/tests/build inputs changed: compile a fresh APK instead")
jobs = api(f"actions/runs/{run_id}/jobs?per_page=100")["jobs"]
build = next((j for j in jobs if j["name"] ==
    "build-debug-apk (assembleLawnWithQuickstepGithubDebug)"), None)
if not build or build["conclusion"] != "success":
    raise SystemExit("GithubDebug build did not succeed")
for name in ("Build APK", "Build OneUI instrumentation APK", "Upload artifact"):
    if not any(s["name"] == name and s["conclusion"] == "success" for s in build["steps"]):
        raise SystemExit(f"Required build step did not succeed: {name}")
if not any(j["name"] == "check-style" and j["conclusion"] == "success" for j in jobs):
    raise SystemExit("Source style validation did not succeed")
artifacts = api(f"actions/runs/{run_id}/artifacts?per_page=100")["artifacts"]
if not any(a["name"] == "assembleLawnWithQuickstepGithubDebug" and not a["expired"] for a in artifacts):
    raise SystemExit("Required APK artifact is missing or expired")
result = f"Reused GithubDebug + AndroidTest from run {run_id}, source {run['head_sha']}\n"
pathlib.Path("oneui-emulator-results").mkdir(exist_ok=True)
pathlib.Path("oneui-emulator-results/apk-source.txt").write_text(result)
print(result)
