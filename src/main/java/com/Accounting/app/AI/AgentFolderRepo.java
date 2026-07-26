package com.Accounting.app.AI;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentFolderRepo extends JpaRepository<AgentFolder, String> {
    List<AgentFolder> findAllByUserEmailOrderByCreatedAtDesc(String userEmail);

    Optional<AgentFolder> findByIdAndUserEmail(String id, String userEmail);
}
