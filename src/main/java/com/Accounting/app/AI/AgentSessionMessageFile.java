package com.Accounting.app.AI;

import java.time.Instant;

import com.Accounting.app.files.UploadedFile;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "agent_session_message_files")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
public class AgentSessionMessageFile {
    @EmbeddedId
    @EqualsAndHashCode.Include
    private AgentSessionMessageFileId id = new AgentSessionMessageFileId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("messageId")
    @JoinColumn(name = "message_id")
    @ToString.Exclude
    private AgentSessionMessage message;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("uploadedFileId")
    @JoinColumn(name = "uploaded_file_id")
    @ToString.Exclude
    private UploadedFile uploadedFile;

    @Column(columnDefinition = "TEXT")
    private String uploadRequest;

    @Column(columnDefinition = "TEXT")
    private String extractedData;

    private Instant createdAt;

    public AgentSessionMessageFile(
            AgentSessionMessage message,
            UploadedFile uploadedFile,
            String uploadRequest,
            String extractedData,
            Instant createdAt) {
        this.message = message;
        this.uploadedFile = uploadedFile;
        this.uploadRequest = uploadRequest;
        this.extractedData = extractedData;
        this.createdAt = createdAt;
        this.id = new AgentSessionMessageFileId(message.getId(), uploadedFile.getId());
    }
}
