package io.github.wantaekchoi.agentpay.identity.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "agents")
public class Agent {
    @Id private UUID id;
    @Column(name = "owner_user_id") private UUID ownerUserId;
    @Column(name = "public_key") private String publicKey;
    private String address;
    private String did;
    private String alias;
    private String status;

    protected Agent() {}
    public Agent(UUID id, UUID ownerUserId, String publicKey, String address,
                 String did, String alias, String status) {
        this.id = id; this.ownerUserId = ownerUserId; this.publicKey = publicKey;
        this.address = address; this.did = did; this.alias = alias; this.status = status;
    }
    public UUID getId() { return id; }
    public UUID getOwnerUserId() { return ownerUserId; }
    public String getPublicKey() { return publicKey; }
    public String getAddress() { return address; }
    public String getDid() { return did; }
    public String getAlias() { return alias; }
    public String getStatus() { return status; }
}
