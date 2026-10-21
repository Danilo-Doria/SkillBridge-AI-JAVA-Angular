package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.domain.model.MentorshipMode;
import com.riwi.skillbridge.domain.model.MentorshipStatus;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.BookingEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.MentorshipAssignmentEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaBookingRepository;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaMentorshipAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.util.UUID;

/** Crea una mentoría única para cada reserva pagada, incluidas las confirmadas antes de esta funcionalidad. */
@Service
public class MentorshipProvisioningService {
    private static final Duration DEFAULT_ACCESS_WINDOW = Duration.ofHours(2);
    private final JpaBookingRepository bookings; private final JpaMentorshipAssignmentRepository assignments;
    public MentorshipProvisioningService(JpaBookingRepository bookings, JpaMentorshipAssignmentRepository assignments) { this.bookings=bookings; this.assignments=assignments; }
    @Transactional public void provisionForPaidBooking(UUID bookingId) { bookings.findById(bookingId).ifPresent(this::provision); }
    @Transactional public void provisionPaidBookingsForCustomer(UUID customerId) { bookings.findByCustomerIdOrderByScheduledAtDesc(customerId).stream().filter(b -> b.getStatus() == BookingStatus.CONFIRMED).forEach(this::provision); }
    private void provision(BookingEntity booking) { if (booking.getStatus() != BookingStatus.CONFIRMED || assignments.findByBookingId(booking.getId()).isPresent()) return; assignments.save(new MentorshipAssignmentEntity(UUID.randomUUID(), booking.getId(), booking.getCustomerId(), booking.getOfferingId(), MentorshipMode.ASYNCHRONOUS, MentorshipStatus.SCHEDULED, booking.getScheduledAt(), booking.getScheduledAt().plus(DEFAULT_ACCESS_WINDOW), 0, null)); }
}
