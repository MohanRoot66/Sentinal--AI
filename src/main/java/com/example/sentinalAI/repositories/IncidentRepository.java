package com.example.sentinalAI.repositories;

import com.example.sentinalAI.models.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, String> {
    List<Incident> findByStatusOrderByDetectedAtDesc(String status);

    List<Incident> findBySeverityOrderByDetectedAtDesc(String severity);
}

