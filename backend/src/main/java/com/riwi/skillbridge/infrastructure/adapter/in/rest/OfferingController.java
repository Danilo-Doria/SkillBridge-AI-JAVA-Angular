package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.ListOfferingsUseCase;
import com.riwi.skillbridge.domain.model.Offering;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/offerings")
public class OfferingController {
    private final ListOfferingsUseCase useCase;
    public OfferingController(ListOfferingsUseCase useCase) { this.useCase = useCase; }

    @GetMapping
    public List<Offering> list() { return useCase.listActive(); }
}
