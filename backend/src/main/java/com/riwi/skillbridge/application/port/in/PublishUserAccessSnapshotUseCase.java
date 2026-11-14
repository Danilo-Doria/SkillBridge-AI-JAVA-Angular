package com.riwi.skillbridge.application.port.in;

/** Publica un {@code UserAccessChanged} por cada usuario no activo (arranque en frío de las revocaciones). */
public interface PublishUserAccessSnapshotUseCase {
    /** @return número de eventos publicados */
    int publishRestrictedUsersSnapshot();
}
