package com.monetka.repository;

import com.monetka.model.Subscription;
import com.monetka.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findByUserAndActiveTrue(User user);

    List<Subscription> findByActiveTrueAndDayOfMonth(int dayOfMonth);
}
