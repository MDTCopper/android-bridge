package copper.bridge.v1;

import copper.bridge.annotation.BridgeOverride;

import io.anuke.arc.audio.*;
import io.anuke.arc.files.*;

import copper.bridge.gen.*;

/**
 * One sound of the abstract arc.Audio. The int it plays with is ART's handle, not a SoundPool stream id: a stream id
 * dies with its stream, and the game holds these for as long as it likes.
 *
 * <p>This era's Sound interface names the handle {@code long}; the bridge's handles are still ints.
 */
final class LegacySound implements Sound {
    private final int sound;

    LegacySound(FileHandle file) {
        this.sound = JvmCall.newSound(file.name(), file.readBytes());
    }

    @BridgeOverride
    public long play(float volume, float pitch, float pan) {
        return sound <= 0 ? -1 : JvmCall.playSound(sound, volume, pitch, pan, false);
    }

    @BridgeOverride
    public long loop(float volume, float pitch, float pan) {
        return sound <= 0 ? -1 : JvmCall.playSound(sound, volume, pitch, pan, true);
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
    public void stop(long handle) {
        if (sound > 0)
            JvmCall.stopSoundAt(sound, (int)handle);
    }

    @BridgeOverride
    public void pause(long handle) {
        if (sound > 0)
            JvmCall.pauseSoundAt((int)handle);
    }

    @BridgeOverride
    public void resume(long handle) {
        if (sound > 0)
            JvmCall.resumeSoundAt((int)handle);
    }

    @BridgeOverride
    public void setLooping(long handle, boolean looping) {
        if (sound > 0)
            JvmCall.setSoundLooping((int)handle, looping);
    }

    @BridgeOverride
    public void setPitch(long handle, float pitch) {
        if (sound > 0)
            JvmCall.setSoundPitch((int)handle, pitch);
    }

    @BridgeOverride
    public void setVolume(long handle, float volume) {
        if (sound > 0)
            JvmCall.setSoundVolume((int)handle, volume);
    }

    @BridgeOverride
    public void setPan(long handle, float pan, float volume) {
        if (sound > 0)
            JvmCall.setSoundPan((int)handle, pan, volume);
    }
}
