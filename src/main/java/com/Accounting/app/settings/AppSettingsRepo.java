package com.Accounting.app.settings;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface AppSettingsRepo extends JpaRepository<AppSettings, String> {
    Optional<AppSettings> findByEmail(String email);
}
