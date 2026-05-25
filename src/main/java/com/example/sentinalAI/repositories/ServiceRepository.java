package com.example.sentinalAI.repositories;

import com.example.sentinalAI.models.Service;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ServiceRepository extends JpaRepository<Service, String> {
    Optional<Service> findByServiceName(String serviceName);
}

