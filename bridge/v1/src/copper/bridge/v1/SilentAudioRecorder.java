package copper.bridge.v1;

import io.anuke.arc.audio.AudioRecorder;

/** Nothing captures raw PCM; a silent recorder keeps the non-null contract. */
public class SilentAudioRecorder implements AudioRecorder {
    @Override
    public void read(short[] samples, int offset, int numSamples) {
        java.util.Arrays.fill(samples, offset, offset + numSamples, (short)0);
    }

    @Override
    public void dispose() {
    }
}
