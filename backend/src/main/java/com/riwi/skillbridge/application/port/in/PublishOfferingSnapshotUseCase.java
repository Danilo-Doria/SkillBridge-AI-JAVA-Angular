package com.riwi.skillbridge.application.port.in;

/** Publica el estado de todas las ofertas (activas e inactivas) como {@code OfferingUpserted} (arranque en frío de read models). */
public interface PublishOfferingSnapshotUseCase {
    /** @return número de ofertas publicadas */
    int publishActiveSnapshot();
}
