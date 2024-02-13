package ru.marthastudios.tgsteamcookiegetter.repository;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.marthastudios.tgsteamcookiegetter.enums.RequestStatus;
import ru.marthastudios.tgsteamcookiegetter.model.Request;

import java.util.List;

@Repository
public interface RequestRepository extends CrudRepository<Request, Long> {
    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM requests_table r WHERE r.user_id = :userId AND r.amount IS NOT NULL")
    double sumAmountByUserId(@Param("userId") long userId);

    @Query("SELECT r.file_path FROM requests_table r WHERE r.id = :id")
    String findFilePathById(@Param("id") long id);

    @Query("UPDATE requests_table SET file_path = :filePath WHERE id = :id")
    @Modifying
    void updateFilePathById(@Param("filePath") String filePath, @Param("id") long id);

    @Query("UPDATE requests_table SET amount = :amount WHERE id = :id")
    @Modifying
    void updateAmountById(@Param("amount") double amount, @Param("id") long id);

    @Query("UPDATE requests_table SET status = :status WHERE id = :id")
    @Modifying
    void updateStatusById(@Param("status") RequestStatus status, @Param("id") long id);

    @Query("SELECT r.* FROM requests_table r WHERE r.user_id = :userId AND r.status = :status OFFSET :offset LIMIT :limit")
    List<Request> findAllByUserIdAndStatusWithOffsetLimit(@Param("userId") long userId, @Param("status") RequestStatus status,
                                                          @Param("offset") int offset, @Param("limit") int limit);

    @Query("SELECT r.* FROM requests_table r WHERE r.user_id = :userId AND r.status = :status")
    List<Request> findAllByUserIdAndStatus(@Param("userId") long userId, @Param("status") RequestStatus status);

    @Query("SELECT count(r) FROM requests_table r WHERE r.user_id = :userId")
    long countByUserId(@Param("userId") long userId);
}
