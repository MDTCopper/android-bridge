package copper.bridge.v1;

import copper.bridge.annotation.BridgeOverride;

import com.badlogic.gdx.audio.AudioRecorder;

/** A recorder that returns silence; the bridge has no microphone caller. */
public class BridgeAudioRecorder implements AudioRecorder {

    @Override
    @BridgeOverride
    public void read(short[] samples, int offset, int numSamples) {
    }

    @Override
    @BridgeOverride
    public void dispose() {
    }
}
