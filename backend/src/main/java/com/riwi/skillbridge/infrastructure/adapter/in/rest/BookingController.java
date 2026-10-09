package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.CancelBookingCommand;
import com.riwi.skillbridge.application.port.in.CancelBookingUseCase;
import com.riwi.skillbridge.application.port.in.CreateBookingUseCase;
import com.riwi.skillbridge.application.port.in.ListCustomerBookingsUseCase;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.CreateBookingRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {
    private final CreateBookingUseCase useCase;
    private final ListCustomerBookingsUseCase listCustomerBookingsUseCase;
    private final CancelBookingUseCase cancelBookingUseCase;

    public BookingController(CreateBookingUseCase useCase,
                             ListCustomerBookingsUseCase listCustomerBookingsUseCase,
                             CancelBookingUseCase cancelBookingUseCase) {
        this.useCase = useCase;
        this.listCustomerBookingsUseCase = listCustomerBookingsUseCase;
        this.cancelBookingUseCase = cancelBookingUseCase;
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Booking create(@Valid @RequestBody CreateBookingRequest request, Authentication authentication,
                          @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return useCase.create(request.offeringId(), request.scheduledAt(), authentication.getName(), idempotencyKey);
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    public List<Booking> getBookingsById(Authentication authentication) {
        String userEmail = authentication.getName(); // Extrae el email del token
        return listCustomerBookingsUseCase.bookingsList(userEmail);
    }

    @PatchMapping("/{bookingId}/cancel")
    public Booking cancel(@PathVariable java.util.UUID bookingId, Authentication authentication) {
        return cancelBookingUseCase.cancel(new CancelBookingCommand(bookingId, authentication.getName()));
    }
}
