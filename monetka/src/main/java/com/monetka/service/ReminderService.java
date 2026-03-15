package com.monetka.service;

import com.monetka.model.User;
import com.monetka.model.UserReminder;
import com.monetka.repository.UserReminderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReminderService {

    private final UserReminderRepository reminderRepository;

    public ReminderService(UserReminderRepository reminderRepository) {
        this.reminderRepository = reminderRepository;
    }

    /** Получить настройки напоминаний (создаёт если нет) */
    @Transactional
    public UserReminder getOrCreate(User user) {
        return reminderRepository.findByUser(user).orElseGet(() -> {
            UserReminder r = new UserReminder(user);
            return reminderRepository.save(r);
        });
    }

    /** Включить / выключить напоминания */
    @Transactional
    public UserReminder setEnabled(User user, boolean enabled) {
        UserReminder r = getOrCreate(user);
        r.setEnabled(enabled);
        return reminderRepository.save(r);
    }

    /** Установить время утреннего напоминания */
    @Transactional
    public UserReminder setMorningHour(User user, int hour) {
        UserReminder r = getOrCreate(user);
        r.setHourMorning(hour);
        r.setMorningEnabled(true);
        return reminderRepository.save(r);
    }

    /** Установить время вечернего напоминания */
    @Transactional
    public UserReminder setEveningHour(User user, int hour) {
        UserReminder r = getOrCreate(user);
        r.setHourEvening(hour);
        r.setEveningEnabled(true);
        return reminderRepository.save(r);
    }

    /** Выключить утреннее */
    @Transactional
    public UserReminder toggleMorning(User user) {
        UserReminder r = getOrCreate(user);
        r.setMorningEnabled(!r.isMorningEnabled());
        return reminderRepository.save(r);
    }

    /** Выключить вечернее */
    @Transactional
    public UserReminder toggleEvening(User user) {
        UserReminder r = getOrCreate(user);
        r.setEveningEnabled(!r.isEveningEnabled());
        return reminderRepository.save(r);
    }

    /** Для scheduler — кому слать утром в данный час */
    @Transactional(readOnly = true)
    public List<UserReminder> getMorningAt(int hour) {
        return reminderRepository.findEnabledMorningAt(hour);
    }

    /** Для scheduler — кому слать вечером в данный час */
    @Transactional(readOnly = true)
    public List<UserReminder> getEveningAt(int hour) {
        return reminderRepository.findEnabledEveningAt(hour);
    }

    /** Строка статуса для отображения пользователю */
    public String statusText(UserReminder r) {
        if (!r.isEnabled()) return "🔕 Напоминания выключены";
        StringBuilder sb = new StringBuilder("🔔 Напоминания включены\n\n");
        if (r.isMorningEnabled())
            sb.append("☀️ Утром: *").append(String.format("%02d:00", r.getHourMorning())).append("*\n");
        else
            sb.append("☀️ Утром: _выключено_\n");
        if (r.isEveningEnabled())
            sb.append("🌙 Вечером: *").append(String.format("%02d:00", r.getHourEvening())).append("*\n");
        else
            sb.append("🌙 Вечером: _выключено_\n");
        return sb.toString();
    }
}