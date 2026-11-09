package copper.bridge.v7;

import copper.bridge.annotation.BridgeInterface;

import arc.audio.*;
import arc.files.*;

import copper.bridge.annotation.CompatRange;
import copper.bridge.gen.*;
import copper.bridge.jvm.MusicListeners;

/**
 * One track of the abstract arc.Audio. The loop flag and the volume are kept here because MediaPlayer has no getter
 * for either, and completion arrives as a bus event: the player answers on a thread of its own, the listener belongs
 * to the game's thread.
 */
@CompatRange(ceiling = "v112")
final class LegacyMusic implements Music {
    private final int music;
    private boolean looping;
    private float volume = 1f;
    private OnCompletionListener listener;

    LegacyMusic(Fi file) {
        this.music = JvmCall.newMusic(file.name(), file.readBytes());
        if (music > 0)
            MusicListeners.put(music, this::completed);
    }

    @BridgeInterface
    public void play() {
        if (music > 0)
            JvmCall.playMusic(music);
    }

    @BridgeInterface
    public void pause() {
        if (music > 0)
            JvmCall.pauseMusic(music);
    }

    @BridgeInterface
    public void stop() {
        if (music > 0)
            JvmCall.stopMusic(music);
    }

    @BridgeInterface
    public boolean isPlaying() {
        return music > 0 && JvmCall.isMusicPlaying(music);
    }

    @BridgeInterface
    public boolean isLooping() {
        return looping;
    }

    @BridgeInterface
    public void setLooping(boolean looping) {
        this.looping = looping;
        if (music > 0)
            JvmCall.setMusicLooping(music, looping);
    }

    @BridgeInterface
    public float getVolume() {
        return volume;
    }

    @BridgeInterface
    public void setVolume(float volume) {
        this.volume = volume;
        if (music > 0)
            JvmCall.setMusicVolume(music, volume);
    }

    @BridgeInterface
    public void setPan(float pan, float volume) {
        this.volume = volume;
        if (music > 0)
            JvmCall.setMusicPan(music, pan, volume);
    }

    @BridgeInterface
    public float getPosition() {
        return music <= 0 ? 0f : JvmCall.getMusicPosition(music);
    }

    @BridgeInterface
    public void setPosition(float position) {
        if (music > 0)
            JvmCall.setMusicPosition(music, position);
    }

    @BridgeInterface
    public void dispose() {
        if (music > 0) {
            MusicListeners.remove(music);
            JvmCall.disposeMusic(music);
        }
    }

    @BridgeInterface
    public void setCompletionListener(OnCompletionListener listener) {
        this.listener = listener;
    }

    /** Runs on the game's thread, delivered by the branch's event. */
    private void completed() {
        OnCompletionListener current = listener;
        if (current != null)
            current.complete(this);
    }
}
