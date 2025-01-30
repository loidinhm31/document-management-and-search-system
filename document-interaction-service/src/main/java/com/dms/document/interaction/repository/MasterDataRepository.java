package com.dms.document.interaction.repository;

import com.dms.document.interaction.enums.MasterDataType;
import com.dms.document.interaction.model.MasterData;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MasterDataRepository extends MongoRepository<MasterData, String> {
    List<MasterData> findByType(MasterDataType type);
    Optional<MasterData> findByTypeAndCode(MasterDataType type, String code);
    List<MasterData> findByTypeAndIsActiveTrue(MasterDataType type);

    @Query("{ $text: { $search: ?0 } }")
    List<MasterData> searchByText(String searchText);
}