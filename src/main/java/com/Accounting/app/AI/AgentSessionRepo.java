package com.Accounting.app.AI;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentSessionRepo extends JpaRepository<AgentSession, String> {
    List<AgentSession> findAllByUserEmailOrderByUpdatedAtDesc(String userEmail);

    @EntityGraph(attributePaths = {"messages", "messages.files", "messages.files.uploadedFile", "folder"})
    Optional<AgentSession> findByIdAndUserEmail(String id, String userEmail);
}
