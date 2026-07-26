package com.Accounting.app.AI;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentSessionMessageFileId implements Serializable {
    @Column(name = "message_id")
    private String messageId;

    @Column(name = "uploaded_file_id")
    private Integer uploadedFileId;
}
