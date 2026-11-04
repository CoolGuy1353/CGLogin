# CGLogin

CGLogin is a Spigot-compatible Minecraft plugin that provides `/register`,
`/login`, and `/logout` for players, plus `/resetpw` for administrators.

## Build

Requires Java 17 and Maven. Build the plugin with `mvn package`; the jar is
created in `target/`. Place it in the server's `plugins/` directory and restart.

## Commands

- `/register <password>` creates an account for your Minecraft UUID.
- `/login <password>` authenticates for the current session.
- `/logout` ends the current authenticated session.
- `/resetpw <player> <new-password>` resets a registered account. Requires
	`cglogin.admin` (operators by default).

Passwords must be at least 8 characters. Passwords are stored as salted
PBKDF2-HMAC-SHA256 hashes in `plugins/CGLogin/accounts.yml`. Players must
authenticate after each join, and unauthenticated players cannot move, chat,
interact, or use other commands. The authentication timeout is configurable in
`plugins/CGLogin/config.yml`.