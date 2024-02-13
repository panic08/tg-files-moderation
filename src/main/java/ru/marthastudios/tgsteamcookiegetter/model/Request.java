package ru.marthastudios.tgsteamcookiegetter.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import ru.marthastudios.tgsteamcookiegetter.enums.RequestStatus;

@Table(name = "requests_table")
@Data
@Builder
public class Request {
    @Id
    private Long id;

    @Column("user_id")
    private Long userId;

    private Double amount;

    private RequestStatus status;

    @Column("file_path")
    private String filePath;

    @Column("created_at")
    private Long createdAt;
}
