package com.live_commerce.coupon.infrastructure.security;

import java.util.Collection;
import java.util.UUID;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;


public class RequestUserDetails implements UserDetails {

	@Getter
  private final UUID userId;
	private final String username;
	private final Collection<? extends GrantedAuthority> authorities;

	public RequestUserDetails(UUID userId, String username, Collection<? extends GrantedAuthority> authorities) {
		this.userId = userId;
		this.username = username;
		this.authorities = authorities;
	}

  @Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return authorities;
	}

	@Override
	public String getUsername() {
		return username;
	}

	@Override public String getPassword() { return null; }
	@Override public boolean isAccountNonExpired() { return true; }
	@Override public boolean isAccountNonLocked() { return true; }
	@Override public boolean isCredentialsNonExpired() { return true; }
	@Override public boolean isEnabled() { return true; }

	@Override
	public String toString() {
		return "RequestUserDetails{" +
				"userId=" + userId +
				", username='" + username + '\'' +
				", authorities=" + authorities +
				'}';
	}
}
