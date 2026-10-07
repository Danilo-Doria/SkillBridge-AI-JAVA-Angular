import re

with open('src/main/java/com/riwi/skillbridge/application/service/BookingService.java', 'r') as f:
    code = f.read()

# Replace the entire cancel method
new_cancel = """    @Override
    @Transactional
    public Booking cancel(CancelBookingCommand command) {
        Booking booking = bookingRepository.findById(command.bookingId())
                .orElseThrow(() -> new DomainNotFoundException("Reserva no encontrada"));

        UUID customerId = userAccountPort.findIdByEmail(command.customerEmail())
                .orElseThrow(() -> new DomainNotFoundException("Reserva no encontrada"));
        if (!booking.customerId().equals(customerId)) {
            throw new DomainNotFoundException("Reserva no encontrada");
        }

        Booking cancelled = booking.cancel();
        Booking finalBooking;
        if (cancelled == booking) {
            finalBooking = booking;
        } else {
            cancellationPolicy.validate(booking);
            finalBooking = bookingRepository.save(cancelled);
        }
        
        String correlationId = CorrelationIdHolder.get() != null ? CorrelationIdHolder.get() : UUID.randomUUID().toString();
        BookingCancelledPayload payload = new BookingCancelledPayload(finalBooking.id(), finalBooking.customerId(), finalBooking.status().name());
        BusinessEvent<BookingCancelledPayload> event = new BusinessEvent<>(
            UUID.randomUUID(), "BookingCancelled", finalBooking.id().toString(), "Booking", Instant.now(), correlationId, 1, payload);
        eventPublisher.publish(event);
        
        return finalBooking;
    }"""

code = re.sub(r'    @Override\s+@Transactional\s+public Booking cancel\(CancelBookingCommand command\) \{.*\}', new_cancel, code, flags=re.DOTALL)

with open('src/main/java/com/riwi/skillbridge/application/service/BookingService.java', 'w') as f:
    f.write(code)
