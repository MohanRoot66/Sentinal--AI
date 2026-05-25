package com.example.sentinalAI.controllers;

import com.example.sentinalAI.models.Service;
import com.example.sentinalAI.services.ServiceManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/services")
@RequiredArgsConstructor
@Tag(name = "Services", description = "Service management and health status endpoints")
public class ServiceController {
    private final ServiceManagementService serviceManagementService;

    @PostMapping
    @Operation(summary = "Create a new service", description = "Register a new service in the monitoring system")
    public ResponseEntity<Service> createService(@RequestBody Map<String, String> request) {
        Service service = serviceManagementService.createService(
                request.get("serviceName"),
                request.get("serviceType"),
                request.get("description"));
        return ResponseEntity.ok(service);
    }

    @GetMapping
    @Operation(summary = "Get all services", description = "Retrieve all registered services")
    public ResponseEntity<List<Service>> getAllServices() {
        List<Service> services = serviceManagementService.getAllServices();
        return ResponseEntity.ok(services);
    }

    @GetMapping("/{serviceId}")
    @Operation(summary = "Get service details", description = "Retrieve details for a specific service")
    public ResponseEntity<Service> getService(@PathVariable String serviceId) {
        Optional<Service> service = serviceManagementService.getService(serviceId);
        return service.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/name/{serviceName}")
    @Operation(summary = "Get service by name", description = "Retrieve service by its name")
    public ResponseEntity<Service> getServiceByName(@PathVariable String serviceName) {
        Optional<Service> service = serviceManagementService.getServiceByName(serviceName);
        return service.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{serviceId}/health")
    @Operation(summary = "Update service health status", description = "Update health status for a service")
    public ResponseEntity<Void> updateServiceHealth(
            @PathVariable String serviceId,
            @RequestBody Map<String, Object> request) {
        Boolean isHealthy = (Boolean) request.get("isHealthy");
        String status = (String) request.get("status");
        serviceManagementService.updateServiceHealth(serviceId, isHealthy, status);
        return ResponseEntity.ok().build();
    }
}

