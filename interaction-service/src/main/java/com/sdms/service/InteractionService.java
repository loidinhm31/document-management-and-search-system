package com.sdms.service;


import com.sdms.dto.InteractionRequest;
import com.sdms.dto.InteractionResponse;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class InteractionService {
    // In-memory storage for demonstration
    private final Map<UUID, InteractionResponse> interactions = new ConcurrentHashMap<>();

    public InteractionResponse createInteraction(InteractionRequest request, String username) {
        UUID id = UUID.randomUUID();
        InteractionResponse interaction = InteractionResponse.builder()
                .id(id)
                .type(request.getType())
                .details(request.getDetails())
                .targetId(request.getTargetId())
                .username(username)
                .createdAt(LocalDateTime.now())
                .build();

        interactions.put(id, interaction);
        return interaction;
    }

    public InteractionResponse getInteraction(String id) {
        return interactions.get(UUID.fromString(id));
    }

    public List<InteractionResponse> getUserInteractions(String username) {
        return interactions.values().stream()
                .filter(i -> i.getUsername().equals(username))
                .toList();
    }
}