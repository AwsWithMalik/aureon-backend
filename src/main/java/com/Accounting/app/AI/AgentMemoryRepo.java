package com.Accounting.app.AI;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentMemoryRepo extends JpaRepository<AgentMemory, Integer> {
    List<AgentMemory> findByUser_IdOrderByUpdatedAtDesc(Integer userId);

    Optional<AgentMemory> findByIdAndUser_Id(Integer id, Integer userId);
}
