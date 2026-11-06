"""Exercise the real hook in disposable repositories without Docker or Gradle."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

HOOK_DIRECTORY = Path(__file__).resolve().parent


class PrePushTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="payguard-hook-test-")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.log = self.root / "calls.log"
        self.env = dict(os.environ, HOOK_TEST_LOG=str(self.log))
        self.git("init", "-q")
        self.git("config", "user.name", "Hook Test")
        self.git("config", "user.email", "hook@example.invalid")
        self.write("build.gradle.kts", "// fixture\n")
        self.write("settings.gradle.kts", "// fixture\n")
        self.write("payguard-wallet/change.txt", "committed\n")
        self.write("git-hook/pre-push.sh", (HOOK_DIRECTORY / "pre-push.sh").read_text())
        self.write("gradlew", '#!/bin/bash\nprintf "gradle %s\\n" "$*" >> "$HOOK_TEST_LOG"\ncat payguard-wallet/change.txt >> "$HOOK_TEST_LOG"\nexit "${HOOK_TEST_EXIT:-0}"\n')
        self.write("bin/act", '#!/bin/bash\nprintf "act %s\\n" "$*" >> "$HOOK_TEST_LOG"\nexit "${HOOK_TEST_EXIT:-0}"\n')
        self.write("bin/docker", "#!/bin/bash\nexit 0\n")
        for name in ("act", "docker"):
            (self.root / "bin" / name).chmod(0o755)
        self.env["PATH"] = str(self.root / "bin") + os.pathsep + os.environ["PATH"]
        self.base = self.commit()

    def write(self, name, content):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)

    def git(self, *arguments):
        return subprocess.check_output(["git", *arguments], cwd=self.root, env=self.env, text=True).strip()

    def commit(self):
        self.git("add", ".")
        self.git("-c", "commit.gpgsign=false", "commit", "-qm", "fixture")
        return self.git("rev-parse", "HEAD")

    def run_hook(self, commit, base=None):
        return subprocess.run(
            ["bash", str(HOOK_DIRECTORY / "pre-push.sh"), "origin"],
            cwd=self.root, env=self.env, text=True, capture_output=True,
            input=f"refs/heads/topic {commit} refs/heads/topic {base or self.base}\n",
        )

    def test_scoped_checks_use_pushed_commit_and_ignore_uncommitted_files(self):
        self.write("payguard-wallet/change.txt", "pushed\n")
        commit = self.commit()
        self.write("payguard-wallet/change.txt", "uncommitted\n")
        result = self.run_hook(commit)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("gradle --build-cache :payguard-wallet:check\npushed\n", self.log.read_text())
        self.assertEqual("uncommitted\n", (self.root / "payguard-wallet/change.txt").read_text())

    def test_shared_changes_run_full_workflow(self):
        self.write("payguard-shared/change.txt", "shared\n")
        result = self.run_hook(self.commit())
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("act pull_request --container-architecture linux/amd64", self.log.read_text())

    def test_missing_committed_gradle_build_fails_with_actionable_message(self):
        (self.root / "build.gradle.kts").unlink()
        result = self.run_hook(self.commit())
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Commit the Gradle migration", result.stderr)
        self.assertFalse(self.log.exists())

    def test_failed_gradle_check_blocks_push(self):
        self.write("payguard-wallet/change.txt", "failing\n")
        commit = self.commit()
        self.env["HOOK_TEST_EXIT"] = "7"
        result = self.run_hook(commit)
        self.assertEqual(7, result.returncode, result.stderr)
        self.assertNotIn("Pre-push validation passed", result.stdout)

    def test_installer_delegates_to_tracked_hook(self):
        subprocess.run(["bash", str(HOOK_DIRECTORY / "install.sh")], cwd=self.root, env=self.env, check=True, capture_output=True)
        result = subprocess.run([str(self.root / ".git/hooks/pre-push"), "origin"], cwd=self.root, env=self.env, input="", text=True, capture_output=True)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("No commits to validate", result.stdout)


if __name__ == "__main__":
    unittest.main()
