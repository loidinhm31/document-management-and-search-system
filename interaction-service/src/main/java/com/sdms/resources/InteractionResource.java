package com.sdms.resources;


import com.sdms.dto.InteractionRequest;
import com.sdms.dto.InteractionResponse;
import com.sdms.service.InteractionService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/api/v1/interactions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InteractionResource {

    @Inject
    JsonWebToken jwt;

    @Inject
    InteractionService interactionService;

    @GET
    @Path("/test")
    public Response test() {
        return Response.ok("test").build();
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"ROLE_USER", "ROLE_ADMIN"})
    public Response getInteraction(@PathParam("id") String id) {
        return Response.ok(interactionService.getInteraction(id)).build();
    }

    @POST
    @RolesAllowed({"ROLE_USER", "ROLE_ADMIN"})
    public Response createInteraction(InteractionRequest request) {
        InteractionResponse response = interactionService.createInteraction(request, jwt.getName());
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @GET
    @Path("/user/{username}")
    @RolesAllowed({"ROLE_USER", "ROLE_ADMIN"})
    public Response getUserInteractions(@PathParam("username") String username) {
        if (!jwt.getName().equals(username) && !jwt.getGroups().contains("ROLE_ADMIN")) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        return Response.ok(interactionService.getUserInteractions(username)).build();
    }

}