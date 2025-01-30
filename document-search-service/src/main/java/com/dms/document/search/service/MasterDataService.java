package com.dms.document.search.service;


import com.dms.document.search.enums.MasterDataType;
import com.dms.document.search.model.MasterData;
import com.dms.document.search.repository.MasterDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MasterDataService {
    private final MasterDataRepository masterDataRepository;

    public List<MasterData> getAllByType(MasterDataType type) {
        return masterDataRepository.findByType(type);
    }

    public List<MasterData> getAllActiveByType(MasterDataType type) {
        return masterDataRepository.findByTypeAndIsActiveTrue(type);
    }

    public Optional<MasterData> getByTypeAndCode(MasterDataType type, String code) {
        return masterDataRepository.findByTypeAndCode(type, code);
    }

    public List<MasterData> searchByText(String searchText) {
        return masterDataRepository.searchByText(searchText);
    }

    public MasterData save(MasterData masterData) {
        return masterDataRepository.save(masterData);
    }

    public void deleteById(String id) {
        masterDataRepository.deleteById(id);
    }
}