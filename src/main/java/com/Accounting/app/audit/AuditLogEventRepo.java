package com.Accounting.app.audit;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogEventRepo extends JpaRepository<AuditLogEvent, Long>, JpaSpecificationExecutor<AuditLogEvent> {
    @Query("""
            select distinct e.actorType
            from AuditLogEvent e
            where lower(e.userEmail) = lower(:email) and e.actorType is not null
            order by e.actorType
            """)
    List<String> findDistinctActorTypesByUserEmail(@Param("email") String email);

    @Query("""
            select distinct e.result
            from AuditLogEvent e
            where lower(e.userEmail) = lower(:email) and e.result is not null
            order by e.result
            """)
    List<String> findDistinctResultsByUserEmail(@Param("email") String email);

    @Query("""
            select distinct e.resource from AuditLogEvent e
            where lower(e.userEmail) = lower(:email) and e.resource is not null
            order by e.resource
            """)
    List<String> findDistinctResourcesByUserEmail(@Param("email") String email);
}
