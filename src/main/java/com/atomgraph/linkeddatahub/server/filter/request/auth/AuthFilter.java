/**
 *  Copyright 2019 Martynas Jusevičius <martynas@atomgraph.com>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.atomgraph.linkeddatahub.server.filter.request.auth;

import com.atomgraph.core.exception.AuthenticationException;
import com.atomgraph.core.vocabulary.SD;
import com.atomgraph.linkeddatahub.exception.auth.AuthorizationException;
import com.atomgraph.linkeddatahub.apps.model.EndUserApplication;
import com.atomgraph.linkeddatahub.client.SesameProtocolClient;
import com.atomgraph.linkeddatahub.client.filter.CacheControlFilter;
import com.atomgraph.linkeddatahub.model.Service;
import com.atomgraph.linkeddatahub.model.UserAccount;
import com.atomgraph.linkeddatahub.server.provider.ApplicationProvider;
import com.atomgraph.linkeddatahub.vocabulary.ACL;
import com.atomgraph.linkeddatahub.vocabulary.APLT;
import com.atomgraph.linkeddatahub.vocabulary.LACL;
import com.atomgraph.processor.vocabulary.SIOC;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;
import com.sun.jersey.spi.container.ContainerResponseFilter;
import com.sun.jersey.spi.container.ResourceFilter;
import java.net.URI;
import java.net.URISyntaxException;
import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.Providers;
import org.apache.jena.ontology.Ontology;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract JAX-RS filter base class for authentication request filters.
 * 
 * @author Martynas Jusevičius {@literal <martynas@atomgraph.com>}
 */
