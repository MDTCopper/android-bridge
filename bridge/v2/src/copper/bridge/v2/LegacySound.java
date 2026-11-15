package copper.bridge.v2;

import copper.bridge.annotation.BridgeHandled;
import copper.bridge.annotation.BridgeRename;
import copper.bridge.annotation.BridgeOverride;

import io.anuke.arc.audio.*;
import io.anuke.arc.files.*;

import copper.bridge.gen.*;

/**
 * One sound of the abstract arc.Audio. The int it plays with is ART's handle, not a SoundPool stream id: a stream id
 * dies with its stream, and the game holds these for as long as it likes.
 *
 * <p>84 and 85 declare play and loop with a long handle, 86 and later with an int. Java cannot declare both under one
 * name because they differ only in return type, so the long variants carry a build name and the packer renames them;
 * the class then has both JVM overloads and matches whichever interface the running game declares. The handle-typed
 * stop/pause/set methods are ordinary overloads and need no rename.
 */
@BridgeHandled(type = "io/anuke/arc/audio/Sound", member = "play", kind = BridgeHandled.Kind.SIGNATURE,
        reason = "84/85 use a long handle, 86+ an int; both overloads are declared here")
@BridgeHandled(type = "io/anuke/arc/audio/Sound", member = "loop", kind = BridgeHandled.Kind.SIGNATURE,
        reason = "84/85 use a long handle, 86+ an int; both overloads are declared here")
@BridgeHandled(type = "io/anuke/arc/audio/Sound", member = "stop", kind = BridgeHandled.Kind.SIGNATURE,
        reason = "the handle type changes between 85 and 86")
@BridgeHandled(type = "io/anuke/arc/audio/Sound", member = "pause", kind = BridgeHandled.Kind.SIGNATURE,
        reason = "the handle type changes between 85 and 86")
@BridgeHandled(type = "io/anuke/arc/audio/Sound", member = "resume", kind = BridgeHandled.Kind.SIGNATURE,
        reason = "the handle type changes between 85 and 86")
@BridgeHandled(type = "io/anuke/arc/audio/Sound", member = "setLooping", kind = BridgeHandled.Kind.SIGNATURE,
        reason = "the handle type changes between 85 and 86")
@BridgeHandled(type = "io/anuke/arc/audio/Sound", member = "setPitch", kind = BridgeHandled.Kind.SIGNATURE,
        reason = "the handle type changes between 85 and 86")
@BridgeHandled(type = "io/anuke/arc/audio/Sound", member = "setVolume", kind = BridgeHandled.Kind.SIGNATURE,
        reason = "the handle type changes between 85 and 86")
@BridgeHandled(type = "io/anuke/arc/audio/Sound", member = "setPan", kind = BridgeHandled.Kind.SIGNATURE,
        reason = "the handle type changes between 85 and 86")
final class LegacySound implements Sound {
    private final int sound;

    LegacySound(FileHandle file) {
        this.sound = JvmCall.newSound(file.name(), file.readBytes());
    }

    @BridgeOverride
    public int play(float volume, float pitch, float pan) {
        return sound <= 0 ? -1 : JvmCall.playSound(sound, volume, pitch, pan, false);
    }

    @BridgeRename("play")
    @BridgeOverride
    public long playLong(float volume, float pitch, float pan) {
        return sound <= 0 ? -1 : (long)JvmCall.playSound(sound, volume, pitch, pan, false);
    }

    @BridgeOverride
    public int loop(float volume, float pitch, float pan) {
        return sound <= 0 ? -1 : JvmCall.playSound(sound, volume, pitch, pan, true);
    }

    @BridgeRename("loop")
    @BridgeOverride
    public long loopLong(float volume, float pitch, float pan) {
        return sound <= 0 ? -1 : (long)JvmCall.playSound(sound, volume, pitch, pan, true);
    }

    @BridgeOverride
    public void stop() {
        if (sound > 0)
            JvmCall.stopSound(sound);
    }

    /** SoundPool pauses every stream at once; it has no per-sound switch, so this is what the platform offers. */
    @BridgeOverride
    public void pause() {
        if (sound > 0)
            JvmCall.pauseSound();
    }

    @BridgeOverride
    public void resume() {
        if (sound > 0)
            JvmCall.resumeSound();
    }

    @BridgeOverride
    public void dispose() {
        if (sound > 0)
            JvmCall.disposeSound(sound);
    }

    @BridgeOverride
    public void stop(int handle) {
        if (sound > 0)
            JvmCall.stopSoundAt(sound, handle);
    }

    @BridgeOverride
    public void stop(long handle) {
        if (sound > 0)
            JvmCall.stopSoundAt(sound, (int)handle);
    }

    @BridgeOverride
    public void pause(int handle) {
        if (sound > 0)
            JvmCall.pauseSoundAt(handle);
    }

    @BridgeOverride
    public void pause(long handle) {
        if (sound > 0)
            JvmCall.pauseSoundAt((int)handle);
    }

    @BridgeOverride
    public void resume(int handle) {
        if (sound > 0)
            JvmCall.resumeSoundAt(handle);
    }

    @BridgeOverride
    public void resume(long handle) {
        if (sound > 0)
            JvmCall.resumeSoundAt((int)handle);
    }

    @BridgeOverride
    public void setLooping(int handle, boolean looping) {
        if (sound > 0)
            JvmCall.setSoundLooping(handle, looping);
    }

    @BridgeOverride
    public void setLooping(long handle, boolean looping) {
        if (sound > 0)
            JvmCall.setSoundLooping((int)handle, looping);
    }

    @BridgeOverride
    public void setPitch(int handle, float pitch) {
        if (sound > 0)
            JvmCall.setSoundPitch(handle, pitch);
    }

    @BridgeOverride
    public void setPitch(long handle, float pitch) {
        if (sound > 0)
            JvmCall.setSoundPitch((int)handle, pitch);
    }

    @BridgeOverride
    public void setVolume(int handle, float volume) {
        if (sound > 0)
            JvmCall.setSoundVolume(handle, volume);
    }

    @BridgeOverride
    public void setVolume(long handle, float volume) {
        if (sound > 0)
            JvmCall.setSoundVolume((int)handle, volume);
    }

    @BridgeOverride
    public void setPan(int handle, float pan, float volume) {
        if (sound > 0)
            JvmCall.setSoundPan(handle, pan, volume);
    }

    @BridgeOverride
    public void setPan(long handle, float pan, float volume) {
        if (sound > 0)
            JvmCall.setSoundPan((int)handle, pan, volume);
    }
}
