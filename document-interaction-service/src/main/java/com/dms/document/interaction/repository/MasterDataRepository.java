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

    @Query("""
            { $or: [
            { code: { $regex: ?0, $options: 'i' } },
            { 'translations.en': { $regex: ?0, $options: 'i' } },
            { 'translations.vi': { $regex: ?0, $options: 'i' } },
            { description: { $regex: ?0, $options: 'i' } }
            ] }
            """)
    List<MasterData> searchByText(String searchText);
}