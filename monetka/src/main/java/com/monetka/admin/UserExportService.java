package com.monetka.admin;

import com.monetka.model.User;
import com.monetka.model.enums.UserStatus;
import com.monetka.repository.TransactionRepository;
import com.monetka.repository.UserRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class UserExportService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final UserRepository         userRepository;
    private final TransactionRepository  transactionRepository;
    private final ActivityStatsService   activityStatsService;

    public UserExportService(UserRepository userRepository,
                             TransactionRepository transactionRepository,
                             ActivityStatsService activityStatsService) {
        this.userRepository        = userRepository;
        this.transactionRepository = transactionRepository;
        this.activityStatsService  = activityStatsService;
    }

    @Transactional(readOnly = true)
    public byte[] generateUsersXlsx() throws IOException {
        List<User> active  = userRepository.findAllByStatus(UserStatus.ACTIVE);
        List<User> pending = userRepository.findAllByStatus(UserStatus.PENDING);
        List<User> blocked = userRepository.findAllByStatus(UserStatus.BLOCKED);

        XSSFWorkbook wb = new XSSFWorkbook();

        // ── Styles ──────────────────────────────────────────────
        XSSFCellStyle titleStyle   = makeTitleStyle(wb);
        XSSFCellStyle headerStyle  = makeHeaderStyle(wb);
        XSSFCellStyle activeStyle  = makeRowStyle(wb, "D8F5E8");  // green tint
        XSSFCellStyle pendingStyle = makeRowStyle(wb, "FFF9DB");  // yellow tint
        XSSFCellStyle blockedStyle = makeRowStyle(wb, "FDECEA");  // red tint
        XSSFCellStyle altStyle     = makeRowStyle(wb, "F8F8F8");  // grey alt
        XSSFCellStyle totalStyle   = makeTotalStyle(wb);

        // ── Sheet 1: All users ───────────────────────────────────
        buildSheet(wb, "Все пользователи",
                List.of(active, pending, blocked),
                List.of(activeStyle, pendingStyle, blockedStyle),
                titleStyle, headerStyle, totalStyle, altStyle, true);

        // ── Sheet 2: Active only ─────────────────────────────────
        buildSheet(wb, "✅ Активные",
                List.of(active),
                List.of(activeStyle),
                titleStyle, headerStyle, totalStyle, altStyle, false);

        // ── Sheet 3: Pending ─────────────────────────────────────
        if (!pending.isEmpty()) {
            buildSheet(wb, "⏳ Заявки",
                    List.of(pending),
                    List.of(pendingStyle),
                    titleStyle, headerStyle, totalStyle, altStyle, false);
        }

        // ── Sheet 4: Blocked ─────────────────────────────────────
        if (!blocked.isEmpty()) {
            buildSheet(wb, "🚫 Заблокированные",
                    List.of(blocked),
                    List.of(blockedStyle),
                    titleStyle, headerStyle, totalStyle, altStyle, false);
        }

        // ── Sheet 5: Activity dashboard ─────────────────────────
        buildActivitySheet(wb, titleStyle, headerStyle, totalStyle, altStyle);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        return out.toByteArray();
    }

    // ================================================================

    private void buildSheet(XSSFWorkbook wb, String sheetName,
                            List<List<User>> groups, List<XSSFCellStyle> groupStyles,
                            XSSFCellStyle titleStyle, XSSFCellStyle headerStyle,
                            XSSFCellStyle totalStyle, XSSFCellStyle altStyle,
                            boolean showGroupLabel) {

        XSSFSheet sheet = wb.createSheet(sheetName);

        // Column widths
        sheet.setColumnWidth(0, 5 * 256);    // №
        sheet.setColumnWidth(1, 22 * 256);   // Имя
        sheet.setColumnWidth(2, 18 * 256);   // @username
        sheet.setColumnWidth(3, 18 * 256);   // Telegram ID
        sheet.setColumnWidth(4, 14 * 256);   // Статус
        sheet.setColumnWidth(5, 16 * 256);   // Баланс
        sheet.setColumnWidth(6, 18 * 256);   // Дата регистрации

        int rowIdx = 0;

        // Title row
        Row title = sheet.createRow(rowIdx++);
        title.setHeightInPoints(28);
        Cell titleCell = title.createCell(0);
        titleCell.setCellValue("Monetka — " + sheetName);
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));

        // Date row
        Row dateRow = sheet.createRow(rowIdx++);
        Cell dateCell = dateRow.createCell(0);
        dateCell.setCellValue("Выгрузка от " + LocalDate.now().format(FMT));
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 6));
        rowIdx++; // gap

        // Headers
        Row header = sheet.createRow(rowIdx++);
        header.setHeightInPoints(20);
        String[] cols = {"№", "Имя", "@username", "Telegram ID", "Статус", "Баланс (сом)", "Регистрация"};
        for (int c = 0; c < cols.length; c++) {
            Cell hc = header.createCell(c);
            hc.setCellValue(cols[c]);
            hc.setCellStyle(headerStyle);
        }

        int counter = 1;
        for (int g = 0; g < groups.size(); g++) {
            List<User> users = groups.get(g);
            XSSFCellStyle rowStyle = groupStyles.get(g);

            if (showGroupLabel && !users.isEmpty()) {
                Row labelRow = sheet.createRow(rowIdx++);
                Cell lc = labelRow.createCell(0);
                lc.setCellValue(statusLabel(users.get(0).getStatus()) +
                        " (" + users.size() + ")");
                sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, 6));
            }

            for (User u : users) {
                Row row = sheet.createRow(rowIdx++);
                row.setHeightInPoints(18);

                XSSFCellStyle style = (counter % 2 == 0) ? altStyle : rowStyle;

                setCell(row, 0, String.valueOf(counter++), style);
                setCell(row, 1, u.getDisplayName(), style);
                setCell(row, 2, u.getUsername() != null ? "@" + u.getUsername() : "—", style);
                setCell(row, 3, String.valueOf(u.getTelegramId()), style);
                setCell(row, 4, statusLabel(u.getStatus()), style);
                setCell(row, 5, u.getBalance() != null
                        ? String.format("%,.2f", u.getBalance()) : "0.00", style);
                setCell(row, 6, u.getCreatedAt() != null
                        ? u.getCreatedAt().toLocalDate().format(FMT) : "—", style);
            }
        }

        // Totals row
        int total = groups.stream().mapToInt(List::size).sum();
        Row totRow = sheet.createRow(rowIdx + 1);
        totRow.setHeightInPoints(20);
        Cell totLabel = totRow.createCell(0);
        totLabel.setCellValue("Итого пользователей:");
        totLabel.setCellStyle(totalStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowIdx + 1, rowIdx + 1, 0, 3));
        Cell totVal = totRow.createCell(4);
        totVal.setCellValue(total);
        totVal.setCellStyle(totalStyle);

        // Freeze header rows
        sheet.createFreezePane(0, 4);
    }

    // ── Activity dashboard sheet ─────────────────────────────────────

    private void buildActivitySheet(XSSFWorkbook wb,
                                    XSSFCellStyle titleStyle,
                                    XSSFCellStyle headerStyle,
                                    XSSFCellStyle totalStyle,
                                    XSSFCellStyle altStyle) {
        ActivityStatsService.ActivitySnapshot snap = activityStatsService.getSnapshot();
        XSSFSheet sheet = wb.createSheet("\uD83D\uDCCA Активность");

        sheet.setColumnWidth(0, 28 * 256);
        sheet.setColumnWidth(1, 18 * 256);
        sheet.setColumnWidth(2, 18 * 256);
        sheet.setColumnWidth(3, 18 * 256);
        sheet.setColumnWidth(4, 18 * 256);
        sheet.setColumnWidth(5, 18 * 256);

        int r = 0;

        // Title
        Row title = sheet.createRow(r++);
        title.setHeightInPoints(28);
        Cell tc = title.createCell(0);
        tc.setCellValue("Monetka — Аналитика активности");
        tc.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));

        // Date
        Row dateRow = sheet.createRow(r++);
        Cell dc = dateRow.createCell(0);
        dc.setCellValue("Отчёт за: " + LocalDate.now().format(FMT));
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 5));
        r++; // gap

        // ── Block 1: Summary KPIs ────────────────────────────────
        XSSFCellStyle kpiHeader = makeKpiHeaderStyle(wb, "2E6DA4");
        XSSFCellStyle kpiGreen  = makeKpiStyle(wb, "D8F5E8");
        XSSFCellStyle kpiYellow = makeKpiStyle(wb, "FFF9DB");
        XSSFCellStyle kpiRed    = makeKpiStyle(wb, "FDECEA");
        XSSFCellStyle kpiBlue   = makeKpiStyle(wb, "E8F0FB");

        Row kpiTitle = sheet.createRow(r++);
        Cell kpiTc = kpiTitle.createCell(0);
        kpiTc.setCellValue("📊 Сводка");
        kpiTc.setCellStyle(makeKpiHeaderStyle(wb, "1A3C5E"));
        sheet.addMergedRegion(new CellRangeAddress(r - 1, r - 1, 0, 5));

        // KPI headers
        Row kpiH = sheet.createRow(r++);
        kpiH.setHeightInPoints(20);
        String[] kpiCols = {"Показатель", "Значение"};
        for (int c = 0; c < kpiCols.length; c++) {
            Cell hc = kpiH.createCell(c);
            hc.setCellValue(kpiCols[c]);
            hc.setCellStyle(headerStyle);
        }

        // KPI rows
        Object[][] kpis = {
                {"👥 Всего пользователей (активных)",  snap.totalUsers(),    kpiGreen},
                {"⏳ Заявки (ожидают одобрения)",       snap.pendingUsers(),  kpiYellow},
                {"🚫 Заблокировано",                    snap.blockedUsers(),  kpiRed},
                {"✅ Активны сегодня",                  snap.activeToday(),   kpiGreen},
                {"📅 Активны за 7 дней",                snap.activeWeek(),    kpiBlue},
                {"😴 Неактивны 3+ дня",                 snap.inactive3days(), kpiRed},
                {"💸 Транзакций сегодня",               snap.txToday(),       kpiBlue},
                {"📈 Транзакций за 7 дней",             snap.txWeek(),        kpiBlue},
        };

        for (int i = 0; i < kpis.length; i++) {
            Row row = sheet.createRow(r++);
            row.setHeightInPoints(18);
            XSSFCellStyle st = (XSSFCellStyle) kpis[i][2];
            Cell label = row.createCell(0);
            label.setCellValue((String) kpis[i][0]);
            label.setCellStyle(st);
            Cell val = row.createCell(1);
            val.setCellValue((long) kpis[i][1]);
            val.setCellStyle(st);
        }

        r += 2; // gap

        // ── Block 2: Retention rate ──────────────────────────────
        if (snap.totalUsers() > 0) {
            long retentionPct = snap.activeWeek() * 100 / snap.totalUsers();
            Row retRow = sheet.createRow(r++);
            retRow.setHeightInPoints(20);
            Cell retLabel = retRow.createCell(0);
            retLabel.setCellValue("🎯 Retention (7 дней)");
            retLabel.setCellStyle(retentionPct >= 50 ? kpiGreen : kpiRed);
            Cell retVal = retRow.createCell(1);
            retVal.setCellValue(retentionPct + "%");
            retVal.setCellStyle(retentionPct >= 50 ? kpiGreen : kpiRed);
            r++;
        }

        r++; // gap

        // ── Block 3: Top 5 active users ──────────────────────────
        if (!snap.top5().isEmpty()) {
            Row topTitle = sheet.createRow(r++);
            Cell topTc = topTitle.createCell(0);
            topTc.setCellValue("🔥 Топ активных за 7 дней");
            topTc.setCellStyle(makeKpiHeaderStyle(wb, "1A3C5E"));
            sheet.addMergedRegion(new CellRangeAddress(r - 1, r - 1, 0, 5));

            Row topH = sheet.createRow(r++);
            topH.setHeightInPoints(20);
            String[] topCols = {"#", "Пользователь", "Транзакций"};
            for (int c = 0; c < topCols.length; c++) {
                Cell hc = topH.createCell(c);
                hc.setCellValue(topCols[c]);
                hc.setCellStyle(headerStyle);
            }

            for (int i = 0; i < snap.top5().size(); i++) {
                ActivityStatsService.UserActivity ua = snap.top5().get(i);
                Row row = sheet.createRow(r++);
                row.setHeightInPoints(18);
                XSSFCellStyle st = i % 2 == 0 ? kpiGreen : altStyle;
                Cell num = row.createCell(0); num.setCellValue(i + 1);      num.setCellStyle(st);
                Cell nm  = row.createCell(1); nm.setCellValue(ua.name());   nm.setCellStyle(st);
                Cell cnt = row.createCell(2); cnt.setCellValue(ua.txCount()); cnt.setCellStyle(st);
            }

            r += 2;
        }

        // ── Block 4: Per-user retention table ────────────────────
        if (!snap.retention().isEmpty()) {
            Row retTitle = sheet.createRow(r++);
            Cell rtc = retTitle.createCell(0);
            rtc.setCellValue("📋 Активность по пользователям");
            rtc.setCellStyle(makeKpiHeaderStyle(wb, "1A3C5E"));
            sheet.addMergedRegion(new CellRangeAddress(r - 1, r - 1, 0, 5));

            Row retH = sheet.createRow(r++);
            retH.setHeightInPoints(20);
            String[] retCols = {"Пользователь", "@username", "Дата регистрации",
                    "Всего транзакций", "Последняя активность", "Дней назад"};
            for (int c = 0; c < retCols.length; c++) {
                Cell hc = retH.createCell(c);
                hc.setCellValue(retCols[c]);
                hc.setCellStyle(headerStyle);
            }

            for (int i = 0; i < snap.retention().size(); i++) {
                ActivityStatsService.UserRetention u = snap.retention().get(i);
                Row row = sheet.createRow(r++);
                row.setHeightInPoints(18);

                XSSFCellStyle st;
                if (u.daysAgo() < 0)      st = kpiYellow; // never active
                else if (u.daysAgo() == 0) st = kpiGreen;  // active today
                else if (u.daysAgo() <= 2) st = kpiBlue;   // active this week
                else                       st = kpiRed;     // inactive

                Cell n  = row.createCell(0); n.setCellValue(u.name());  n.setCellStyle(st);
                Cell un = row.createCell(1);
                un.setCellValue(u.username() != null ? "@" + u.username() : "—");
                un.setCellStyle(st);
                Cell reg = row.createCell(2);
                reg.setCellValue(u.registeredAt() != null ? u.registeredAt().format(FMT) : "—");
                reg.setCellStyle(st);
                Cell txc = row.createCell(3); txc.setCellValue(u.txCount()); txc.setCellStyle(st);
                Cell la  = row.createCell(4);
                la.setCellValue(u.lastActivity() != null ? u.lastActivity().format(FMT) : "Нет");
                la.setCellStyle(st);
                Cell da  = row.createCell(5);
                da.setCellValue(u.daysAgo() >= 0 ? u.daysAgo() : -1);
                da.setCellStyle(st);
            }
        }

        sheet.createFreezePane(0, 4);
    }

    private XSSFCellStyle makeKpiHeaderStyle(XSSFWorkbook wb, String hex) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 12);
        f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(hexColor(wb, hex));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.LEFT);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setIndention((short) 1);
        setBorder(s);
        return s;
    }

    private XSSFCellStyle makeKpiStyle(XSSFWorkbook wb, String hex) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setFontHeightInPoints((short) 10);
        s.setFont(f);
        s.setFillForegroundColor(hexColor(wb, hex));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(s);
        return s;
    }

    // ── Style builders ───────────────────────────────────────────────

    private XSSFCellStyle makeTitleStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 16);
        f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(hexColor(wb, "1A3C5E"));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.LEFT);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setIndention((short) 1);
        return s;
    }

    private XSSFCellStyle makeHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 11);
        f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(hexColor(wb, "2E6DA4"));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(s);
        return s;
    }

    private XSSFCellStyle makeRowStyle(XSSFWorkbook wb, String hex) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setFontHeightInPoints((short) 10);
        s.setFont(f);
        s.setFillForegroundColor(hexColor(wb, hex));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(s);
        return s;
    }

    private XSSFCellStyle makeTotalStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 11);
        s.setFont(f);
        s.setFillForegroundColor(hexColor(wb, "1A3C5E"));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont wf = wb.createFont();
        wf.setBold(true);
        wf.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(wf);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(s);
        return s;
    }

    private XSSFColor hexColor(XSSFWorkbook wb, String hex) {
        byte[] rgb = new byte[]{
                (byte) Integer.parseInt(hex.substring(0, 2), 16),
                (byte) Integer.parseInt(hex.substring(2, 4), 16),
                (byte) Integer.parseInt(hex.substring(4, 6), 16)
        };
        return new XSSFColor(rgb, wb.getStylesSource().getIndexedColors());
    }

    private void setBorder(XSSFCellStyle s) {
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        s.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
    }

    private void setCell(Row row, int col, String value, XSSFCellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private String statusLabel(UserStatus status) {
        return switch (status) {
            case ACTIVE  -> "✅ Активный";
            case PENDING -> "⏳ Ожидание";
            case BLOCKED -> "🚫 Заблокирован";
        };
    }
}