package com.Accounting.app.files;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReceiptExtractionRepo extends JpaRepository<ReceiptExtraction, Integer> {
    @EntityGraph(attributePaths = "items")
    Optional<ReceiptExtraction> findByUploadedFile_Id(Integer uploadedFileId);

    @EntityGraph(attributePaths = "items")
    List<ReceiptExtraction> findAllByUploadedFile_IdIn(Collection<Integer> uploadedFileIds);

    @EntityGraph(attributePaths = "items")
    List<ReceiptExtraction> findAllByUploadedFile_User_Email(String email);
}
