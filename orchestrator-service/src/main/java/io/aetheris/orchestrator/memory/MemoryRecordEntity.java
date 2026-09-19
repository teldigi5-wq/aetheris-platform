package io.aetheris.orchestrator.memory;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name="aetheris_memory_records", indexes={
        @Index(name="idx_memory_owner_updated", columnList="ownerId,updatedAt"),
        @Index(name="idx_memory_owner_scope_project", columnList="ownerId,scope,projectId"),
        @Index(name="idx_memory_expiry", columnList="expiresAt")
})
public class MemoryRecordEntity {
    @Id private UUID id;
    @Column(nullable=false,length=120) private String ownerId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private MemoryScope scope;
    @Column(length=160) private String projectId;
    @Column(nullable=false,length=240) private String memoryKey;
    @Column(nullable=false,length=24000) private String payload;
    @Column(nullable=false) private boolean encrypted;
    @Column(nullable=false) private boolean sensitive;
    @Column(nullable=false,length=4000) private String tagsCsv;
    @Column(nullable=false,length=80) private String provenanceType;
    @Column(length=800) private String provenanceReference;
    @Column(nullable=false) private Instant createdAt;
    @Column(nullable=false) private Instant updatedAt;
    private Instant expiresAt;

    protected MemoryRecordEntity() {}

    public MemoryRecordEntity(UUID id,String ownerId,MemoryScope scope,String projectId,String memoryKey,
                              String payload,boolean encrypted,boolean sensitive,Set<String> tags,
                              String provenanceType,String provenanceReference,Instant expiresAt) {
        this.id=Objects.requireNonNull(id,"id");
        this.ownerId=require(ownerId,"ownerId",120);
        this.createdAt=Instant.now();
        update(scope,projectId,memoryKey,payload,encrypted,sensitive,tags,provenanceType,provenanceReference,expiresAt);
    }

    public void update(MemoryScope scope,String projectId,String memoryKey,String payload,boolean encrypted,
                       boolean sensitive,Set<String> tags,String provenanceType,String provenanceReference,Instant expiresAt) {
        this.scope=scope==null?MemoryScope.PROJECT:scope;
        this.projectId=normalizeProject(this.scope,projectId);
        this.memoryKey=require(memoryKey,"memoryKey",240);
        this.payload=require(payload,"payload",24000);
        this.encrypted=encrypted;
        this.sensitive=sensitive;
        this.tagsCsv=tags==null?"":tags.stream().filter(Objects::nonNull).map(String::trim)
                .filter(s->!s.isBlank()).map(String::toLowerCase).distinct().sorted().collect(Collectors.joining(","));
        this.provenanceType=require(provenanceType==null?"OWNER_INPUT":provenanceType,"provenanceType",80);
        this.provenanceReference=trimNullable(provenanceReference,800);
        this.expiresAt=expiresAt;
        this.updatedAt=Instant.now();
        if(this.createdAt==null)this.createdAt=this.updatedAt;
    }

    public UUID getId(){return id;}
    public String getOwnerId(){return ownerId;}
    public MemoryScope getScope(){return scope;}
    public String getProjectId(){return projectId;}
    public String getMemoryKey(){return memoryKey;}
    public String getPayload(){return payload;}
    public boolean isEncrypted(){return encrypted;}
    public boolean isSensitive(){return sensitive;}
    public String getProvenanceType(){return provenanceType;}
    public String getProvenanceReference(){return provenanceReference;}
    public Instant getCreatedAt(){return createdAt;}
    public Instant getUpdatedAt(){return updatedAt;}
    public Instant getExpiresAt(){return expiresAt;}
    public Set<String> getTags(){
        if(tagsCsv==null||tagsCsv.isBlank())return Set.of();
        return Arrays.stream(tagsCsv.split(",")).map(String::trim).filter(s->!s.isBlank())
                .collect(Collectors.toCollection(TreeSet::new));
    }
    public boolean expiredAt(Instant now){return expiresAt!=null&&!expiresAt.isAfter(now);}

    private static String normalizeProject(MemoryScope scope,String projectId){
        String value=trimNullable(projectId,160);
        if(scope==MemoryScope.PROJECT&&(value==null||value.isBlank()))throw new IllegalArgumentException("projectId is required for PROJECT memory");
        return value;
    }
    private static String require(String value,String label,int max){
        if(value==null||value.isBlank())throw new IllegalArgumentException(label+" is required");
        String v=value.trim(); if(v.length()>max)throw new IllegalArgumentException(label+" is too long"); return v;
    }
    private static String trimNullable(String value,int max){
        if(value==null||value.isBlank())return null;
        String v=value.trim(); if(v.length()>max)throw new IllegalArgumentException("value is too long"); return v;
    }
}
