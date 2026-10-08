package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.CreateBookingUseCase;
import com.riwi.skillbridge.application.port.in.ListCustomerBookingsUseCase;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.CreateBookingRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {
    private final CreateBookingUseCase useCase;
    private  final ListCustomerBookingsUseCase listCustomerBookingsUseCase;

    public BookingController(CreateBookingUseCase useCase, ListCustomerBookingsUseCase listCustomerBookingsUseCase) {
        this.useCase = useCase;
        this.listCustomerBookingsUseCase = listCustomerBookingsUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Booking create(@Valid @RequestBody CreateBookingRequest request, Authentication authentication) {
        return useCase.create(request.offeringId(), request.scheduledAt(), authentication.getName());
    }

    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    public List<Booking> getBookingsById(Authentication authentication) {
        String userEmail = authentication.getName(); // Extrae el email del token
        return listCustomerBookingsUseCase.bookingsList(userEmail);
    }
    // Expone el endpoint para cancelar reservas (permite reembolsos si está a tiempo)
    @PostMapping("/cancel")
    @ResponseStatus(HttpStatus.OK)
    public Booking cancelBooking(@RequestBody com.riwi.skillbridge.application.port.in.CancelBookingCommand command) {
        return ((com.riwi.skillbridge.application.service.BookingService) useCase).cancel(command);
    }
}