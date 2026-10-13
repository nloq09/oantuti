package com.rpsbattle.bot.persistence;
import com.rpsbattle.bot.ownership.BotMetadata;
import com.rpsbattle.bot.upload.BotLanguage;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="bots")
public class BotEntity {
    @Id UUID id;
    @Column(name="owner_user_id",nullable=false) UUID ownerUserId;
    @Column(nullable=false,length=60) String name;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) BotLanguage language;
    @Column(name="source_path",nullable=false,length=1024) String sourcePath;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) BotMetadata.Status status;
    @Column(name="created_at",nullable=false) Instant createdAt;
    @Column(name="updated_at",nullable=false) Instant updatedAt;
    protected BotEntity() {}
    BotEntity(BotMetadata b) {id=b.id();ownerUserId=b.ownerUserId();name=b.name();language=b.language();sourcePath=b.sourcePath();status=b.status();createdAt=b.createdAt();updatedAt=b.updatedAt();}
    BotMetadata domain() {return new BotMetadata(id,ownerUserId,name,language,sourcePath,status,createdAt,updatedAt);}
}
