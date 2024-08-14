package com.smsweb.sms.repositories.universal;

import com.smsweb.sms.models.universal.Discounthead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DiscountRepository extends JpaRepository<Discounthead, Long> {
    Discounthead findByDiscountName(String discountName);
    List<Discounthead> findByDiscountNameNotContains(String name);
}
