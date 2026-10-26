#!/usr/bin/env python3
"""Run interactively on the Lightsail host; never accepts passwords as arguments."""
import getpass
import pathlib
import re
import subprocess
import sys

import bcrypt

ROOT = pathlib.Path(__file__).resolve().parent


def mysql(sql):
    result = subprocess.run(
        ["sudo", "docker", "compose", "exec", "-T", "mysql", "sh", "-c",
         'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --default-character-set=utf8mb4 '
         '-uadmin --batch --skip-column-names admin_portfolio'],
        input=sql, text=True, encoding="utf-8", cwd=ROOT,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    )
    if result.returncode:
        # Database errors can contain SQL values, so do not echo them.
        raise RuntimeError("Database command failed. Check the MySQL container status.")
    return result.stdout.strip()


def sql_text(value):
    return "CONVERT(X'" + value.encode("utf-8").hex() + "' USING utf8mb4)"


def main():
    if mysql("SELECT COUNT(*) FROM users;") != "0":
        print("Users already exist. No account was created or changed.")
        return
    email = input("Admin login email: ").strip()
    if len(email) > 100 or not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", email):
        raise ValueError("Enter a valid email address, up to 100 characters.")
    password = getpass.getpass("New password (hidden, at least 12 characters): ")
    if len(password) < 12 or len(password.encode("utf-8")) > 72:
        raise ValueError("Use at least 12 characters and at most 72 UTF-8 bytes.")
    if password != getpass.getpass("Confirm password: "):
        raise ValueError("Passwords do not match.")
    hashed = "{bcrypt}" + bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt(rounds=10)).decode("ascii")
    del password
    # Guard the write as well as the initial check. Never reset existing users.
    rows = mysql(
        "INSERT INTO users (name, email, role, password_hash) SELECT "
        + ", ".join(map(sql_text, ["포트폴리오 관리자", email, "관리자", hashed]))
        + " FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM users); SELECT ROW_COUNT();"
    )
    if rows != "1":
        raise RuntimeError("No account created: a user may already have been added.")
    print("Admin account created. Log in with the email and password you entered.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
    except (EOFError, KeyboardInterrupt):
        print("\nCancelled. No further action taken.", file=sys.stderr)
        sys.exit(1)
