package copper.bridge.v1;

import copper.bridge.annotation.BridgeOverride;

import com.badlogic.gdx.audio.Music;

/** uCore's music path is disabled; this keeps the interface contract without claiming playback. */
public class BridgeMusic implements Music {
    private float volume = 1f;

    @Override
    @BridgeOverride
    public void play() {
    }

    @Override
    @BridgeOverride
    public void pause() {
    }

    @Override
    @BridgeOverride
    public void stop() {
    }

    @Override
    @BridgeOverride
    public boolean isPlaying() {
        return false;
    }

    @Override
    @BridgeOverride
    public void setLooping(boolean isLooping) {
    }

    @Override
    @BridgeOverride
    public boolean isLooping() {
        return false;
    }

    @Override
    @BridgeOverride
    public void setVolume(float volume) {
        this.volume = volume;
    }

    @Override
    @BridgeOverride
    public float getVolume() {
        return volume;
    }

    @Override
    @BridgeOverride
    public void setPan(float pan, float volume) {
    }

    @Override
    @BridgeOverride
    public void setPosition(float position) {
    }

    @Override
    @BridgeOverride
    public float getPosition() {
        return 0f;
    }

    @Override
    @BridgeOverride
    public void dispose() {
    }

    @Override
    @BridgeOverride
    public void setOnCompletionListener(OnCompletionListener listener) {
    }
}
