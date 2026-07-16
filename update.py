#!/usr/bin/env python3
"""
update.py — Termux automation for SafeTube Kids (Toofar6321/Safetubekids)

Commands:
    python update.py push "commit message"     Swap in clean manifest, commit, push
    python update.py status                    Show git status + manifest/key state
    python update.py backup-key                Back up local.properties (Claude API key) to safe storage
    python update.py restore-key [timestamp]   Restore a backed-up local.properties (latest if no timestamp given)
    python update.py list-backups              List available key backups
"""

import argparse
import shutil
import subprocess
import sys
import time
from pathlib import Path

# CONFIG — adjust these three paths if your layout differs
REPO_DIR = Path.home() / "Safetubekids"
MANIFEST_CLEAN = REPO_DIR / "manifest_clean.xml"
MANIFEST_TARGET = REPO_DIR / "app" / "src" / "main" / "AndroidManifest.xml"
LOCAL_PROPS = REPO_DIR / "local.properties"
KEY_BACKUP_DIR = Path.home() / ".safetube_backups"


def run(cmd, cwd=REPO_DIR, check=True):
    print(f"$ {' '.join(cmd)}")
    result = subprocess.run(cmd, cwd=cwd, text=True, capture_output=True)
    if result.stdout.strip():
        print(result.stdout.strip())
    if result.stderr.strip():
        print(result.stderr.strip())
    if check and result.returncode != 0:
        sys.exit(f"Command failed: {' '.join(cmd)}")
    return result


def require_repo():
    if not REPO_DIR.exists():
        sys.exit(f"Repo not found at {REPO_DIR}. Edit REPO_DIR in update.py or git clone it there.")


def swap_manifest():
    if not MANIFEST_CLEAN.exists():
        print(f"manifest_clean.xml not found at {MANIFEST_CLEAN} — skipping manifest swap.")
        return
    MANIFEST_TARGET.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(MANIFEST_CLEAN, MANIFEST_TARGET)
    print(f"Copied manifest_clean.xml -> {MANIFEST_TARGET.relative_to(REPO_DIR)}")


def cmd_push(args):
    require_repo()
    swap_manifest()
    run(["git", "add", "-A"])
    diff = run(["git", "diff", "--cached", "--quiet"], check=False)
    if diff.returncode == 0:
        print("Nothing to commit — working tree clean after manifest swap.")
        return
    run(["git", "commit", "-m", args.message])
    run(["git", "push"])
    print("Pushed.")


def cmd_status(_args):
    require_repo()
    print("-- git status --")
    run(["git", "status", "-s"], check=False)
    print("\n-- manifest --")
    print(f"clean source exists: {MANIFEST_CLEAN.exists()}")
    print(f"target exists:       {MANIFEST_TARGET.exists()}")
    print("\n-- api key --")
    print(f"local.properties exists: {LOCAL_PROPS.exists()}")
    backups = sorted(KEY_BACKUP_DIR.glob("local.properties.*")) if KEY_BACKUP_DIR.exists() else []
    print(f"backups on file: {len(backups)}")


def cmd_backup_key(_args):
    if not LOCAL_PROPS.exists():
        sys.exit(f"{LOCAL_PROPS} not found — nothing to back up.")
    KEY_BACKUP_DIR.mkdir(parents=True, exist_ok=True)
    stamp = time.strftime("%Y%m%d-%H%M%S")
    dest = KEY_BACKUP_DIR / f"local.properties.{stamp}"
    shutil.copyfile(LOCAL_PROPS, dest)
    print(f"Backed up to {dest}")


def cmd_restore_key(args):
    if not KEY_BACKUP_DIR.exists():
        sys.exit("No backup directory found — nothing to restore.")
    backups = sorted(KEY_BACKUP_DIR.glob("local.properties.*"))
    if not backups:
        sys.exit("No backups found.")

    if args.timestamp:
        matches = [b for b in backups if args.timestamp in b.name]
        if not matches:
            sys.exit(f"No backup matching '{args.timestamp}'. Run list-backups to see options.")
        src = matches[-1]
    else:
        src = backups[-1]

    if LOCAL_PROPS.exists():
        safety = LOCAL_PROPS.with_suffix(".properties.pre-restore")
        shutil.copyfile(LOCAL_PROPS, safety)
        print(f"Existing local.properties saved to {safety} before overwrite.")

    shutil.copyfile(src, LOCAL_PROPS)
    print(f"Restored {src.name} -> local.properties")


def cmd_list_backups(_args):
    if not KEY_BACKUP_DIR.exists():
        print("No backups yet.")
        return
    backups = sorted(KEY_BACKUP_DIR.glob("local.properties.*"))
    if not backups:
        print("No backups yet.")
        return
    for b in backups:
        print(b.name)


def main():
    parser = argparse.ArgumentParser(description="SafeTube Kids Termux automation")
    sub = parser.add_subparsers(dest="cmd", required=True)

    p_push = sub.add_parser("push", help="Swap manifest, commit, push")
    p_push.add_argument("message", help="Commit message")
    p_push.set_defaults(func=cmd_push)

    p_status = sub.add_parser("status", help="Show current state")
    p_status.set_defaults(func=cmd_status)

    p_backup = sub.add_parser("backup-key", help="Back up local.properties")
    p_backup.set_defaults(func=cmd_backup_key)

    p_restore = sub.add_parser("restore-key", help="Restore a backed-up local.properties")
    p_restore.add_argument("timestamp", nargs="?", help="Optional timestamp/substring to match")
    p_restore.set_defaults(func=cmd_restore_key)

    p_list = sub.add_parser("list-backups", help="List available key backups")
    p_list.set_defaults(func=cmd_list_backups)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
