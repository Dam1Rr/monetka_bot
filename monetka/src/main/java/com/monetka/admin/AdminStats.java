package com.monetka.admin;

import java.math.BigDecimal;

/**
 * Immutable snapshot of bot-wide statistics shown in admin panel.
 */
public final class AdminStats {

    private final long       totalUsers;
    private final long       activeUsers;
    private final long       pendingUsers;
    private final long       blockedUsers;
    private final long       totalTransactions;
    private final BigDecimal totalExpenses;
    private final long       totalSubscriptions;

    public AdminStats(long totalUsers, long activeUsers, long pendingUsers,
                      long blockedUsers, long totalTransactions,
                      BigDecimal totalExpenses, long totalSubscriptions) {
        this.totalUsers         = totalUsers;
        this.activeUsers        = activeUsers;
        this.pendingUsers       = pendingUsers;
        this.blockedUsers       = blockedUsers;
        this.totalTransactions  = totalTransactions;
        this.totalExpenses      = totalExpenses;
        this.totalSubscriptions = totalSubscriptions;
    }

    public long       getTotalUsers()         { return totalUsers; }
    public long       getActiveUsers()        { return activeUsers; }
    public long       getPendingUsers()       { return pendingUsers; }
    public long       getBlockedUsers()       { return blockedUsers; }
    public long       getTotalTransactions()  { return totalTransactions; }
    public BigDecimal getTotalExpenses()      { return totalExpenses; }
    public long       getTotalSubscriptions() { return totalSubscriptions; }
}