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
package com.atomgraph.linkeddatahub.resource.graph;

import org.apache.jena.ontology.Ontology;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import com.sun.jersey.api.core.HttpContext;
import com.sun.jersey.api.core.ResourceContext;
import java.net.URI;
import java.util.List;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Providers;
import com.atomgraph.core.MediaTypes;
import com.atomgraph.linkeddatahub.client.DataManager;
import com.atomgraph.core.util.ModelUtils;
import com.atomgraph.linkeddatahub.model.Service;
import com.atomgraph.linkeddatahub.server.model.impl.ClientUriInfo;
import com.atomgraph.linkeddatahub.server.method.PATCH;
import com.atomgraph.linkeddatahub.server.model.Patchable;
import com.atomgraph.linkeddatahub.server.model.impl.ResourceBase;
import com.atomgraph.processor.util.TemplateCall;
import com.atomgraph.processor.vocabulary.DH;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import java.util.Arrays;
import java.util.Collections;
import javax.ws.rs.DELETE;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.PUT;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.UriInfo;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.ResultSetRewindable;
import org.apache.jena.sparql.vocabulary.FOAF;
import org.apache.jena.update.UpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Directly identified named graph resource, based on Graph Store.
 * 
 * @author Martynas Jusevičius {@literal <martynas@atomgraph.com>}
 */
public class Item extends ResourceBase implements Patchable // com.atomgraph.core.model.impl.QuadStoreBase 
{

    private static final Logger log = LoggerFactory.getLogger(Item.class);
    
    public Item(@Context UriInfo uriInfo, @Context ClientUriInfo clientUriInfo, @Context Request request, @Context MediaTypes mediaTypes,
            @Context Service service, @Context com.atomgraph.linkeddatahub.apps.model.Application application,
            @Context Ontology ontology, @Context TemplateCall templateCall,
            @Context HttpHeaders httpHeaders, @Context ResourceContext resourceContext,
            @Context Client client,
            @Context HttpContext httpContext, @Context SecurityContext securityContext,
            @Context DataManager dataManager, @Context Providers providers,
            @Context Application system)
    {
        super(uriInfo, clientUriInfo, request, mediaTypes, 
                service, application,
                ontology, templateCall,
                httpHeaders, resourceContext,
                client,
                httpContext, securityContext,
                dataManager, providers,
                system);
    }
    
    @OPTIONS
    @Override
    public Response options()
    {
        ResponseBuilder rb = Response.ok().
            header("Allow", HttpMethod.GET).
            header("Allow", HttpMethod.POST).
            header("Allow", HttpMethod.PUT).
            header("Allow", HttpMethod.DELETE);
        
        String acceptWritable = StringUtils.join(getWritableMediaTypes(), ",");
        rb.header("Accept-Post", acceptWritable);
        rb.header("Accept-Put", acceptWritable);
        
        return rb.build();
    }
  
    @Override
    public Response get()
    {
        return getResponseBuilder(DatasetFactory.create(getService().getDatasetAccessor().getModel(getURI().toString()))).build();
    }

    @Override
    public Response post(Dataset dataset)
    {
        getService().getDatasetAccessor().add(getURI().toString(), dataset.getDefaultModel());
        
        return Response.ok().build();
    }

