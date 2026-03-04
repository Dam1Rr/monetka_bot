package com.monetka.repository;

import com.monetka.model.LearnedKeyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LearnedKeywordRepository extends JpaRepository<LearnedKeyword, Long> {

    /** Ищем сначала персональное слово пользователя, потом глобальное */
    @Query("SELECT lk FROM LearnedKeyword lk " +
            "WHERE lk.keyword = :keyword " +
            "AND (lk.userId = :userId OR lk.userId IS NULL) " +
            "ORDER BY lk.userId DESC NULLS LAST")
    List<LearnedKeyword> findByKeywordAndUser(
            @Param("keyword") String keyword,
            @Param("userId")  Long userId
    );

    Optional<LearnedKeyword> findByKeywordAndUserId(String keyword, Long userId);

    List<LearnedKeyword> findAllByUserId(Long userId);
}