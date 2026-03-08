package com.udit.subscriptionmanager.repository;

import com.udit.subscriptionmanager.entity.Household;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HouseholdRepository extends JpaRepository<Household, Long> {
}