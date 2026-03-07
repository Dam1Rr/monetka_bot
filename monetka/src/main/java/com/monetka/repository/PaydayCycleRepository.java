package com.monetka.repository;

import com.monetka.model.PaydayCycle;
import com.monetka.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaydayCycleRepository extends JpaRepository<PaydayCycle, Long> {

    Optional<PaydayCycle> findByUserAndActiveTrue(User user);
}