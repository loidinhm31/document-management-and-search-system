package com.dms.document.search.repository;


import com.dms.document.search.model.DocumentInformation;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DocumentRepository extends MongoRepository<DocumentInformation, String> {

}