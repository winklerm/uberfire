/*
 * Copyright 2012 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.uberfire.security.server.auth;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;

import org.uberfire.security.ResourceManager;
import org.uberfire.security.Role;
import org.uberfire.security.SecurityContext;
import org.uberfire.security.Subject;
import org.uberfire.security.auth.AuthenticatedStorageProvider;
import org.uberfire.security.auth.AuthenticationException;
import org.uberfire.security.auth.AuthenticationManager;
import org.uberfire.security.auth.AuthenticationProvider;
import org.uberfire.security.auth.AuthenticationResult;
import org.uberfire.security.auth.AuthenticationScheme;
import org.uberfire.security.auth.Credential;
import org.uberfire.security.auth.Principal;
import org.uberfire.security.auth.RoleProvider;
import org.uberfire.security.impl.IdentityImpl;
import org.uberfire.security.impl.RoleImpl;
import org.uberfire.security.server.HttpSecurityContext;
import org.uberfire.security.server.cdi.SecurityFactory;

import static org.kie.commons.validation.PortablePreconditions.checkNotEmpty;
import static org.kie.commons.validation.PortablePreconditions.checkNotNull;
import static org.kie.commons.validation.Preconditions.*;
import static org.uberfire.security.Role.*;
import static org.uberfire.security.auth.AuthenticationStatus.*;

//TODO {porcelli} support for jaas!
public class HttpAuthenticationManager implements AuthenticationManager,
                                                  Serializable {

    private static final long serialVersionUID = 4343210317731383536L;

    private final List<AuthenticationScheme> authSchemes;
    private final List<AuthenticationProvider> authProviders;
    private final List<RoleProvider> roleProviders;
    private final List<AuthenticatedStorageProvider> authStorageProviders;

    private final ResourceManager resourceManager;
    private final Map<String, String> requestCache = new HashMap<String, String>();

    //if System.getProperty("java.security.auth.login.config") != null => create a JAASProvider

    public HttpAuthenticationManager( final List<AuthenticationScheme> authScheme,
                                      final List<AuthenticationProvider> authProviders,
                                      final List<RoleProvider> roleProviders,
                                      final List<AuthenticatedStorageProvider> authStorageProviders,
                                      final ResourceManager resourceManager ) {
        this.authSchemes = checkNotEmpty( "authScheme", authScheme );
        this.authProviders = checkNotEmpty( "authProviders", authProviders );
        this.roleProviders = checkNotEmpty( "roleProviders", roleProviders );
        this.authStorageProviders = checkNotEmpty( "authStorageProviders", authStorageProviders );
        this.resourceManager = checkNotNull( "resourceManager", resourceManager );
    }

    @Override
    public Subject authenticate( final SecurityContext context ) throws AuthenticationException {
        final HttpSecurityContext httpContext = checkInstanceOf( "context", context, HttpSecurityContext.class );

        Principal principal = null;
        for ( final AuthenticatedStorageProvider storeProvider : authStorageProviders ) {
            principal = storeProvider.load( httpContext );
            if ( principal != null ) {
                break;
            }
        }

        if ( principal != null && principal instanceof Subject ) {
            return (Subject) principal;
        }

        boolean isRememberOp = principal != null;

        final boolean requiresAuthentication = resourceManager.requiresAuthentication( httpContext.getResource() );

        if ( principal == null ) {
            for ( final AuthenticationScheme authScheme : authSchemes ) {
                if ( !authScheme.isAuthenticationRequest( httpContext ) && requiresAuthentication ) {
                    requestCache.put( httpContext.getRequest().getSession().getId(), httpContext.getRequest().getRequestURI() + "?" + httpContext.getRequest().getQueryString() );
                    authScheme.challengeClient( httpContext );
                }
            }

            if ( !requiresAuthentication ) {
                return null;
            }

            all_auth:
            for ( final AuthenticationScheme authScheme : authSchemes ) {
                final Credential credential = authScheme.buildCredential( httpContext );

                if ( credential == null ) {
                    continue;
                }

                for ( final AuthenticationProvider authProvider : authProviders ) {
                    final AuthenticationResult result = authProvider.authenticate( credential );
                    if ( result.getStatus().equals( FAILED ) ) {
                        throw new AuthenticationException( "Invalid credentials." );
                    } else if ( result.getStatus().equals( SUCCESS ) ) {
                        principal = result.getPrincipal();
                        break all_auth;
                    }
                }
            }
        }

        if ( principal == null ) {
            throw new AuthenticationException( "Invalid credentials." );
        }

        final List<Role> roles = new ArrayList<Role>();
        if ( isRememberOp ) {
            roles.add( new RoleImpl( ROLE_REMEMBER_ME ) );
        }

        for ( final RoleProvider roleProvider : roleProviders ) {
            roles.addAll( roleProvider.loadRoles( principal ) );
        }

        final String name = principal.getName();
        final Subject result = new IdentityImpl( name, roles );

        for ( final AuthenticatedStorageProvider storeProvider : authStorageProviders ) {
            storeProvider.store( httpContext, result );
        }

        final String originalRequest = requestCache.remove(httpContext.getRequest().getSession().getId());
        if (originalRequest != null && !originalRequest.isEmpty() && !httpContext.getResponse().isCommitted()) {
            try {
                if (useRedirect(originalRequest)) {
                    httpContext.getResponse().sendRedirect(originalRequest);
                } else {
                    // subject must be already set here since we forwarding
                    SecurityFactory.setSubject(result);
                    RequestDispatcher rd = httpContext.getRequest().getRequestDispatcher(originalRequest.replaceFirst(httpContext.getRequest().getContextPath(), ""));
                    // forward instead of sendRedirect as sendRedirect will always use GET method which
                    // means it can change http method if non GET was used for instance POST
                    rd.forward(httpContext.getRequest(), httpContext.getResponse());
                }
            } catch (Exception e) {
                throw new RuntimeException("Unable to redirect.");
            }
        }

        return result;
    }

    @Override
    public void logout( final SecurityContext context ) throws AuthenticationException {
        for ( final AuthenticatedStorageProvider storeProvider : authStorageProviders ) {
            storeProvider.cleanup( context );
        }
        final HttpSecurityContext httpContext = checkInstanceOf( "context", context, HttpSecurityContext.class );
        httpContext.getRequest().getSession().invalidate();
    }

    private boolean useRedirect(String originalRequest) {
        // hack for gwt hosted mode
        if (originalRequest.indexOf("gwt.codesvr") != -1) {
            return true;
        }
        return false;
    }
}
