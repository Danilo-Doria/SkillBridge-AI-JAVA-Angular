package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.domain.exception.*;
import com.riwi.skillbridge.domain.model.*;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.*;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MentorshipResourceControllerTest {
    final JpaMentorshipResourceRepository resources = mock(JpaMentorshipResourceRepository.class);
    final JpaOfferingRepository offerings = mock(JpaOfferingRepository.class);
    final UserRepositoryPort users = mock(UserRepositoryPort.class);
    final JpaMentorshipAssignmentRepository assignments = mock(JpaMentorshipAssignmentRepository.class);
    final Authentication auth = mock(Authentication.class);
    final UUID offeringId = UUID.randomUUID(), userId = UUID.randomUUID();
    final MentorshipResourceController controller = new MentorshipResourceController(resources, offerings, users, assignments);

    @BeforeEach void setup() {
        when(auth.getName()).thenReturn("user@test.com");
        role(Role.PROVIDER);
        when(offerings.findById(offeringId)).thenReturn(Optional.of(new OfferingEntity(offeringId, userId, "Service", "Description", "Category", BigDecimal.ONE, true)));
        when(resources.save(any())).thenAnswer(call -> call.getArgument(0));
    }
    void role(Role role) { when(users.findByEmail("user@test.com")).thenReturn(Optional.of(new UserAccount(userId, "User", "user@test.com", "hash", role))); }
    MentorshipResourceEntity file() throws Exception {
        var resource = controller.upload(offeringId, "Presentation", new MockMultipartFile("file", "slides.pptx", "application/octet-stream", new byte[]{1,2,3}), auth);
        when(resources.findById(resource.getId())).thenReturn(Optional.of(resource));
        return resource;
    }

    @Test void uploadsArbitraryFileAndDownloadsAsAttachment() throws Exception {
        var resource = file();
        var response = controller.download(resource.getId(), auth);
        assertEquals("slides.pptx", resource.getFileName());
        assertArrayEquals(new byte[]{1,2,3}, response.getBody());
        assertTrue(response.getHeaders().getFirst("Content-Disposition").startsWith("attachment;"));
    }

    @Test void editsSameResourceAndRetainsFileWhenOnlyTitleChanges() throws Exception {
        var resource = file();
        var updated = controller.update(resource.getId(), new MentorshipResourceController.ResourceRequest("New title", "DOCUMENT", resource.getUrl()), auth);
        assertEquals(resource.getId(), updated.getId());
        assertEquals("New title", updated.getTitle());
        assertArrayEquals(new byte[]{1,2,3}, updated.getFileData());
        controller.update(resource.getId(), new MentorshipResourceController.ResourceRequest("Video", "VIDEO", "https://www.youtube.com/watch?v=example"), auth);
        assertNull(resource.getFileData());
    }

    @Test void rejectsUnsafeLinksAndEmptyFiles() {
        assertThrows(BusinessRuleException.class, () -> controller.create(offeringId, new MentorshipResourceController.ResourceRequest("Bad link", "LINK", "javascript:alert(1)"), auth));
        assertThrows(BusinessRuleException.class, () -> controller.upload(offeringId, "Empty", new MockMultipartFile("file", new byte[0]), auth));
        verify(resources, never()).save(any());
    }

    @Test void anotherProviderCannotModifyResources() {
        when(offerings.findById(offeringId)).thenReturn(Optional.of(new OfferingEntity(offeringId, UUID.randomUUID(), "Other", "", "", BigDecimal.ONE, true)));
        assertThrows(ForbiddenOperationException.class, () -> controller.create(offeringId, new MentorshipResourceController.ResourceRequest("Link", "LINK", "https://example.com"), auth));
        verify(resources, never()).save(any());
    }

    @Test void studentDownloadRequiresCurrentAssignment() throws Exception {
        var resource = file();
        role(Role.CUSTOMER);
        when(assignments.findByCustomerIdOrderByAccessStartsAtDesc(userId)).thenReturn(List.of());
        assertThrows(ForbiddenOperationException.class, () -> controller.download(resource.getId(), auth));
        var assignment = new MentorshipAssignmentEntity(UUID.randomUUID(), UUID.randomUUID(), userId, offeringId, MentorshipMode.ASYNCHRONOUS, MentorshipStatus.IN_PROGRESS, Instant.now().minusSeconds(60), Instant.now().plusSeconds(60), 0, null);
        when(assignments.findByCustomerIdOrderByAccessStartsAtDesc(userId)).thenReturn(List.of(assignment));
        assertArrayEquals(new byte[]{1,2,3}, controller.download(resource.getId(), auth).getBody());
        assignment.update(MentorshipStatus.COMPLETED, 100, Instant.now());
        assertThrows(ForbiddenOperationException.class, () -> controller.download(resource.getId(), auth));
    }
}
