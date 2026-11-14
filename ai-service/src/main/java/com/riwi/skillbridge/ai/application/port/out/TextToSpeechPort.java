package com.riwi.skillbridge.ai.application.port.out;

public interface TextToSpeechPort {
    AudioContent synthesize(String text);

    record AudioContent(byte[] bytes, String contentType) {}
}
