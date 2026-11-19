package com.devtiro.tickets.services.impl;

import com.devtiro.tickets.domain.entities.QrCode;
import com.devtiro.tickets.domain.entities.QrCodeStatusEnum;
import com.devtiro.tickets.domain.entities.Ticket;
import com.devtiro.tickets.domain.entities.TicketValidation;
import com.devtiro.tickets.domain.entities.TicketValidationMethod;
import com.devtiro.tickets.domain.entities.TicketValidationStatusEnum;
import com.devtiro.tickets.exceptions.QrCodeNotFoundException;
import com.devtiro.tickets.exceptions.TicketNotFoundException;
import com.devtiro.tickets.repositories.QrCodeRepository;
import com.devtiro.tickets.repositories.TicketRepository;
import com.devtiro.tickets.repositories.TicketValidationRepository;
import com.devtiro.tickets.services.TicketValidationService;
import jakarta.transaction.Transactional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Transactional
public class TicketValidationServiceImpl implements TicketValidationService {

  private final QrCodeRepository qrCodeRepository;
  private final TicketValidationRepository ticketValidationRepository;
  private final TicketRepository ticketRepository;

  @Override
  public TicketValidation validateTicketByQrCode(UUID qrCodeId) {
    QrCode qrCode = qrCodeRepository.findByIdAndStatus(qrCodeId, QrCodeStatusEnum.ACTIVE)
        .orElseThrow(() -> new QrCodeNotFoundException(
            String.format(
                "QR Code with ID %s was not found", qrCodeId
            )
        ));

    // Lock the ticket row so two simultaneous scans are validated one after another.
    Ticket ticket = ticketRepository.findByIdForUpdate(qrCode.getTicket().getId())
        .orElseThrow(TicketNotFoundException::new);

    return validateTicket(ticket, TicketValidationMethod.QR_SCAN);
  }

  private TicketValidation validateTicket(Ticket ticket,
      TicketValidationMethod ticketValidationMethod) {
    TicketValidation ticketValidation = new TicketValidation();
    ticketValidation.setTicket(ticket);
    ticketValidation.setValidationMethod(ticketValidationMethod);

    // Query the database (not the cached collection) so a scan that committed while we
    // waited for the lock is seen. Only the first successful scan is VALID.
    boolean alreadyValidated = ticketValidationRepository.existsByTicketIdAndStatus(
        ticket.getId(), TicketValidationStatusEnum.VALID);
    TicketValidationStatusEnum ticketValidationStatus = alreadyValidated
        ? TicketValidationStatusEnum.INVALID
        : TicketValidationStatusEnum.VALID;

    ticketValidation.setStatus(ticketValidationStatus);

    return ticketValidationRepository.save(ticketValidation);
  }

  @Override
  public TicketValidation validateTicketManually(UUID ticketId) {
    Ticket ticket = ticketRepository.findByIdForUpdate(ticketId)
        .orElseThrow(TicketNotFoundException::new);
    return validateTicket(ticket, TicketValidationMethod.MANUAL);
  }
}
