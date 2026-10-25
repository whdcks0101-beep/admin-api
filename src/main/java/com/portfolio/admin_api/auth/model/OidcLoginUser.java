package com.portfolio.admin_api.auth.model;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public class OidcLoginUser extends DefaultOidcUser implements AuthenticatedUser{

    private static final long serialVersionUID = 1L;

    private final Long userId;

    public OidcLoginUser(
            Long userId,
            Collection<? extends GrantedAuthority> authorities,
            OidcUser oidcUser
    ) {
        super(
                authorities,
                oidcUser.getIdToken(),
                oidcUser.getUserInfo()
        );

        this.userId = userId;
    }

    @Override
    public Long getUserId() {
        return userId;
    }
}
