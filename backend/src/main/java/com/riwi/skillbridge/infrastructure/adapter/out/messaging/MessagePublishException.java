package com.riwi.skillbridge.infrastructure.adapter.out.messaging;

/** El broker no confirmó la publicación de un evento que no se puede perder. */
public class MessagePublishException extends RuntimeException {

    public MessagePublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
