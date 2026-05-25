package com.example.sentinalAI.services;

import com.example.sentinalAI.models.Service;
import com.example.sentinalAI.repositories.ServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ServiceManagementService {
    private final ServiceRepository serviceRepository;

    public Service createService(String serviceName, String serviceType, String description) {
        Service service = Service.builder()
                .serviceName(serviceName)
                .serviceType(serviceType)
                .description(description)
                .isHealthy(true)
                .healthStatus("HEALTHY")
                .lastChecked(LocalDateTime.now())
                .build();
        return serviceRepository.save(service);
    }

    public Optional<Service> getService(String serviceId) {
        return serviceRepository.findById(serviceId);
    }

    public Optional<Service> getServiceByName(String serviceName) {
        return serviceRepository.findByServiceName(serviceName);
    }

    public List<Service> getAllServices() {
        return serviceRepository.findAll();
    }

    public void updateServiceHealth(String serviceId, Boolean isHealthy, String status) {
        Optional<Service> service = serviceRepository.findById(serviceId);
        if (service.isPresent()) {
            Service s = service.get();
            s.setIsHealthy(isHealthy);
            s.setHealthStatus(status);
            s.setLastChecked(LocalDateTime.now());
            serviceRepository.save(s);
        }
    }
}



