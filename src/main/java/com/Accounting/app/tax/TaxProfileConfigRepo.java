package com.Accounting.app.tax;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface TaxProfileConfigRepo extends JpaRepository<TaxProfileConfig, String> {
    Optional<TaxProfileConfig> findByEmail(String email);
}
