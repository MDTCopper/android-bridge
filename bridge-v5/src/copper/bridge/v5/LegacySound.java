package copper.bridge.v5;

import arc.audio.*;
import arc.files.*;

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

    @Override
    public int play(float volume, float pitch, float pan) {
        return sound <= 0 ? -1 : JvmCall.playSound(sound, volume, pitch, pan, false);
    }

    @Override
    public int loop(float volume, float pitch, float pan) {
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
    public void stop(int handle) {
        if (sound > 0)
            JvmCall.stopSoundAt(sound, handle);
    }

    @Override
    public void pause(int handle) {
        if (sound > 0)
            JvmCall.pauseSoundAt(handle);
    }

    @Override
    public void resume(int handle) {
        if (sound > 0)
            JvmCall.resumeSoundAt(handle);
    }

    @Override
    public void setLooping(int handle, boolean looping) {
        if (sound > 0)
            JvmCall.setSoundLooping(handle, looping);
    }

    @Override
    public void setPitch(int handle, float pitch) {
        if (sound > 0)
            JvmCall.setSoundPitch(handle, pitch);
    }

    @Override
    public void setVolume(int handle, float volume) {
        if (sound > 0)
            JvmCall.setSoundVolume(handle, volume);
    }

    @Override
    public void setPan(int handle, float pan, float volume) {
        if (sound > 0)
            JvmCall.setSoundPan(handle, pan, volume);
    }
}
