package com.devtiro.tickets.repositories;

import com.devtiro.tickets.domain.entities.TicketType;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketTypeRepository extends JpaRepository<TicketType, UUID> {

  /**
   * Atomically reserves one ticket of the given type.
   *
   * <p>A single conditional UPDATE: the database increments {@code sold_count} only while
   * tickets remain. Concurrent buyers of the same row are serialised by the database's own
   * row lock for the duration of this one statement, and each re-checks the condition against
   * the latest committed value, so the count can never exceed {@code total_available}.
   *
   * @return 1 if a ticket was reserved, 0 if the type is sold out (or does not exist)
   */
  @Modifying
  @Query("""
      UPDATE TicketType tt
         SET tt.soldCount = tt.soldCount + 1
       WHERE tt.id = :id
         AND tt.soldCount < tt.totalAvailable
      """)
  int reserveTicket(@Param("id") UUID id);
}
