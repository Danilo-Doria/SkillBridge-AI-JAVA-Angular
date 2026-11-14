package com.riwi.skillbridge.ai.application.port.in;

import com.riwi.skillbridge.ai.application.port.out.TextToSpeechPort;

public interface SynthesizeSpeechUseCase {
    TextToSpeechPort.AudioContent synthesize(String text);
}
