package copper.bridge.v5;

import copper.bridge.annotation.BridgeOverride;

import io.anuke.arc.audio.*;
import io.anuke.arc.files.*;

import copper.bridge.gen.*;

/**
 * One sound of the abstract arc.Audio. The int it plays with is ART's handle, not a SoundPool stream id: a stream id
 * dies with its stream, and the game holds these for as long as it likes.
 */
final class LegacySound implements Sound {
    private final int sound;

    LegacySound(Fi file) {
        this.sound = JvmCall.newSound(file.name(), file.readBytes());
    }

    @BridgeOverride
    public int play(float volume, float pitch, float pan) {
        return sound <= 0 ? -1 : JvmCall.playSound(sound, volume, pitch, pan, false);
    }

    @BridgeOverride
    public int loop(float volume, float pitch, float pan) {
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
    public void stop(int handle) {
        if (sound > 0)
            JvmCall.stopSoundAt(sound, handle);
    }

    @BridgeOverride
    public void pause(int handle) {
        if (sound > 0)
            JvmCall.pauseSoundAt(handle);
    }

    @BridgeOverride
    public void resume(int handle) {
        if (sound > 0)
            JvmCall.resumeSoundAt(handle);
    }

    @BridgeOverride
    public void setLooping(int handle, boolean looping) {
        if (sound > 0)
            JvmCall.setSoundLooping(handle, looping);
    }

    @BridgeOverride
    public void setPitch(int handle, float pitch) {
        if (sound > 0)
            JvmCall.setSoundPitch(handle, pitch);
    }

    @BridgeOverride
    public void setVolume(int handle, float volume) {
        if (sound > 0)
            JvmCall.setSoundVolume(handle, volume);
    }

    @BridgeOverride
    public void setPan(int handle, float pan, float volume) {
        if (sound > 0)
            JvmCall.setSoundPan(handle, pan, volume);
    }
}