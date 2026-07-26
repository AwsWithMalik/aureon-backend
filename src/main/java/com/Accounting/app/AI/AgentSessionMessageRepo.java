package com.Accounting.app.AI;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentSessionMessageRepo extends JpaRepository<AgentSessionMessage, String> {
    Optional<AgentSessionMessage> findByIdAndSession_UserEmail(String id, String userEmail);
}