public abstract class AuthFilter implements ResourceFilter, ContainerRequestFilter
{
    
    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);
    
    @Context Application system;
    @Context Providers providers;
    @Context UriInfo uriInfo;
    @Context Request request;
    @Context HttpServletRequest httpServletRequest;

    private ParameterizedSparqlString authQuery, ownerAuthQuery;

    @PostConstruct
    public void init()
    {
        authQuery = new ParameterizedSparqlString(getSystem().getAuthQuery().toString());
        ownerAuthQuery = new ParameterizedSparqlString(getSystem().getOwnerAuthQuery().toString());
    }

    public abstract String getScheme();
    
    public abstract void login(com.atomgraph.linkeddatahub.apps.model.Application app, String realm, ContainerRequest request);

    public abstract void logout(com.atomgraph.linkeddatahub.apps.model.Application app, String realm, ContainerRequest request);

    public boolean isApplied(com.atomgraph.linkeddatahub.apps.model.Application app, String realm, ContainerRequest request)
    {
        return true;
    }
    
    public abstract QuerySolutionMap getQuerySolutionMap(String realm, ContainerRequest request, URI absolutePath, Resource accessMode);

    public abstract ContainerRequest authenticate(String realm, ContainerRequest request, Resource accessMode, UserAccount account, Resource agent);

    @Override
    public ContainerRequest filter(ContainerRequest request)
    {
        if (request == null) throw new IllegalArgumentException("ContainerRequest cannot be null");
        if (log.isDebugEnabled()) log.debug("Authenticating request URI: {}", request.getRequestUri());

        // skip filter if user already authorized
        if (request.getSecurityContext().getUserPrincipal() != null) return request;
        
        com.atomgraph.linkeddatahub.apps.model.Application app = getApplicationProvider().getApplication(getHttpServletRequest());
        // skip filter if no application has matched
        if (app == null) return request;
            
        Resource accessMode = null;
        if (request.getMethod().equalsIgnoreCase("GET") || request.getMethod().equalsIgnoreCase("HEAD")) accessMode = ACL.Read;
        if (request.getMethod().equalsIgnoreCase("POST")) accessMode = ACL.Append;
        if (request.getMethod().equalsIgnoreCase("PUT")) accessMode = ACL.Write;
        if (request.getMethod().equalsIgnoreCase("DELETE")) accessMode = ACL.Write;
        if (request.getMethod().equalsIgnoreCase("PATCH")) accessMode = ACL.Write;
        if (log.isDebugEnabled()) log.debug("Request method: {} ACL access mode: {}", request.getMethod(), accessMode);
        if (accessMode == null)
        {
            if (log.isWarnEnabled()) log.warn("Skipping authentication/authorization, request method not recognized: {}", request.getMethod());
            return request;
        }
        
        return authorize(request, request.getAbsolutePath(), accessMode, app);
    }
    
    public ContainerRequest authorize(ContainerRequest request, URI absolutePath, Resource accessMode, com.atomgraph.linkeddatahub.apps.model.Application app)
    {
        if (!app.hasProperty(DCTerms.title) || !app.getProperty(DCTerms.title).getObject().isLiteral())
        {
            if (log.isErrorEnabled()) log.error("Authorization realm is not configured (application missing title)");
            throw new IllegalStateException("Authorization realm not configured (application missing title");
        }
        if (log.isDebugEnabled()) log.debug("Authenticating against Application: {}", app);
        
        String realm = app.getProperty(DCTerms.title).getString();
                
        if (isApplied(app, realm, request) || isLoginForced(request, getScheme())) // checks if this filter should be applied
        {
            if (isLogoutForced(request, getScheme())) logout(app, realm, request);
            
            QuerySolutionMap qsm = getQuerySolutionMap(realm, request, absolutePath, accessMode);
            if (qsm == null && isLoginForced(request, getScheme())) login(app, realm, request); // no credentials
            
            Model authModel = loadAuth(qsm, app);
            // RDFS inference is too slow (takes ~2.5 seconds)
            //InfModel authModel = ModelFactory.createRDFSModel(getOntology().getOntModel(), rawModel);

            Resource accountRes = getResourceByPropertyValue(authModel, SIOC.ACCOUNT_OF, null);
            if (accountRes != null)
            {
                // imitate type inference, otherwise we'll get Jena's polymorphism exception
                accountRes.addProperty(RDF.type, LACL.UserAccount);
                UserAccount account = accountRes.as(UserAccount.class);
                Resource agent = account.getPropertyResourceValue(SIOC.ACCOUNT_OF);
                if (agent == null) throw new IllegalStateException("UserAccount must belong to an agent Resource");
             
                try
                {
                    if (log.isTraceEnabled()) log.trace("Authenticating UserAccount realm {}", account, realm);
                    authenticate(realm, request, accessMode, account, agent);
                    request.setSecurityContext(new UserAccountContext(account, getScheme()));
                }
                catch (AuthenticationException ex)
                {
                    if (isLoginForced(request, getScheme())) login(app, realm, request); // allow login if password is bad
                    throw ex;
                }
            }
            else
                if (isLoginForced(request, getScheme())) login(app, realm, request); // allow login if username is bad
                
            // type check will not work on LACL subclasses without InfModel
            Resource authorization = getResourceByPropertyValue(authModel, ACL.mode, null);
            if (authorization == null)
            {
                if (log.isTraceEnabled()) log.trace("Access not authorized for request URI: {}", request.getAbsolutePath());
                throw new AuthorizationException("Access not authorized", request.getAbsolutePath(), accessMode, null);
            }
        }
        
        return request;
    }

    protected Resource getEndUserEndpoint(com.atomgraph.linkeddatahub.apps.model.Application app)
    {
        if (app == null) throw new IllegalArgumentException("Application Resource cannot be null");

        if (app.getService().canAs(com.atomgraph.linkeddatahub.model.dydra.Service.class))
            return app.getService().as(com.atomgraph.linkeddatahub.model.dydra.Service.class).getRepository();
        
        return app.getService().getSPARQLEndpoint();
    }
    
    protected Model loadAuth(QuerySolutionMap qsm, com.atomgraph.linkeddatahub.apps.model.Application app)
    {
        if (app == null) throw new IllegalArgumentException("Application Resource cannot be null");

        ParameterizedSparqlString pss;
        if (app.canAs(EndUserApplication.class)) pss = getAuthQuery().copy(); // end-user
        else pss = getOwnerAuthQuery().copy(); // admin
        
        Service adminService; // always run auth queries on admin Service
        if (app.canAs(EndUserApplication.class))
        {
            EndUserApplication endUserApp = app.as(EndUserApplication.class);
            adminService = endUserApp.getAdminApplication().getService();

            // set ?endpoint value, otherwise the federation between admin and end-user services will fail
            if (app.getService().canAs(com.atomgraph.linkeddatahub.model.dydra.Service.class))
            {
                Resource repository = app.getService().as(com.atomgraph.linkeddatahub.model.dydra.Service.class).getRepository();
                URI endpointURI = URI.create(repository.getURI());

                // rewrite end-user SERVICE ?endpoint's external URL into "localhost" URL if its host matches base (admin) endpoint's host
                if (adminService.canAs(Service.class))
                {
                    URI adminEndpointURI = URI.create(adminService.as(com.atomgraph.linkeddatahub.model.dydra.Service.class).getRepository().getURI());
                    if (adminEndpointURI.getHost().equals(endpointURI.getHost()))
                        try
                        {
                            endpointURI = new URI(endpointURI.getScheme(), "localhost",
                                    endpointURI.getPath(), endpointURI.getFragment());
                        }
                        catch (URISyntaxException ex)
                        {
                        }
                }

                pss.setIri(SD.endpoint.getLocalName(), endpointURI.toString());
            }
            else
                pss.setIri(SD.endpoint.getLocalName(), app.getService().getSPARQLEndpoint().getURI());
        }
        else
            adminService = app.getService();
        
        // send query bindings separately from the query if the service supports the Sesame protocol
        if (adminService.getSPARQLClient() instanceof SesameProtocolClient)
            return ((SesameProtocolClient)adminService.getSPARQLClient()).
                addFilter(new CacheControlFilter(CacheControl.valueOf("no-cache"))). // add Cache-Control: no-cache to request
                query(pss.asQuery(), Model.class, qsm, null).
                getEntity(Model.class);
        else
        {
            pss.setParams(qsm);
            return adminService.getSPARQLClient().
                addFilter(new CacheControlFilter(CacheControl.valueOf("no-cache"))). // add Cache-Control: no-cache to request
                query(pss.asQuery(), Model.class, null).
                getEntity(Model.class);
        }
    }
    
    protected Resource getResourceByPropertyValue(Model model, Property property, RDFNode value)
    {
        if (model == null) throw new IllegalArgumentException("Model cannot be null");
        if (property == null) throw new IllegalArgumentException("Property cannot be null");
        
        ResIterator it = model.listSubjectsWithProperty(property, value);
        
        try
        {
            if (it.hasNext()) return it.next();
        }
        finally
        {
            it.close();
        }

        return null;
    }
     
    public boolean isLoginForced(ContainerRequest request, String scheme)
    {
        if (request == null) throw new IllegalArgumentException("ContainerRequest cannot be null");
        
        if (request.getQueryParameters().getFirst(APLT.login.getLocalName()) != null)
            return request.getQueryParameters().getFirst(APLT.login.getLocalName()).equalsIgnoreCase(scheme);
        
        return false;
    }
    
    public boolean isLogoutForced(ContainerRequest request, String scheme)
    {
        if (request == null) throw new IllegalArgumentException("ContainerRequest cannot be null");

        if (request.getQueryParameters().getFirst(APLT.logout.getLocalName()) != null)
            return request.getQueryParameters().getFirst(APLT.logout.getLocalName()).equalsIgnoreCase(scheme);
        
        return false;
    }
    
    public ApplicationProvider getApplicationProvider()
    {
        return ((ApplicationProvider)getProviders().getContextResolver(com.atomgraph.linkeddatahub.apps.model.Application.class, null));
    }
    
    public Ontology getOntology()
    {
        return getProviders().getContextResolver(Ontology.class, null).getContext(Ontology.class);
    }

    public Providers getProviders()
    {
        return providers;
    }

    public Request getRequest()
    {
        return request;
    }
    
    public HttpServletRequest getHttpServletRequest()
    {
        return httpServletRequest;
    }
    
    public ParameterizedSparqlString getAuthQuery()
    {
        return authQuery;
    }

    public ParameterizedSparqlString getOwnerAuthQuery()
    {
        return ownerAuthQuery;
    }
        
    @Override
    public ContainerRequestFilter getRequestFilter()
    {
        return this;
    }

    @Override
    public ContainerResponseFilter getResponseFilter()
    {
        return null;
    }
    
    public com.atomgraph.linkeddatahub.Application getSystem()
    {
        return (com.atomgraph.linkeddatahub.Application)system;
    }
        
}