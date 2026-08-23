package com.polybezev.currencybot.repository;

import com.polybezev.currencybot.entity.TradeOrder;
import com.polybezev.currencybot.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link TradeOrder} entities.
 */
@Repository
public interface TradeOrderRepository extends JpaRepository<TradeOrder, Long> {

    /**
     * Returns a paginated list of trade orders for a user, newest first.
     * Used by the trade history screen in the bot UI (typically last 10 orders).
     *
     * @param user     the user whose orders to fetch
     * @param pageable pagination config — pass {@code PageRequest.of(0, 10)} for the last 10
     * @return orders ordered by {@code createdAt} descending
     */
    List<TradeOrder> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
}
