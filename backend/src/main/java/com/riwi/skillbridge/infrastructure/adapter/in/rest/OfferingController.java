package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.*;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.CreateOfferingRequest;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.OfferingResponse;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.UpdateOfferingRequest;
import com.riwi.skillbridge.infrastructure.security.CurrentActorResolver;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/offerings")
public class OfferingController {
    private final ListOfferingsUseCase listActive;
    private final ListMyOfferingsUseCase listMine;
    private final CreateOfferingUseCase create;
    private final UpdateOfferingUseCase update;
    private final DeactivateOfferingUseCase deactivate;
    private final ActivateOfferingUseCase activate;
    private final CurrentActorResolver actors;

    public OfferingController(ListOfferingsUseCase listActive, ListMyOfferingsUseCase listMine,
                              CreateOfferingUseCase create, UpdateOfferingUseCase update,
                              DeactivateOfferingUseCase deactivate, ActivateOfferingUseCase activate,
                              CurrentActorResolver actors) {
        this.listActive = listActive;
        this.listMine = listMine;
        this.create = create;
        this.update = update;
        this.deactivate = deactivate;
        this.activate = activate;
        this.actors = actors;
    }


    @GetMapping
    public List<OfferingResponse> list() {
        return listActive.listActive().stream().map(OfferingResponse::from).toList();
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('PROVIDER')")
    public List<OfferingResponse> mine(Authentication auth) {
        return listMine.listMine(actors.from(auth)).stream().map(OfferingResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public OfferingResponse create(@Valid @RequestBody CreateOfferingRequest req, Authentication auth) {
        var command = new CreateOfferingUseCase.Command(req.providerId(), req.title(),
            req.description(), req.category(), req.price());
        return OfferingResponse.from(create.create(actors.from(auth), command));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public OfferingResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateOfferingRequest req,
                                   Authentication auth) {
        var command = new UpdateOfferingUseCase.Command(req.title(), req.description(),
            req.category(), req.price());
        return OfferingResponse.from(update.update(actors.from(auth), id, command));
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public void deactivate(@PathVariable UUID id, Authentication auth) {
        deactivate.deactivate(actors.from(auth), id);
    }

    @PostMapping("/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public void activate(@PathVariable UUID id, Authentication auth) {
        activate.activate(actors.from(auth), id);
    }
}
