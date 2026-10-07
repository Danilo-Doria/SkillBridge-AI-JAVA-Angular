import re

with open('src/main/java/com/riwi/skillbridge/application/service/BookingService.java', 'r') as f:
    code = f.read()

# Add imports
imports = """
import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.application.port.out.event.BookingCreatedPayload;
import com.riwi.skillbridge.application.port.out.event.BookingCancelledPayload;
import com.riwi.skillbridge.application.common.CorrelationIdHolder;
"""
code = re.sub(r'(import org\.springframework\.stereotype\.Service;)', r'\1\n' + imports, code)

# Add port dependency
port_dep = "    private final BookingEventPublisherPort eventPublisher;"
code = re.sub(r'(private final NotificationPublisherPort notificationPublisher;)', r'\1\n' + port_dep, code)

# In create()
create_event = """
        String correlationId = CorrelationIdHolder.get() != null ? CorrelationIdHolder.get() : UUID.randomUUID().toString();
        BookingCreatedPayload payload = new BookingCreatedPayload(saved.id(), saved.offeringId(), saved.customerId(), saved.scheduledAt(), saved.status().name());
        BusinessEvent<BookingCreatedPayload> event = new BusinessEvent<>(
            UUID.randomUUID(), "BookingCreated", saved.id().toString(), "Booking", Instant.now(), correlationId, 1, payload);
        eventPublisher.publish(event);
        return saved;
"""
code = re.sub(r'return saved;\n\s*}', create_event + '    }', code, count=1)

# In cancel()
cancel_event = """
        Booking finalBooking = cancelled == booking ? booking : bookingRepository.save(cancelled);
        String correlationId = CorrelationIdHolder.get() != null ? CorrelationIdHolder.get() : UUID.randomUUID().toString();
        BookingCancelledPayload payload = new BookingCancelledPayload(finalBooking.id(), finalBooking.customerId(), finalBooking.status().name());
        BusinessEvent<BookingCancelledPayload> event = new BusinessEvent<>(
            UUID.randomUUID(), "BookingCancelled", finalBooking.id().toString(), "Booking", Instant.now(), correlationId, 1, payload);
        eventPublisher.publish(event);
        return finalBooking;
"""
code = re.sub(r'return bookingRepository\.save\(cancelled\);\n\s*}', cancel_event + '    }', code)
code = re.sub(r'return booking;\n\s*}', cancel_event + '    }', code)

with open('src/main/java/com/riwi/skillbridge/application/service/BookingService.java', 'w') as f:
    f.write(code)