    @Override
    @PUT
    public Response put(Dataset dataset)
    {
        Resource document = null, container = null;
        ResultSet resultSet = getService().getSPARQLClient().query(new ParameterizedSparqlString(getSystem().getGraphDocumentQuery().toString(),
                getTemplateCall().getQuerySolutionMap(), getUriInfo().getBaseUri().toString()).asQuery(), ResultSet.class,
                new MultivaluedMapImpl()).
                getEntity(ResultSetRewindable.class);
        if (resultSet.hasNext())
        {
            QuerySolution qs = resultSet.next();
            document = qs.getResource(FOAF.Document.getLocalName());
            container = qs.getResource(DH.Container.getLocalName());
        }
        
        Model existing = getService().getDatasetAccessor().getModel(getURI().toString());
        if (!existing.isEmpty()) // remove existing representation
        {
            EntityTag entityTag = new EntityTag(Long.toHexString(ModelUtils.hashModel(existing)));
            ResponseBuilder rb = getRequest().evaluatePreconditions(entityTag);
            if (rb != null)
            {
                if (log.isDebugEnabled()) log.debug("PUT preconditions were not met for resource: {} with entity tag: {}", this, entityTag);
                return rb.build();
            }
        }
        
        //Response response = super.put(dataset, Boolean.FALSE, URI.create(getURI().toString()));
        getService().getDatasetAccessor().putModel(getURI().toString(), dataset.getDefaultModel());
        
        if (getSystem().isInvalidateCache())
        {
//            if (document == null) throw new IllegalStateException("Graph '" + getURI() + "' does not contain a document resource");
//            ClientResponse purgeResponse = purge(document); // purge cache for Item URI
//            if (log.isDebugEnabled()) log.debug("Sent PURGE request to URI: {}; received status code: {}", document.getURI(), purgeResponse.getStatus());
//            purgeResponse.close();

            // ban the Item and its parent Container
//            ClientResponse ban;
//            if (container != null) ban = ban(document, container);
//            else ban = ban(document);
//            if (ban != null)
//                if (log.isDebugEnabled()) log.debug("Sent BAN request, received status code: {}", ban.getStatus());
        }
        
        return Response.ok().build();
    }
    
//    @PUT
//    @Consumes(MediaType.MULTIPART_FORM_DATA)
//    public Response putMultipart(FormDataMultiPart multiPart)
//    {
//        if (log.isDebugEnabled()) log.debug("MultiPart fields: {} body parts: {}", multiPart.getFields(), multiPart.getBodyParts());
//
//        try
//        {
//            Model model = ResourceBase.parseModel(multiPart);
//            if (log.isDebugEnabled()) log.debug("POSTed Model size: {} Model: {}", model.size(), model);
//
//            // writing files has to go before put() as it changes model (e.g. adds body part media type as dct:format)
//            int count = ResourceBase.processFormDataMultiPart(model, multiPart);
//            if (log.isDebugEnabled()) log.debug("{} Files uploaded from FormDataMultiPart: {} ", count, multiPart);
//
//            Response response = put(model);
//
//            return Response.seeOther(URI.create(getURI())).build();
//        }
//        catch (URISyntaxException ex)
//        {
//            if (log.isErrorEnabled()) log.error("URI '{}' has syntax error in request with media type: {}", ex.getInput(), multiPart.getMediaType());
//            throw new WebApplicationException(ex, Response.Status.BAD_REQUEST);
//        }
//        catch (IOException ex)
//        {
//            if (log.isErrorEnabled()) log.error("Error reading multipart request");
//            throw new WebApplicationException(ex);
//        }
//    }
    
    @Override
    @DELETE
    public Response delete()
    {
        Resource document = null, container = null;
        ResultSet resultSet = getService().getSPARQLClient().
            query(new ParameterizedSparqlString(getSystem().getGraphDocumentQuery().toString(),
                getTemplateCall().getQuerySolutionMap(), getUriInfo().getBaseUri().toString()).asQuery(), ResultSet.class,
                new MultivaluedMapImpl()).
            getEntity(ResultSetRewindable.class);
        if (resultSet.hasNext())
        {
            QuerySolution qs = resultSet.next();
            document = qs.getResource(FOAF.Document.getLocalName());
            container = qs.getResource(DH.Container.getLocalName());
        }

        //getSPARQLEndpoint().post(getUpdateRequest((Model)null), Collections.<URI>emptyList(), Collections.<URI>emptyList()); // remove named graph about named graph
        getService().getDatasetAccessor().deleteModel(getURI().toString());

        if (getSystem().isInvalidateCache())
        {
//            if (document == null) throw new IllegalStateException("Graph '" + getURI() + "' does not contain a document resource");
//            ClientResponse purgeResponse = purge(document); // purge cache for Item URI
//            if (log.isDebugEnabled()) log.debug("Sent PURGE request to URI: {}; received status code: {}", document.getURI(), purgeResponse.getStatus());
//            purgeResponse.close();

            // ban the Item and its parent Container
//            ClientResponse ban;
//            if (container != null) ban = ban(document, container);
//            else ban = ban(document);
//            if (ban != null)
//                if (log.isDebugEnabled()) log.debug("Sent BAN request, received status code: {}", ban.getStatus());
        }
        
        if (container != null) return Response.seeOther(URI.create(container.getURI())).build(); // TO-DO: move to Filter?
        
        return Response.noContent().build();
    }
    
    @PATCH
    @Override
    public Response patch(UpdateRequest updateRequest)
    {
        // TO-DO: do a check that the update only uses this named graph
        getService().getEndpointAccessor().update(updateRequest, Collections.<URI>emptyList(), Collections.<URI>emptyList());
        
        return Response.ok().build();
    }

    public List<MediaType> getWritableMediaTypes()
    {
        return getMediaTypes().getWritable(Model.class);
    }
    
}