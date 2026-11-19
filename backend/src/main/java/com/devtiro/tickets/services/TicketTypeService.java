package com.devtiro.tickets.services;

import com.devtiro.tickets.domain.entities.Ticket;
import java.util.UUID;

public interface TicketTypeService {

  Ticket purchaseTicket(UUID userId, UUID ticketTypeId);

  /**
   * Purchases one ticket. When {@code idempotencyKey} is given, repeating the call with the
   * same key returns the original ticket instead of buying another one.
   */
  Ticket purchaseTicket(UUID userId, UUID ticketTypeId, String idempotencyKey);

  boolean hasPurchase(UUID userId, String idempotencyKey);
}
