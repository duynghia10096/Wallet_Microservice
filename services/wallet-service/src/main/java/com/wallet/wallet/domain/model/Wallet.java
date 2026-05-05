package com.wallet.wallet.domain.model;

import com.wallet.wallet.domain.exception.InsufficientBalanceException;
import com.wallet.wallet.domain.valueobject.Money;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

// import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "wallets")
public class Wallet {
    @Id
    private UUID id;
    private String walletNumber;
    private UUID userId;
    @Enumerated(EnumType.STRING)
    private WalletType walletType;
    private String currency;
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "balance_amount")),
        @AttributeOverride(name = "currency", column = @Column(name = "balance_currency"))
    })
    private Money balance;
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "available_balance_amount")),
        @AttributeOverride(name = "currency", column = @Column(name = "available_balance_currency"))
    })
    private Money availableBalance;
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "frozen_balance_amount")),
        @AttributeOverride(name = "currency", column = @Column(name = "frozen_balance_currency"))
    })
    private Money frozenBalance;
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "daily_limit_amount")),
        @AttributeOverride(name = "currency", column = @Column(name = "daily_limit_currency"))
    })
    private Money dailyLimit;
    @Enumerated(EnumType.STRING)
    private WalletStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Version
    private long version;

    private Wallet() {}

    public static Wallet create(UUID userId, WalletType walletType, String currency) {
        Wallet w = new Wallet();
        w.id = UUID.randomUUID();
        w.userId = userId;
        w.walletType = walletType;
        w.currency = currency;
        w.balance = Money.of(BigDecimal.ZERO, currency);
        w.availableBalance = Money.of(BigDecimal.ZERO, currency);
        w.frozenBalance = Money.of(BigDecimal.ZERO, currency);
        w.dailyLimit = walletType == WalletType.BUSINESS
                ? Money.of(BigDecimal.valueOf(500_000_000), currency)
                : Money.of(BigDecimal.valueOf(50_000_000), currency);
        w.status = WalletStatus.ACTIVE;
        w.createdAt = LocalDateTime.now();
        w.updatedAt = LocalDateTime.now();
        w.version = 0;
        return w;
    }

    public static Wallet reconstitute(UUID id, String walletNumber, UUID userId,
            WalletType walletType, String currency, Money balance,
            Money availableBalance, Money frozenBalance, Money dailyLimit,
            WalletStatus status, LocalDateTime createdAt, LocalDateTime updatedAt, long version) {
        Wallet w = new Wallet();
        w.id = id; w.walletNumber = walletNumber; w.userId = userId;
        w.walletType = walletType; w.currency = currency;
        w.balance = balance; w.availableBalance = availableBalance; w.frozenBalance = frozenBalance;
        w.dailyLimit = dailyLimit; w.status = status;
        w.createdAt = createdAt; w.updatedAt = updatedAt; w.version = version;
        return w;
    }

    public void assignWalletNumber(String number) {
        this.walletNumber = number;
    }

    public void credit(Money amount, String reason) {
        assertActive();
        this.balance = this.balance.add(amount);
        this.availableBalance = this.availableBalance.add(amount);
        this.updatedAt = LocalDateTime.now();
    }

    public void debit(Money amount, String reason) {
        assertActive();
        if (amount.isGreaterThan(this.availableBalance)) {
            throw new InsufficientBalanceException(this.id.toString());
        }
        this.balance = this.balance.subtract(amount);
        this.availableBalance = this.availableBalance.subtract(amount);
        this.updatedAt = LocalDateTime.now();
    }

    public void freeze(Money amount) {
        assertActive();
        if (amount.isGreaterThan(this.availableBalance)) {
            throw new InsufficientBalanceException(this.id.toString());
        }
        this.availableBalance = this.availableBalance.subtract(amount);
        this.frozenBalance = this.frozenBalance.add(amount);
        this.updatedAt = LocalDateTime.now();
    }

    public void unfreeze(Money amount) {
        this.frozenBalance = this.frozenBalance.subtract(amount);
        this.availableBalance = this.availableBalance.add(amount);
        this.updatedAt = LocalDateTime.now();
    }

    public void suspend(String reason) {
        this.status = WalletStatus.SUSPENDED;
        this.updatedAt = LocalDateTime.now();
    }

    public void activate() {
        this.status = WalletStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isActive() { return this.status == WalletStatus.ACTIVE; }

    private void assertActive() {
        if (this.status != WalletStatus.ACTIVE) {
            throw new com.wallet.common.exception.ServiceException(
                    "Wallet is not active: " + id, "WALLET_NOT_ACTIVE",
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }
}
