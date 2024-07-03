package com.smsweb.sms.repositories.universal;

import com.smsweb.sms.models.universal.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}
