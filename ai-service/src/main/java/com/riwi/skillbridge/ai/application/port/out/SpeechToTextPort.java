package com.riwi.skillbridge.ai.application.port.out;

public interface SpeechToTextPort {
    String transcribe(byte[] audio, String contentType, String fileName);
}
