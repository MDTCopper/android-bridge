package copper.bridge.v5;

import arc.audio.*;
import arc.files.*;

import copper.bridge.gen.*;
import copper.bridge.jvm.MusicListeners;

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

    @Override
    public void play() {
        if (music > 0)
            JvmCall.playMusic(music);
    }

    @Override
    public void pause() {
        if (music > 0)
            JvmCall.pauseMusic(music);
    }

    @Override
    public void stop() {
        if (music > 0)
            JvmCall.stopMusic(music);
    }

    @Override
    public boolean isPlaying() {
        return music > 0 && JvmCall.isMusicPlaying(music);
    }

    @Override
    public boolean isLooping() {
        return looping;
    }

    @Override
    public void setLooping(boolean looping) {
        this.looping = looping;
        if (music > 0)
            JvmCall.setMusicLooping(music, looping);
    }

    @Override
    public float getVolume() {
        return volume;
    }

    @Override
    public void setVolume(float volume) {
        this.volume = volume;
        if (music > 0)
            JvmCall.setMusicVolume(music, volume);
    }

    @Override
    public void setPan(float pan, float volume) {
        this.volume = volume;
        if (music > 0)
            JvmCall.setMusicPan(music, pan, volume);
    }

    @Override
    public float getPosition() {
        return music <= 0 ? 0f : JvmCall.getMusicPosition(music);
    }

    @Override
    public void setPosition(float position) {
        if (music > 0)
            JvmCall.setMusicPosition(music, position);
    }

    @Override
    public void dispose() {
        if (music > 0) {
            MusicListeners.remove(music);
            JvmCall.disposeMusic(music);
        }
    }

    @Override
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
