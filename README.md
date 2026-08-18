# autocommit

Watches a set of git repositories and commits, pulls and pushes every change automatically —
a safety net for note-taking repositories, not a replacement for real commits.

Runs as a launchd agent under the label `autocommit`.

## Configuration

`~/autocommit-config.yaml`, read once at startup (a change requires a restart):

```yaml
commitMessagePrefix: '[mbp m4]'   # optional, applies to all repositories
repositories:
  - name: pap
    path: /Users/mlo/git-pap/pap
    branch: main
    # commitMessagePrefix: '[...]'  # optional, overrides the common prefix
```

One thread watches each repository, recursively, `.git` excluded. Each thread runs a first
cycle at startup, so changes made while the process was down are picked up without waiting for
a filesystem event; afterwards every filesystem event triggers a cycle. The behaviour of a
cycle depends on the state of the repository:

| Repository state                          | Behaviour                                                                  |
| ----------------------------------------- | -------------------------------------------------------------------------- |
| on the configured `branch`                | `git add --all`, `git commit`, `git pull --rebase`, `git push`              |
| on any other branch                       | `git add --all` only, then a warning listing the staged files — no commit   |
| detached HEAD (rebase, merge, bisect)     | nothing at all, just a warning                                             |

So checking out another branch is enough to stop the commits, but the index keeps being
staged; only a detached HEAD leaves the repository completely untouched.

Errors during a cycle are logged and the watcher moves on, so a transient git failure never
leaves a repository silently unwatched.

## Env

Install:

* sdkman
* direnv
* devbox

devbox inits sdkman (sdkman bootstrap is NOT needed in shell init)

## Build

```sh
./gradlew nativeCompile
```

The binary lands in `build/native/nativeCompile/autocommit`.

## Deploy an update

The agent runs `~/work/autocommit/autocommit`, not the build output, so the binary has to be
copied over. It cannot be overwritten while running, hence stop, copy, start:

```sh
./gradlew nativeCompile
launchctl bootout gui/$(id -u)/autocommit
cp build/native/nativeCompile/autocommit ~/work/autocommit/autocommit
launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/net.mlorber.autocommit.plist
launchctl print gui/$(id -u)/autocommit | head -5   # expect: state = running
```

## Operate

The plist is `~/Library/LaunchAgents/net.mlorber.autocommit.plist` but its label is
`autocommit`, so that — not the file name — is the service target.

```sh
# restart without replacing the binary (after editing ~/autocommit-config.yaml)
launchctl kickstart -k gui/$(id -u)/autocommit

# status, and the pid to check the watcher is alive
launchctl print gui/$(id -u)/autocommit

# stop until next login, then start again
launchctl bootout gui/$(id -u)/autocommit
launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/net.mlorber.autocommit.plist
```

`KeepAlive` is set, so the agent is restarted whenever it exits — a plain `kill` is not
enough to stop it, use `bootout`.

## Logs

Both files are appended to, never rotated; truncate them when they get large.

```sh
tail -f ~/work/autocommit/autocommit.log         # stdout: one line per commit
tail -f ~/work/autocommit/autocommit-error.log   # stderr: crashes only
: > ~/work/autocommit/autocommit.log             # truncate
```
