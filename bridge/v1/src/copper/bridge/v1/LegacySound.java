package copper.bridge.v1;

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

    @Override
    public long play(float volume, float pitch, float pan) {
        return sound <= 0 ? -1 : JvmCall.playSound(sound, volume, pitch, pan, false);
    }

    @Override
    public long loop(float volume, float pitch, float pan) {
        return sound <= 0 ? -1 : JvmCall.playSound(sound, volume, pitch, pan, true);
    }

    @Override
    public void stop() {
        if (sound > 0)
            JvmCall.stopSound(sound);
    }

    /** SoundPool pauses every stream at once; it has no per-sound switch, so this is what the platform offers. */
    @Override
    public void pause() {
        if (sound > 0)
            JvmCall.pauseSound();
    }

    @Override
    public void resume() {
        if (sound > 0)
            JvmCall.resumeSound();
    }

    @Override
    public void dispose() {
        if (sound > 0)
            JvmCall.disposeSound(sound);
    }

    @Override
    public void stop(long handle) {
        if (sound > 0)
            JvmCall.stopSoundAt(sound, (int)handle);
    }

    @Override
    public void pause(long handle) {
        if (sound > 0)
            JvmCall.pauseSoundAt((int)handle);
    }

    @Override
    public void resume(long handle) {
        if (sound > 0)
            JvmCall.resumeSoundAt((int)handle);
    }

    @Override
    public void setLooping(long handle, boolean looping) {
        if (sound > 0)
            JvmCall.setSoundLooping((int)handle, looping);
    }

    @Override
    public void setPitch(long handle, float pitch) {
        if (sound > 0)
            JvmCall.setSoundPitch((int)handle, pitch);
    }

    @Override
    public void setVolume(long handle, float volume) {
        if (sound > 0)
            JvmCall.setSoundVolume((int)handle, volume);
    }

    @Override
    public void setPan(long handle, float pan, float volume) {
        if (sound > 0)
            JvmCall.setSoundPan((int)handle, pan, volume);
    }
}
