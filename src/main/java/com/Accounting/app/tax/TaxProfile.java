package com.Accounting.app.tax;

import java.util.List;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "tax_profiles")
public class TaxProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer profileId;

    @Column(nullable = false)
    private String email;
    private String country;
    private String city;
    private String incomeType;
    private String province;

    @ElementCollection
    @CollectionTable(name = "profile_disabilities", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "disability")
    private List<String> disabilities;

    private String deductions;

    // This is the same as @NoArgsConstructor
    public TaxProfile() {
    }

    public TaxProfile(String email, String country, String city, String incomeType, List<String> disabilities,
            String deductions, String province) {
        this.email = email;
        this.country = country;
        this.city = city;
        this.incomeType = incomeType;
        this.disabilities = disabilities;
        this.deductions = deductions;
        this.province = province;
    }

    public Integer getProfileId() {
        return profileId;
    }

    public void setProfileId(Integer profileId) {
        this.profileId = profileId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getIncomeType() {
        return incomeType;
    }

    public void setIncomeType(String incomeType) {
        this.incomeType = incomeType;
    }

    public List<String> getDisabilities() {
        return disabilities;
    }

    public void setDisabilities(List<String> disabilities) {
        this.disabilities = disabilities;
    }

    public String getModifications() {
        return deductions;
    }

    public void setDeductions(String deductions) {
        this.deductions = deductions;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }
}