package com.monetka.service;

import com.monetka.model.Debt;
import com.monetka.model.User;
import com.monetka.repository.DebtRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
public class DebtService {

    private static final Logger  log     = LoggerFactory.getLogger(DebtService.class);
    private static final ZoneId  BISHKEK = ZoneId.of("Asia/Bishkek");

    private final DebtRepository debtRepository;

    public DebtService(DebtRepository debtRepository) {
        this.debtRepository = debtRepository;
    }

    // ────────────────────────────────────────────────────────────────
    // Создание долга
    // ────────────────────────────────────────────────────────────────

    @Transactional
    public Debt create(User user, String name, String triggerWord,
                       BigDecimal totalAmount, BigDecimal monthlyPayment,
                       BigDecimal alreadyPaid) {
        BigDecimal remaining = totalAmount.subtract(alreadyPaid.max(BigDecimal.ZERO));
        Debt d = new Debt();
        d.setUser(user);
        d.setName(name.trim());
        d.setTriggerWord(triggerWord.toLowerCase().trim());
        d.setTotalAmount(totalAmount);
        d.setMonthlyPayment(monthlyPayment);
        d.setRemaining(remaining.max(BigDecimal.ZERO));
        d.setCreatedAt(LocalDateTime.now(BISHKEK));
        return debtRepository.save(d);
    }

    // ────────────────────────────────────────────────────────────────
    // Применить платёж — возвращает Optional<Debt> если нашли триггер
    // ────────────────────────────────────────────────────────────────

    @Transactional
    public Optional<Debt> applyPayment(User user, String text, BigDecimal amount) {
        String normalized = text.toLowerCase().trim();
        // Ищем совпадение триггера в тексте расхода
        List<Debt> active = debtRepository.findActiveByUser(user);
        for (Debt d : active) {
            if (normalized.contains(d.getTriggerWord())) {
                BigDecimal newRemaining = d.getRemaining().subtract(amount);
                if (newRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                    // Долг закрыт!
                    d.setRemaining(BigDecimal.ZERO);
                    d.setClosedAt(LocalDateTime.now(BISHKEK));
                    log.info("Debt '{}' closed for user {}", d.getName(), user.getTelegramId());
                } else {
                    d.setRemaining(newRemaining);
                }
                return Optional.of(debtRepository.save(d));
            }
        }
        return Optional.empty(); // Триггер не найден — просто расход
    }

    // ────────────────────────────────────────────────────────────────
    // Чтение
    // ────────────────────────────────────────────────────────────────

    public List<Debt> getActive(User user)  { return debtRepository.findActiveByUser(user); }
    public List<Debt> getAll(User user)     { return debtRepository.findAllByUser(user); }

    public Optional<Debt> findById(Long id) { return debtRepository.findById(id); }

    @Transactional
    public void delete(Long id) { debtRepository.deleteById(id); }

    // ────────────────────────────────────────────────────────────────
    // Статистика
    // ────────────────────────────────────────────────────────────────

    /** Сумма всего что осталось выплатить */
    public BigDecimal totalRemaining(User user) {
        return getActive(user).stream()
                .map(Debt::getRemaining)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Сумма платежей этого месяца (active monthly payments) */
    public BigDecimal monthlyObligation(User user) {
        return getActive(user).stream()
                .map(Debt::getMonthlyPayment)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Всего выплачено по всем долгам за всё время */
    public BigDecimal totalEverPaid(User user) {
        // По активным: totalAmount - remaining
        BigDecimal fromActive = getActive(user).stream()
                .map(d -> d.getTotalAmount().subtract(d.getRemaining()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // По закрытым: totalAmount целиком
        BigDecimal fromClosed = debtRepository.sumClosedTotal(user);
        return fromActive.add(fromClosed);
    }
}