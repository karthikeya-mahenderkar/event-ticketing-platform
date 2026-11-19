package com.devtiro.tickets.services.impl;

import com.devtiro.tickets.domain.entities.Ticket;
import com.devtiro.tickets.domain.entities.TicketStatusEnum;
import com.devtiro.tickets.domain.entities.User;
import com.devtiro.tickets.exceptions.TicketTypeNotFoundException;
import com.devtiro.tickets.exceptions.TicketsSoldOutException;
import com.devtiro.tickets.exceptions.UserNotFoundException;
import com.devtiro.tickets.repositories.TicketRepository;
import com.devtiro.tickets.repositories.TicketTypeRepository;
import com.devtiro.tickets.repositories.UserRepository;
import com.devtiro.tickets.services.QrCodeService;
import com.devtiro.tickets.services.TicketTypeService;
import jakarta.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TicketTypeServiceImpl implements TicketTypeService {

  private final UserRepository userRepository;
  private final TicketTypeRepository ticketTypeRepository;
  private final TicketRepository ticketRepository;
  private final QrCodeService qrCodeService;

  @Override
  @Transactional
  public Ticket purchaseTicket(UUID userId, UUID ticketTypeId) {
    return purchaseTicket(userId, ticketTypeId, null);
  }

  @Override
  @Transactional
  public Ticket purchaseTicket(UUID userId, UUID ticketTypeId, String idempotencyKey) {
    // A retried request with the same key returns the ticket it already bought.
    if (idempotencyKey != null) {
      Optional<Ticket> existing =
          ticketRepository.findByPurchaserIdAndIdempotencyKey(userId, idempotencyKey);
      if (existing.isPresent()) {
        return existing.get();
      }
    }

    User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(
        String.format("User with ID %s was not found", userId)
    ));

    // Reserve inventory with one atomic conditional UPDATE instead of lock + count + check.
    if (ticketTypeRepository.reserveTicket(ticketTypeId) == 0) {
      if (!ticketTypeRepository.existsById(ticketTypeId)) {
        throw new TicketTypeNotFoundException(
            String.format("Ticket type with ID %s was not found", ticketTypeId));
      }
      throw new TicketsSoldOutException();
    }

    Ticket ticket = new Ticket();
    ticket.setStatus(TicketStatusEnum.PURCHASED);
    ticket.setTicketType(ticketTypeRepository.getReferenceById(ticketTypeId));
    ticket.setPurchaser(user);
    ticket.setIdempotencyKey(idempotencyKey);

    // Flush now so a concurrent duplicate idempotency key fails here, inside this
    // transaction, which also rolls back the inventory reservation above.
    Ticket savedTicket = ticketRepository.saveAndFlush(ticket);
    qrCodeService.generateQrCode(savedTicket);

    return ticketRepository.save(savedTicket);
  }

  @Override
  public boolean hasPurchase(UUID userId, String idempotencyKey) {
    return ticketRepository.existsByPurchaserIdAndIdempotencyKey(userId, idempotencyKey);
  }
}
