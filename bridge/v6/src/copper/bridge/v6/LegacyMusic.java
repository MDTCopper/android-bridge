package copper.bridge.v6;

import copper.bridge.annotation.BridgeOverride;

import arc.audio.*;
import arc.files.*;

import copper.bridge.gen.*;

/**
 * One track of the abstract arc.Audio. The loop flag and the volume are kept here because MediaPlayer has no getter
 * for either, and completion arrives as a bus event: the player answers on a thread of its own, the listener belongs
 * to the game's thread.
 */
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

    @BridgeOverride
    public void play() {
        if (music > 0)
            JvmCall.playMusic(music);
    }

    @BridgeOverride
    public void pause() {
        if (music > 0)
            JvmCall.pauseMusic(music);
    }

    @BridgeOverride
    public void stop() {
        if (music > 0)
            JvmCall.stopMusic(music);
    }

    @BridgeOverride
    public boolean isPlaying() {
        return music > 0 && JvmCall.isMusicPlaying(music);
    }

    @BridgeOverride
    public boolean isLooping() {
        return looping;
    }

    @BridgeOverride
    public void setLooping(boolean looping) {
        this.looping = looping;
        if (music > 0)
            JvmCall.setMusicLooping(music, looping);
    }

    @BridgeOverride
    public float getVolume() {
        return volume;
    }

    @BridgeOverride
    public void setVolume(float volume) {
        this.volume = volume;
        if (music > 0)
            JvmCall.setMusicVolume(music, volume);
    }

    @BridgeOverride
    public void setPan(float pan, float volume) {
        this.volume = volume;
        if (music > 0)
            JvmCall.setMusicPan(music, pan, volume);
    }

    @BridgeOverride
    public float getPosition() {
        return music <= 0 ? 0f : JvmCall.getMusicPosition(music);
    }

    @BridgeOverride
    public void setPosition(float position) {
        if (music > 0)
            JvmCall.setMusicPosition(music, position);
    }

    @BridgeOverride
    public void dispose() {
        if (music > 0) {
            MusicListeners.remove(music);
            JvmCall.disposeMusic(music);
        }
    }

    @BridgeOverride
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
