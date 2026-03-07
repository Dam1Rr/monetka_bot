package com.monetka.repository;

import com.monetka.model.User;
import com.monetka.model.UserMonthlyProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserMonthlyProfileRepository extends JpaRepository<UserMonthlyProfile, Long> {

    Optional<UserMonthlyProfile> findByUserAndMonthAndYear(User user, int month, int year);

    List<UserMonthlyProfile> findByUserOrderByYearDescMonthDesc(User user);
}