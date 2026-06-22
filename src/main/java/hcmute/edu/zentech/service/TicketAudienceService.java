package hcmute.edu.zentech.service;

import hcmute.edu.zentech.model.Incident;
import hcmute.edu.zentech.model.Role;
import hcmute.edu.zentech.model.Ticket;
import hcmute.edu.zentech.repository.ActivityLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketAudienceService {

    private final ActivityLogRepository activityLogRepository;

    public List<String> getAffectedUserEmails(Ticket ticket) {
        Set<String> emails = new LinkedHashSet<>();
        if (ticket.getCreatedBy() != null
                && ticket.getCreatedBy().getRole() == Role.CUSTOMER
                && ticket.getCreatedBy().getEmail() != null) {
            emails.add(ticket.getCreatedBy().getEmail());
        }
        if (ticket.getIncident() != null) {
            Incident incident = ticket.getIncident();
            if (incident.getUser() != null
                    && incident.getUser().getRole() == Role.CUSTOMER
                    && incident.getUser().getEmail() != null) {
                emails.add(incident.getUser().getEmail());
            }
            UUID incidentId = incident.getId();
            try {
                List<String> systemUserEmails = activityLogRepository.findUserEmailsByTargetTypeAndTargetIdAndSystemArea(
                        "Incident",
                        incidentId.toString()
                );
                emails.addAll(systemUserEmails);
            } catch (Exception e) {
                log.error("Failed to fetch affected user emails for linked incident: {}", e.getMessage());
            }
        }
        return new ArrayList<>(emails);
    }

    public boolean matchesCustomerEmail(Ticket ticket, String normalizedEmail) {
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            return false;
        }
        if (ticket.getCreatedBy() != null && normalizedEmail.equalsIgnoreCase(ticket.getCreatedBy().getEmail())) {
            return true;
        }
        if (ticket.getIncident() != null
                && ticket.getIncident().getUser() != null
                && normalizedEmail.equalsIgnoreCase(ticket.getIncident().getUser().getEmail())) {
            return true;
        }
        return getAffectedUserEmails(ticket).stream().anyMatch(email -> normalizedEmail.equalsIgnoreCase(email));
    }
}
