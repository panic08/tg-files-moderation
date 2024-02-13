package ru.marthastudios.tgsteamcookiegetter.repository;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.marthastudios.tgsteamcookiegetter.enums.UserRole;
import ru.marthastudios.tgsteamcookiegetter.model.User;

import java.util.List;

@Repository
public interface UserRepository extends CrudRepository<User, Long> {
    @Query("SELECT u.* FROM users_table u WHERE u.telegram_user_id = :telegramUserId")
    User findByTelegramUserId(@Param("telegramUserId") long telegramUserId);

    @Query("SELECT u.telegram_user_id FROM users_table u WHERE u.role = :role")
    List<Long> findAllTelegramUserIdByRole(@Param("role") UserRole role);

    @Query("SELECT u.balance FROM users_table u WHERE u.telegram_user_id = :telegramUserId")
    double findBalanceByTelegramUserId(@Param("telegramUserId") long telegramUserId);

    @Query("UPDATE users_table SET balance = :balance WHERE telegram_user_id = :telegramUserId")
    @Modifying
    void updateBalanceByTelegramUserId(@Param("balance") double balance, @Param("telegramUserId") long telegramUserId);
}
