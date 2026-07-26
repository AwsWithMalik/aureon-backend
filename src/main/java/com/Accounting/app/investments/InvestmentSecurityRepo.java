package com.Accounting.app.investments;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface InvestmentSecurityRepo extends JpaRepository<InvestmentSecurity, Integer> {
    Optional<InvestmentSecurity> findBySecurityId(String securityId);
}
