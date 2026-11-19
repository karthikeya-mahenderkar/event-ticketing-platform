package com.devtiro.tickets.controllers;

import static com.devtiro.tickets.util.JwtUtil.parseUserId;

import com.devtiro.tickets.services.TicketTypeService;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping(path = "/api/v1/events/{eventId}/ticket-types")
public class TicketTypeController {

  private final TicketTypeService ticketTypeService;

  /**
   * Purchases a ticket. Clients may send an {@code Idempotency-Key} header (e.g. a UUID per
   * checkout attempt); retrying with the same key never buys a second ticket.
   */
  @PostMapping(path = "/{ticketTypeId}/tickets")
  public ResponseEntity<Void> purchaseTicket(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID ticketTypeId,
      @RequestHeader(name = "Idempotency-Key", required = false)
      @Size(max = 100) String idempotencyKey
  ) {
    UUID userId = parseUserId(jwt);
    try {
      ticketTypeService.purchaseTicket(userId, ticketTypeId, idempotencyKey);
    } catch (DataIntegrityViolationException ex) {
      // Two retries with the same key raced; the unique constraint let exactly one through.
      if (idempotencyKey == null || !ticketTypeService.hasPurchase(userId, idempotencyKey)) {
        throw ex;
      }
    }
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

}
