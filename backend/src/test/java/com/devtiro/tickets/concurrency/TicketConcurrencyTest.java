package com.devtiro.tickets.concurrency;

import static com.devtiro.tickets.concurrency.ConcurrencyTestSupport.runConcurrently;
import static org.assertj.core.api.Assertions.assertThat;

import com.devtiro.tickets.concurrency.ConcurrencyTestSupport.Outcome;
import com.devtiro.tickets.domain.entities.Ticket;
import com.devtiro.tickets.domain.entities.TicketType;
import com.devtiro.tickets.domain.entities.TicketValidation;
import com.devtiro.tickets.domain.entities.TicketValidationStatusEnum;
import com.devtiro.tickets.domain.entities.User;
import com.devtiro.tickets.exceptions.TicketsSoldOutException;
import com.devtiro.tickets.repositories.TicketRepository;
import com.devtiro.tickets.repositories.TicketTypeRepository;
import com.devtiro.tickets.repositories.TicketValidationRepository;
import com.devtiro.tickets.repositories.UserRepository;
import com.devtiro.tickets.services.TicketTypeService;
import com.devtiro.tickets.services.TicketValidationService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Proves the concurrency guarantees by firing real simultaneous requests at the services
 * (each call runs in its own transaction on its own thread, released together by a latch).
 */
@SpringBootTest
class TicketConcurrencyTest {

  @Autowired TicketTypeService ticketTypeService;
  @Autowired TicketValidationService ticketValidationService;
  @Autowired TicketTypeRepository ticketTypeRepository;
  @Autowired TicketRepository ticketRepository;
  @Autowired TicketValidationRepository ticketValidationRepository;
  @Autowired UserRepository userRepository;

  @Test
  @DisplayName("20 buyers racing for 5 tickets: exactly 5 tickets are issued, never more")
  void concurrentPurchasesNeverOversell() throws Exception {
    TicketType ticketType = createTicketType(5);
    List<User> buyers = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      buyers.add(createUser());
    }

    List<Callable<Ticket>> purchases = new ArrayList<>();
    for (User buyer : buyers) {
      purchases.add(() -> ticketTypeService.purchaseTicket(buyer.getId(), ticketType.getId()));
    }
    List<Outcome<Ticket>> outcomes = runConcurrently(purchases);

    long succeeded = outcomes.stream().filter(Outcome::succeeded).count();
    long soldOut = outcomes.stream()
        .filter(o -> o.error() instanceof TicketsSoldOutException).count();

    assertThat(succeeded).isEqualTo(5);
    assertThat(soldOut).isEqualTo(15);
    assertThat(ticketRepository.countByTicketTypeId(ticketType.getId())).isEqualTo(5);
    assertThat(ticketTypeRepository.findById(ticketType.getId()).orElseThrow().getSoldCount())
        .isEqualTo(5);
  }

  @Test
  @DisplayName("A purchase retried 5 times at once with the same Idempotency-Key buys exactly 1 ticket")
  void concurrentRetriesWithSameIdempotencyKeyBuyOnce() throws Exception {
    TicketType ticketType = createTicketType(10);
    User buyer = createUser();
    String key = UUID.randomUUID().toString();

    List<Callable<Ticket>> retries = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      retries.add(() -> ticketTypeService.purchaseTicket(buyer.getId(), ticketType.getId(), key));
    }
    List<Outcome<Ticket>> outcomes = runConcurrently(retries);

    // Every attempt either returned the ticket or lost the race on the unique constraint
    // (the controller turns that into a successful, idempotent response).
    assertThat(outcomes).allSatisfy(o ->
        assertThat(o.succeeded() || o.error() instanceof DataIntegrityViolationException)
            .as("unexpected failure: %s", o.error())
            .isTrue());
    assertThat(ticketRepository.countByTicketTypeId(ticketType.getId())).isEqualTo(1);
    assertThat(ticketTypeRepository.findById(ticketType.getId()).orElseThrow().getSoldCount())
        .as("losing retries must roll back their inventory reservation")
        .isEqualTo(1);

    // A later sequential retry returns the same ticket instead of buying another.
    Ticket first = ticketRepository.findByPurchaserIdAndIdempotencyKey(buyer.getId(), key)
        .orElseThrow();
    Ticket retried = ticketTypeService.purchaseTicket(buyer.getId(), ticketType.getId(), key);
    assertThat(retried.getId()).isEqualTo(first.getId());
    assertThat(ticketRepository.countByTicketTypeId(ticketType.getId())).isEqualTo(1);
  }

  @Test
  @DisplayName("10 staff scanning the same ticket at once: exactly 1 scan is VALID")
  void concurrentValidationsAdmitTicketOnce() throws Exception {
    TicketType ticketType = createTicketType(1);
    User buyer = createUser();
    Ticket ticket = ticketTypeService.purchaseTicket(buyer.getId(), ticketType.getId());

    List<Callable<TicketValidation>> scans = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      scans.add(() -> ticketValidationService.validateTicketManually(ticket.getId()));
    }
    List<Outcome<TicketValidation>> outcomes = runConcurrently(scans);

    assertThat(outcomes).allSatisfy(o -> assertThat(o.error()).isNull());
    long valid = outcomes.stream()
        .filter(o -> o.result().getStatus() == TicketValidationStatusEnum.VALID).count();
    assertThat(valid).isEqualTo(1);
    assertThat(ticketValidationRepository.countByTicketIdAndStatus(
        ticket.getId(), TicketValidationStatusEnum.VALID)).isEqualTo(1);
    assertThat(ticketValidationRepository.countByTicketIdAndStatus(
        ticket.getId(), TicketValidationStatusEnum.INVALID)).isEqualTo(9);
  }

  private TicketType createTicketType(int totalAvailable) {
    TicketType ticketType = new TicketType();
    ticketType.setName("General Admission");
    ticketType.setPrice(499.0);
    ticketType.setTotalAvailable(totalAvailable);
    return ticketTypeRepository.saveAndFlush(ticketType);
  }

  private User createUser() {
    User user = new User();
    UUID id = UUID.randomUUID();
    user.setId(id);
    user.setName("buyer-" + id);
    user.setEmail(id + "@example.com");
    return userRepository.saveAndFlush(user);
  }
}
