package com.dms.document.interaction.service;

import com.dms.document.interaction.dto.MasterDataRequest;
import com.dms.document.interaction.dto.MasterDataResponse;
import com.dms.document.interaction.enums.MasterDataType;

import java.util.List;
import java.util.Optional;

public interface MasterDataService {

    /**
     * Retrieves all master data entries of a specific type, optionally filtered by active status.
     *
     * @param type     The type of master data to retrieve
     * @param isActive Optional filter for active status
     * @return List of master data responses
     */
    List<MasterDataResponse> getAllByType(MasterDataType type, Boolean isActive);

    /**
     * Retrieves a master data entry by type and code.
     *
     * @param type The type of master data
     * @param code The code of the master data
     * @return Optional containing the master data response if found
     */
    Optional<MasterDataResponse> getByTypeAndCode(MasterDataType type, String code);

    /**
     * Searches for master data entries containing the specified text.
     *
     * @param searchText The text to search for
     * @return List of matching master data responses
     */
    List<MasterDataResponse> searchByText(String searchText);

    /**
     * Creates a new master data entry.
     *
     * @param request The master data creation request
     * @return The created master data response
     */
    MasterDataResponse save(MasterDataRequest request);

    /**
     * Updates an existing master data entry.
     *
     * @param id      The ID of the master data to update
     * @param request The update request
     * @return The updated master data response
     */
    MasterDataResponse update(String id, MasterDataRequest request);

    /**
     * Deletes a master data entry by ID.
     *
     * @param id The ID of the master data to delete
     */
    void deleteById(String id);

    /**
     * Retrieves all master data entries of a specific type and parent ID,
     * optionally filtered by active status.
     *
     * @param type     The type of master data to retrieve
     * @param parentId The parent ID to filter by
     * @param isActive Optional filter for active status
     * @return List of master data responses
     */
    List<MasterDataResponse> getAllByTypeAndParentId(MasterDataType type, String parentId, Boolean isActive);

    /**
     * Checks if a master data item is in use and cannot be fully modified or deleted.
     *
     * @param masterDataId The ID of the master data to check
     * @return true if the master data is in use, false otherwise
     */
    boolean isItemInUse(String masterDataId);
}