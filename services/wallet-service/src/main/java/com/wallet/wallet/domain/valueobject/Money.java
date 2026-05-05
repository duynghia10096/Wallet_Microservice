package com.wallet.wallet.domain.valueobject;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
@EqualsAndHashCode
@Embeddable
public final class Money {
    private BigDecimal amount;
    private String currency;

    // JPA default constructor
    protected Money() {
    }

    private Money(BigDecimal amount, String currency) {
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        this.currency = currency;
    }

    public static Money of(BigDecimal amount, String currency) {
        if (amount == null)
            throw new IllegalArgumentException("Amount cannot be null");
        return new Money(amount, currency);
    }

    public static Money ofVnd(BigDecimal amount) {
        return of(amount, "VND");
    }

    public Money add(Money other) {
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        BigDecimal r = this.amount.subtract(other.amount);
        if (r.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Negative result");
        return new Money(r, this.currency);
    }

    public boolean isGreaterThan(Money other) {
        return this.amount.compareTo(other.amount) > 0;
    }

    public boolean isGreaterThanOrEqual(Money other) {
        return this.amount.compareTo(other.amount) >= 0;
    }

    public boolean isZero() {
        return this.amount.compareTo(BigDecimal.ZERO) == 0;
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }
}
