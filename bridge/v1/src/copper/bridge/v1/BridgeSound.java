package copper.bridge.v1;

import copper.bridge.annotation.BridgeOverride;
import copper.bridge.gen.JvmCall;

import com.badlogic.gdx.audio.Sound;

/** One sound in ART's mixer; every instance handle belongs to that mixer. */
public class BridgeSound implements Sound {
    private final int sound;

    public BridgeSound(int sound) {
        this.sound = sound;
    }

    @Override
    @BridgeOverride
    public long play() {
        return play(1f);
    }

    @Override
    @BridgeOverride
    public long play(float volume) {
        return play(volume, 1f, 0f);
    }

    @Override
    @BridgeOverride
    public long play(float volume, float pitch, float pan) {
        return sound < 0 ? -1 : JvmCall.playSound(sound, volume, pitch, pan, false);
    }

    @Override
    @BridgeOverride
    public long loop() {
        return loop(1f);
    }

    @Override
    @BridgeOverride
    public long loop(float volume) {
        return loop(volume, 1f, 0f);
    }

    @Override
    @BridgeOverride
    public long loop(float volume, float pitch, float pan) {
        return sound < 0 ? -1 : JvmCall.playSound(sound, volume, pitch, pan, true);
    }

    @Override
    @BridgeOverride
    public void stop() {
        if (sound >= 0)
            JvmCall.stopSound(sound);
    }

    @Override
    @BridgeOverride
    public void pause() {
        if (sound >= 0)
            JvmCall.pauseSound();
    }

    @Override
    @BridgeOverride
    public void resume() {
        if (sound >= 0)
            JvmCall.resumeSound();
    }

    @Override
    @BridgeOverride
    public void dispose() {
        if (sound >= 0)
            JvmCall.disposeSound(sound);
    }

    @Override
    @BridgeOverride
    public void stop(long soundId) {
        if (sound >= 0)
            JvmCall.stopSoundAt(sound, (int)soundId);
    }

    @Override
    @BridgeOverride
    public void pause(long soundId) {
        if (sound >= 0)
            JvmCall.pauseSoundAt((int)soundId);
    }

    @Override
    @BridgeOverride
    public void resume(long soundId) {
        if (sound >= 0)
            JvmCall.resumeSoundAt((int)soundId);
    }

    @Override
    @BridgeOverride
    public void setLooping(long soundId, boolean looping) {
        if (sound >= 0)
            JvmCall.setSoundLooping((int)soundId, looping);
    }

    @Override
    @BridgeOverride
    public void setPitch(long soundId, float pitch) {
        if (sound >= 0)
            JvmCall.setSoundPitch((int)soundId, pitch);
    }

    @Override
    @BridgeOverride
    public void setVolume(long soundId, float volume) {
        if (sound >= 0)
            JvmCall.setSoundVolume((int)soundId, volume);
    }

    @Override
    @BridgeOverride
    public void setPan(long soundId, float pan, float volume) {
        if (sound >= 0)
            JvmCall.setSoundPan((int)soundId, pan, volume);
    }
}
