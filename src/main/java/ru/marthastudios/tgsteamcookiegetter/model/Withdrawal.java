package ru.marthastudios.tgsteamcookiegetter.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table(name = "withdrawals_table")
@Data
@Builder
public class Withdrawal {
    @Id
    private Long id;
    @Column("amount")
    private Double amount;
    @Column("user_id")
    private Long userId;
}
