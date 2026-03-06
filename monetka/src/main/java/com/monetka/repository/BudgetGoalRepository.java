package com.monetka.repository;

import com.monetka.model.BudgetGoal;
import com.monetka.model.Category;
import com.monetka.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetGoalRepository extends JpaRepository<BudgetGoal, Long> {

    List<BudgetGoal> findAllByUser(User user);

    Optional<BudgetGoal> findByUserAndCategory(User user, Category category);

    void deleteByUserAndCategory(User user, Category category);
}