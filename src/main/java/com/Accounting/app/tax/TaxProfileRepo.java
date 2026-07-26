package com.Accounting.app.tax;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface TaxProfileRepo extends JpaRepository<TaxProfile,Integer> {

    List<TaxProfile> findAllByEmail(String email);
}
