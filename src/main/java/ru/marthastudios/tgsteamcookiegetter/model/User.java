package ru.marthastudios.tgsteamcookiegetter.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import ru.marthastudios.tgsteamcookiegetter.enums.UserRole;

@Table(name = "users_table")
@Data
@Builder
public class User {
    @Id
    private Long id;

    @Column("telegram_user_id")
    private Long telegramUserId;

    private UserRole role;

    private Double balance;

    @Column("registered_at")
    private Long registeredAt;
}
