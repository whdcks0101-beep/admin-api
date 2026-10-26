package com.portfolio.admin_api.auth;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.portfolio.admin_api.config.SecurityConfig;
import com.portfolio.admin_api.security.handler.ApiAccessDeniedHandler;
import com.portfolio.admin_api.security.handler.ApiAuthenticationEntryPoint;
import com.portfolio.admin_api.inventory.controller.InventoryController;
import com.portfolio.admin_api.inventory.service.InventoryService;
import com.portfolio.admin_api.product.controller.ProductController;
import com.portfolio.admin_api.product.service.ProductService;
import com.portfolio.admin_api.user.controller.UserController;
import com.portfolio.admin_api.user.service.UserService;

@WebMvcTest({InventoryController.class, ProductController.class, UserController.class})
@ActiveProfiles("test")
@Import({SecurityConfig.class, ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class})
class DemoAdminSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean InventoryService inventories;
    @MockitoBean ProductService products;
    @MockitoBean UserService users;

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "PUT", "DELETE"})
    @DisplayName("체험 관리자는 유효한 CSRF를 보내도 회원 조회·생성·수정·삭제가 모두 403이다")
    void demoCannotAccessUsers(String method) throws Exception {
        String path = method.equals("PUT") || method.equals("DELETE") ? "/api/users/1" : "/api/users";
        mvc.perform(request(HttpMethod.valueOf(method), path)
            .with(user("demo").roles("DEMO_ADMIN", "USER")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "DEMO_ADMIN"})
    @DisplayName("운영·체험 관리자는 상품 등록·수정과 입출고를 사용할 수 있다")
    void inventoryWritesAllowed(String role) throws Exception {
        mvc.perform(post("/api/products").with(user("writer").roles(role,"USER")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"sku\":\"DEMO-1\",\"name\":\"상품\",\"safetyStock\":1,\"initialQuantity\":10}"))
            .andExpect(status().isCreated());
        mvc.perform(put("/api/products/1").with(user("writer").roles(role,"USER")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"수정\",\"safetyStock\":2}"))
            .andExpect(status().isOk());
        for (String action : new String[]{"stock-in", "stock-out"}) {
            mvc.perform(post("/api/products/1/" + action).with(user("writer").roles(role,"USER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("일반회원의 상품 변경은 서버에서 거부한다")
    void userCannotWrite() throws Exception {
        mvc.perform(put("/api/products/1").with(user("reader").roles("USER")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"수정\",\"safetyStock\":2}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(products);
    }

    @Test
    @DisplayName("체험 관리자도 CSRF가 없으면 상품 변경을 거부하고 신규 관리 API 권한을 자동 부여하지 않는다")
    void demoNeedsCsrfAndExplicitEndpoint() throws Exception {
        mvc.perform(post("/api/products").with(user("demo").roles("DEMO_ADMIN","USER")))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/new-admin-feature").with(user("demo").roles("DEMO_ADMIN","USER")).with(csrf()))
            .andExpect(status().isForbidden());
        verifyNoInteractions(inventories);
    }
}
