package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.Offering;
import java.util.List;
import java.util.Optional;

public interface OfferingCachePort {
    Optional<List<Offering>> getActiveOfferings();
    void putActiveOfferings(List<Offering> offerings);
    void evictActiveOfferings();
}
