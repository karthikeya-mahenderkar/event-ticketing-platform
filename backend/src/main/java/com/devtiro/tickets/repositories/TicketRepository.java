package com.devtiro.tickets.repositories;

import com.devtiro.tickets.domain.entities.Ticket;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

  int countByTicketTypeId(UUID ticketTypeId);

  Page<Ticket> findByPurchaserId(UUID purchaserId, Pageable pageable);

  Optional<Ticket> findByIdAndPurchaserId(UUID id, UUID purchaserId);

  Optional<Ticket> findByPurchaserIdAndIdempotencyKey(UUID purchaserId, String idempotencyKey);

  boolean existsByPurchaserIdAndIdempotencyKey(UUID purchaserId, String idempotencyKey);

  /**
   * Loads a ticket with a row lock ({@code SELECT ... FOR UPDATE}) so concurrent validations of
   * the same ticket run one after another.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT t FROM Ticket t WHERE t.id = :id")
  Optional<Ticket> findByIdForUpdate(@Param("id") UUID id);
}
