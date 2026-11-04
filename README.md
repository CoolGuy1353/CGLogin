# CGLogin

CGLogin is a Spigot-compatible Minecraft plugin that provides `/register`,
`/login`, and `/logout` for players, plus `/resetpw` for administrators.

## Build

Requires Java 17 and Maven. Build the plugin with `mvn package`; the jar is
created in `target/`. Place it in the server's `plugins/` directory and restart.

## Commands

- `/register <password>` creates an account for your Minecraft username.
- `/login <password>` authenticates for the current session.
- `/logout` ends the current authenticated session.
- `/resetpw <player> <new-password>` resets a registered account. Requires
	`cglogin.admin` (operators by default).

Passwords are stored as salted PBKDF2-HMAC-SHA256 hashes in
`plugins/CGLogin/accounts.yml`, keyed by lowercase username. Existing UUID-keyed
accounts are migrated using their saved usernames. On join, players are told
whether to log in or register. Successful login is remembered for 7 days by
default; returning within that window restores the session automatically and
extends the window. Players can use `/logout` to clear the remembered session.

The session duration, authentication timeout, minimum password length, and
messages can be customized in `plugins/CGLogin/config.yml`. Set
`remember-session-hours` to `0` to disable remembered sessions.