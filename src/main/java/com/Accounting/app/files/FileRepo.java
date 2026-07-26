package com.Accounting.app.files;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface FileRepo extends JpaRepository<UploadedFile,Integer>{
    List<UploadedFile> findAllByUser_Email(String email);

    List<UploadedFile> findAllByUser_EmailAndDocumentTypeOrderByUploadedAtDesc(String email, DocumentType documentType);

    Optional<UploadedFile> findByIdAndUser_Email(Integer id, String email);

    long countByRelatedTransaction_TransactionIdAndUser_Email(Integer transactionId, String email);
}

