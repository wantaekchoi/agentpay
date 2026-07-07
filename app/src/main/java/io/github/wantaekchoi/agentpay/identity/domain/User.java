package io.github.wantaekchoi.agentpay.identity.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {
    @Id private UUID id;
    private String alias;
    @Column(name = "public_key") private String publicKey;
    private String address;

    protected User() {}
    public User(UUID id, String alias, String publicKey, String address) {
        this.id = id; this.alias = alias; this.publicKey = publicKey; this.address = address;
    }
    public UUID getId() { return id; }
    public String getAlias() { return alias; }
    public String getPublicKey() { return publicKey; }
    public String getAddress() { return address; }
}
