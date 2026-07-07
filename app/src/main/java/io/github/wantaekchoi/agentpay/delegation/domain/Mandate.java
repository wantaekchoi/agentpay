package io.github.wantaekchoi.agentpay.delegation.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.math.BigInteger;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "mandates")
public class Mandate {
    @Id private UUID id;
    @Column(name = "user_id") private UUID userId;
    @Column(name = "agent_id") private UUID agentId;
    private String currency;
    @Column(name = "per_tx_limit", precision = 78, scale = 0) private BigInteger perTxLimit;
    @Column(name = "total_limit", precision = 78, scale = 0) private BigInteger totalLimit;
    @Column(precision = 78, scale = 0) private BigInteger spent;
    @Column(name = "allow_any_payee") private boolean allowAnyPayee;

    @ElementCollection
    @CollectionTable(name = "mandate_allowed_payees", joinColumns = @JoinColumn(name = "mandate_id"))
    @Column(name = "payee")
    private Set<String> allowedPayees = new HashSet<>();

    @Column(name = "valid_from") private long validFrom;
    @Column(name = "valid_until") private long validUntil;
    @Column(precision = 78, scale = 0) private BigInteger nonce;
    @Column(name = "user_signature") private String userSignature;
    @Enumerated(EnumType.STRING) private MandateStatus status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected Mandate() {}

    public Mandate(UUID id, UUID userId, UUID agentId, String currency,
                    BigInteger perTxLimit, BigInteger totalLimit, BigInteger spent,
                    boolean allowAnyPayee, Set<String> allowedPayees,
                    long validFrom, long validUntil, BigInteger nonce,
                    String userSignature, MandateStatus status) {
        this.id = id;
        this.userId = userId;
        this.agentId = agentId;
        this.currency = currency;
        this.perTxLimit = perTxLimit;
        this.totalLimit = totalLimit;
        this.spent = spent;
        this.allowAnyPayee = allowAnyPayee;
        this.allowedPayees = allowedPayees;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.nonce = nonce;
        this.userSignature = userSignature;
        this.status = status;
    }

    public void revoke() {
        this.status = MandateStatus.REVOKED;
    }

    public void addSpent(BigInteger amount) {
        this.spent = this.spent.add(amount);
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getAgentId() { return agentId; }
    public String getCurrency() { return currency; }
    public BigInteger getPerTxLimit() { return perTxLimit; }
    public BigInteger getTotalLimit() { return totalLimit; }
    public BigInteger getSpent() { return spent; }
    public boolean isAllowAnyPayee() { return allowAnyPayee; }
    public Set<String> getAllowedPayees() { return allowedPayees; }
    public long getValidFrom() { return validFrom; }
    public long getValidUntil() { return validUntil; }
    public BigInteger getNonce() { return nonce; }
    public String getUserSignature() { return userSignature; }
    public MandateStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
