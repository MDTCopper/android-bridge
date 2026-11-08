# Contributing

This document sets out what a change to this repository has to satisfy, and which changes are not
accepted.

## Repository boundary

This repository produces `bridge.jar` and a set of host conventions, not an app. The ART side owns
activities, the surface, input and the lifecycle; the JVM side owns the game loop and EGL/GL. The host
app is provided or chosen by whoever uses the bridge.

## Accepted changes

- fixing a behaviour problem on one game version
- adding a version branch for a new game version
- adding extension points for hosts
- fixing build, packaging or compatibility problems

## Changes not accepted

- anything past the bridge's job. The bridge does one thing: get the desktop game running; display,
  accounts, downloads, mod loading and UI belong to the host
- changes that depend on modifying the game, arc or the launcher. A change has to work on them
  unmodified
- special cases for one device or ROM

## Extending

Extend through the conventions that already exist: subclasses, options, events, declarations. When they
are not enough, add an interface, but it has to be usable by any host and carry no assumptions about
one. The first-frame interface was added that way: the bridge only calls back when the game hands over
its first frame, and what to show is up to the host.

## Reusing what exists

The repository already has these mechanisms; check whether they express the need before extending
anything:

- Cross-VM calls: the annotated declarations in `core`; the processor generates both sides
- Logging: `<game data folder>/last_log.txt`
- Epochs: the `:prepareEpoch*` tasks
- Version ownership: the ranges declared in `gradle.properties`
- Host options: `--gl2`, `--gl3`, `--angle`, `--angle-path`, `--logcat`, and others

When the existing mechanisms cannot express it, extend an existing declaration instead of opening
another channel. The code is the reference for the rest.

## Versions and ranges

A branch serves one version range; the ranges and refs are declared in `gradle.properties`. Version
ownership is expressed with ranges and real build numbers, not with "this version number belongs to
that tier" special cases.

A branch compiles against the newest epoch in its range, but may only reference symbols that also exist
at the lower bound. The compiler does not check this; a device run does.

## Code and comments

- Java, not Kotlin. Identifiers, comments and logs are English ASCII.
- Comments give the reason, the constraint and the guarantee; they do not restate what the code does,
  and do not reference documents outside this repository.
- The ART side of the bridge uses public APIs only.
- Per-frame data crossing the VMs carries scalars only; high-frequency data that needs boxing gets a new
  Batch channel.

## Commits

Start the game on a device once with the `bridge.jar` you built. After touching branch sources, an epoch
or a version range, also run it on the oldest version in range. Compiling is not running.

Run the full build before committing:

```bash
./gradlew :processor:compileJava :core:compileJava :bridge-v1:compileJava :bridge-v2:compileJava \
          :native:assemble :pack:bridgeJar
```

Without an NDK or the Android SDK, at least finish the Java compile, and say in the pull request which
steps you did not run.

Commit messages are one sentence, start with a verb, and do one thing:

```text
fix volume keys not adjusting volume
add safe insets support
v2: add the Mindustry 9 branch (arc merged into the game repository)
```

For a large change, or one that touches host conventions (options, log format, exit behaviour, how the
activity is created), open an issue with the plan first.

Do not commit `.mindustry/`, `build/`, `*.so`, the lwjgl natives, `native/src/gen/` or any jar,
and do not include changes that only reformat or reorder code.

After touching `@Native` or `@UsedByNative`: the processor generates the names and descriptors from the
Java declarations, so the JNI registration table and the native-to-Java stubs follow the declaration and
are not written by hand. The C++ definition a `@Native` points at is hand-written: if its name or
parameters no longer match, `:native:assemble` fails to link rather than failing at runtime.

## Reporting problems

Include:

- device model, Android version and ABI
- the first line of `last_log.txt` (the bridge version) and the `game version:` line in it
- which of `--angle`, `--angle-path`, `--gl2`, `--gl3` were used
- the relevant log excerpt
