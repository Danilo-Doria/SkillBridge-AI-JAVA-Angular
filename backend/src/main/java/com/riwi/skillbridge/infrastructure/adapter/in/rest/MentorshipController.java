package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.exception.ForbiddenOperationException;
import com.riwi.skillbridge.domain.model.*;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.MentorshipAssignmentRequest;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.ProgressRequest;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.MentorshipAssignmentEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.MentorshipResourceEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaBookingRepository;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaMentorshipAssignmentRepository;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaOfferingRepository;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaMentorshipResourceRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;

/** Protección de acceso en servidor para el aula y para la sesión en vivo. */
@RestController
@RequestMapping("/api/mentorships")
public class MentorshipController {
    private final JpaMentorshipAssignmentRepository assignments;
    private final JpaBookingRepository bookings;
    private final JpaOfferingRepository offerings;
    private final JpaMentorshipResourceRepository resources;
    private final UserRepositoryPort users;
    public MentorshipController(JpaMentorshipAssignmentRepository assignments, JpaBookingRepository bookings, JpaOfferingRepository offerings, JpaMentorshipResourceRepository resources, UserRepositoryPort users) { this.assignments=assignments; this.bookings=bookings; this.offerings=offerings; this.resources=resources; this.users=users; }

    @GetMapping("/me")
    public List<MentorshipAssignmentEntity> mine(Authentication auth) {
        return assignments.findByCustomerIdOrderByAccessStartsAtDesc(currentUser(auth).id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public MentorshipAssignmentEntity assign(@Valid @RequestBody MentorshipAssignmentRequest request, Authentication auth) {
        requireStaff(auth);
        if (!request.accessEndsAt().isAfter(request.accessStartsAt())) throw new BusinessRuleException("La fecha final debe ser posterior a la fecha inicial");
        if (assignments.findByBookingId(request.bookingId()).isPresent()) throw new BusinessRuleException("Esta reserva ya tiene una mentoría asignada");
        var booking = bookings.findById(request.bookingId()).orElseThrow(() -> new DomainNotFoundException("Reserva no encontrada"));
        if (booking.getStatus() != BookingStatus.CONFIRMED) throw new BusinessRuleException("Solo una reserva con pago aprobado puede recibir una mentoría");
        UserAccount actor = currentUser(auth);
        if (actor.role() == Role.PROVIDER && !offerings.findById(booking.getOfferingId()).orElseThrow(() -> new DomainNotFoundException("Servicio no encontrado")).getProviderId().equals(actor.id())) throw new ForbiddenOperationException("No puedes asignar mentorías de otro Provider");
        return assignments.save(new MentorshipAssignmentEntity(UUID.randomUUID(), booking.getId(), booking.getCustomerId(), booking.getOfferingId(), request.mode(), MentorshipStatus.SCHEDULED, request.accessStartsAt(), request.accessEndsAt(), 0, request.liveRoomUrl()));
    }

    @PatchMapping("/{id}/progress")
    @Transactional
    public MentorshipAssignmentEntity updateProgress(@PathVariable UUID id, @Valid @RequestBody ProgressRequest request, Authentication auth) {
        var assignment = owned(id, auth);
        Instant now = Instant.now();
        if (now.isBefore(assignment.getAccessStartsAt()) || !now.isBefore(assignment.getAccessEndsAt())) throw new BusinessRuleException("La mentoría no está disponible en este momento");
        if (assignment.getStatus() == MentorshipStatus.COMPLETED) throw new BusinessRuleException("La mentoría completada no se puede reiniciar con el mismo pago");
        if (assignment.getStatus() == MentorshipStatus.CANCELLED || assignment.getStatus() == MentorshipStatus.NO_SHOW) throw new BusinessRuleException("La mentoría no está habilitada");
        MentorshipStatus state = request.progressPercent() == 100 ? MentorshipStatus.COMPLETED : MentorshipStatus.IN_PROGRESS;
        assignment.update(state, request.progressPercent(), state == MentorshipStatus.COMPLETED ? now : null);
        return assignments.save(assignment);
    }

    @GetMapping("/{id}/access")
    public Map<String,Object> access(@PathVariable UUID id, Authentication auth) {
        var assignment = owned(id, auth); Instant now = Instant.now();
        if (assignment.getStatus() == MentorshipStatus.COMPLETED || assignment.getStatus() == MentorshipStatus.CANCELLED || assignment.getStatus() == MentorshipStatus.NO_SHOW || !now.isBefore(assignment.getAccessEndsAt())) throw new ForbiddenOperationException("El acceso a esta mentoría ya no está habilitado");
        if (now.isBefore(assignment.getAccessStartsAt())) throw new ForbiddenOperationException("La mentoría aún no está disponible");
        return Map.of("assignmentId", assignment.getId(), "mode", assignment.getMode(), "accessEndsAt", assignment.getAccessEndsAt(), "liveRoomUrl", assignment.getMode() == MentorshipMode.LIVE && assignment.getLiveRoomUrl() != null ? assignment.getLiveRoomUrl() : "");
    }
    @GetMapping("/{id}/resources")
    public List<MentorshipResourceEntity> resources(@PathVariable UUID id, Authentication auth) {
        var assignment = owned(id, auth); Instant now = Instant.now();
        if (assignment.getStatus() == MentorshipStatus.COMPLETED || assignment.getStatus() == MentorshipStatus.CANCELLED || assignment.getStatus() == MentorshipStatus.NO_SHOW || now.isBefore(assignment.getAccessStartsAt()) || !now.isBefore(assignment.getAccessEndsAt())) throw new ForbiddenOperationException("Los recursos no están disponibles para esta mentoría");
        return resources.findByOfferingIdOrderByCreatedAtAsc(assignment.getOfferingId());
    }
    private MentorshipAssignmentEntity owned(UUID id, Authentication auth) { var a=assignments.findById(id).orElseThrow(() -> new DomainNotFoundException("Mentoría no encontrada")); if (!a.getCustomerId().equals(currentUser(auth).id())) throw new DomainNotFoundException("Mentoría no encontrada"); return a; }
    private UserAccount currentUser(Authentication auth) { return users.findByEmail(auth.getName()).orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado")); }
    private void requireStaff(Authentication auth) { if (auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_PROVIDER") || a.getAuthority().equals("ROLE_ADMIN"))) throw new ForbiddenOperationException("Solo un Provider o Admin puede asignar mentorías"); }
}
