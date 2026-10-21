package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.exception.ForbiddenOperationException;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.MentorshipResourceEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaMentorshipResourceRepository;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaOfferingRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import org.springframework.http.*;
import org.springframework.web.multipart.MultipartFile;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.model.MentorshipStatus;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaMentorshipAssignmentRepository;

@RestController
@RequestMapping("/api/mentorship-resources")
public class MentorshipResourceController {
    private final JpaMentorshipResourceRepository resources;
    private final JpaOfferingRepository offerings;
    private final UserRepositoryPort users;
    private final JpaMentorshipAssignmentRepository assignments;
    public MentorshipResourceController(JpaMentorshipResourceRepository resources, JpaOfferingRepository offerings, UserRepositoryPort users, JpaMentorshipAssignmentRepository assignments) { this.assignments = assignments; this.resources = resources; this.offerings = offerings; this.users = users; }

    @GetMapping("/offerings/{offeringId}")
    public List<MentorshipResourceEntity> list(@PathVariable UUID offeringId, Authentication auth) { ownedOffering(offeringId, auth); return resources.findByOfferingIdOrderByCreatedAtAsc(offeringId); }

    @PostMapping("/offerings/{offeringId}")
    @ResponseStatus(HttpStatus.CREATED)
    public MentorshipResourceEntity create(@PathVariable UUID offeringId, @Valid @RequestBody ResourceRequest request, Authentication auth) {
        ownedOffering(offeringId, auth);
        validateUrl(request.url());
        return resources.save(new MentorshipResourceEntity(UUID.randomUUID(), offeringId, request.title().trim(), request.resourceType(), request.url().trim()));
    }

    @PutMapping("/{id}")
    public MentorshipResourceEntity update(@PathVariable UUID id, @Valid @RequestBody ResourceRequest request, Authentication auth) {
        var resource = resources.findById(id).orElseThrow(() -> new DomainNotFoundException("Recurso no encontrado"));
        ownedOffering(resource.getOfferingId(), auth);
        if (!(resource.getFileName() != null && "DOCUMENT".equals(request.resourceType()) && resource.getUrl().equals(request.url()))) validateUrl(request.url());
        resource.update(request.title().trim(), request.resourceType(), request.url().trim());
        return resources.save(resource);
    }

    @PostMapping(value = "/offerings/{offeringId}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MentorshipResourceEntity upload(@PathVariable UUID offeringId, @RequestParam String title, @RequestPart MultipartFile file, Authentication auth) throws java.io.IOException {
        ownedOffering(offeringId, auth);
        var resource = new MentorshipResourceEntity(UUID.randomUUID(), offeringId, title.trim(), "DOCUMENT", "");
        attach(resource, title, file);
        return resources.save(resource);
    }

    @PutMapping(value = "/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MentorshipResourceEntity replaceFile(@PathVariable UUID id, @RequestParam String title, @RequestPart MultipartFile file, Authentication auth) throws java.io.IOException {
        var resource = resources.findById(id).orElseThrow(() -> new DomainNotFoundException("Recurso no encontrado"));
        ownedOffering(resource.getOfferingId(), auth);
        attach(resource, title, file);
        return resources.save(resource);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> download(@PathVariable UUID id, Authentication auth) {
        var resource = resources.findById(id).orElseThrow(() -> new DomainNotFoundException("Recurso no encontrado"));
        var user = users.findByEmail(auth.getName()).orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado"));
        if (user.role() == Role.ADMIN || user.role() == Role.PROVIDER) ownedOffering(resource.getOfferingId(), auth);
        else {
            Instant now = Instant.now();
            boolean allowed = assignments.findByCustomerIdOrderByAccessStartsAtDesc(user.id()).stream().anyMatch(a ->
                a.getOfferingId().equals(resource.getOfferingId()) && !now.isBefore(a.getAccessStartsAt()) && now.isBefore(a.getAccessEndsAt()) &&
                a.getStatus() != MentorshipStatus.COMPLETED && a.getStatus() != MentorshipStatus.CANCELLED && a.getStatus() != MentorshipStatus.NO_SHOW);
            if (!allowed) throw new ForbiddenOperationException("Los recursos no están disponibles para esta mentoría");
        }
        if (resource.getFileData() == null) throw new DomainNotFoundException("Archivo no encontrado");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(resource.getFileName(), StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options", "nosniff").cacheControl(CacheControl.noStore()).body(resource.getFileData());
    }

    private void attach(MentorshipResourceEntity resource, String title, MultipartFile file) throws java.io.IOException {
        if (title.isBlank() || title.trim().length() > 160) throw new BusinessRuleException("Escribe un título de hasta 160 caracteres");
        if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024) throw new BusinessRuleException("El archivo debe tener contenido y pesar máximo 10 MB");
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("archivo").replaceAll("[\\\\/\\r\\n]", "_");
        if (name.length() > 255) name = name.substring(name.length() - 255);
        resource.update(title.trim(), "DOCUMENT", resource.getUrl());
        resource.attach(file.getBytes(), name);
    }

    private void validateUrl(String url) {
        try {
            var uri = java.net.URI.create(url.trim());
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) { throw new BusinessRuleException("Escribe un enlace válido que comience con http:// o https://"); }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, Authentication auth) {
        var resource = resources.findById(id).orElseThrow(() -> new DomainNotFoundException("Recurso no encontrado"));
        ownedOffering(resource.getOfferingId(), auth); resources.delete(resource);
    }

    private void ownedOffering(UUID offeringId, Authentication auth) {
        var offering = offerings.findById(offeringId).orElseThrow(() -> new DomainNotFoundException("Servicio no encontrado"));
        var user = users.findByEmail(auth.getName()).orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado"));
        if (user.role() != Role.ADMIN && (user.role() != Role.PROVIDER || !offering.getProviderId().equals(user.id()))) throw new ForbiddenOperationException("No puedes administrar recursos de este servicio");
    }

    public record ResourceRequest(@NotBlank @Size(max = 160) String title, @NotBlank @Pattern(regexp = "VIDEO|DOCUMENT|LINK") String resourceType, @NotBlank @Size(max = 1500) String url) { }
}
