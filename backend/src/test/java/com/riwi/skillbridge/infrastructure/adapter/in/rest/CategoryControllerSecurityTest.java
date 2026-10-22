package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.CreateCategoryUseCase;
import com.riwi.skillbridge.application.port.in.DeleteCategoryUseCase;
import com.riwi.skillbridge.application.port.in.ListCategoriesUseCase;
import com.riwi.skillbridge.application.port.in.UpdateCategoryUseCase;
import com.riwi.skillbridge.domain.model.Category;
import com.riwi.skillbridge.infrastructure.config.SecurityConfiguration;
import com.riwi.skillbridge.infrastructure.security.AuthCookieService;
import com.riwi.skillbridge.infrastructure.security.DatabaseUserDetailsService;
import com.riwi.skillbridge.infrastructure.security.JwtService;
import com.riwi.skillbridge.infrastructure.security.RestAccessDeniedHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SEC-005: modificar y eliminar categorías globales es exclusivo de ADMIN. */
@WebMvcTest(
    controllers = CategoryController.class,
    properties = "app.cors.allowed-origins=http://localhost:4200")
@Import({SecurityConfiguration.class, RestAccessDeniedHandler.class})
class CategoryControllerSecurityTest {

    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final String BODY = "{\"name\":\"Plomeria\"}";

    @Autowired MockMvc mvc;

    @MockitoBean ListCategoriesUseCase listCategories;
    @MockitoBean CreateCategoryUseCase createCategory;
    @MockitoBean UpdateCategoryUseCase updateCategory;
    @MockitoBean DeleteCategoryUseCase deleteCategory;
    // Dependencias del filtro JWT (aquí la autenticación la simula @WithMockUser)
    @MockitoBean JwtService jwtService;
    @MockitoBean DatabaseUserDetailsService userDetailsService;
    @MockitoBean AuthCookieService cookieService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_puede_actualizar() throws Exception {
        when(updateCategory.update(any(), any()))
            .thenReturn(new Category(CATEGORY_ID, "Plomeria", true, Instant.now()));

        mvc.perform(put(url()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("PLOMERIA"));
        verify(updateCategory).update(CATEGORY_ID, "Plomeria");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_puede_eliminar() throws Exception {
        mvc.perform(delete(url()).with(csrf()))
            .andExpect(status().isNoContent());
        verify(deleteCategory).delete(CATEGORY_ID);
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void provider_no_puede_actualizar() throws Exception {
        mvc.perform(put(url()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
        verify(updateCategory, never()).update(any(), any());
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void provider_no_puede_eliminar() throws Exception {
        mvc.perform(delete(url()).with(csrf()))
            .andExpect(status().isForbidden());
        verify(deleteCategory, never()).delete(any());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customer_no_puede_actualizar() throws Exception {
        mvc.perform(put(url()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
        verify(updateCategory, never()).update(any(), any());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customer_no_puede_eliminar() throws Exception {
        mvc.perform(delete(url()).with(csrf()))
            .andExpect(status().isForbidden());
        verify(deleteCategory, never()).delete(any());
    }

    @Test
    void anonimo_no_puede_actualizar() throws Exception {
        mvc.perform(put(url()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isUnauthorized());
        verify(updateCategory, never()).update(any(), any());
    }

    @Test
    void anonimo_no_puede_eliminar() throws Exception {
        mvc.perform(delete(url()))
            .andExpect(status().isUnauthorized());
        verify(deleteCategory, never()).delete(any());
    }

    private String url() {
        return "/api/categories/" + CATEGORY_ID;
    }
}
