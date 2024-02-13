package ru.marthastudios.tgsteamcookiegetter.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import ru.marthastudios.tgsteamcookiegetter.model.Withdrawal;

@Repository
public interface WithdrawalRepository extends CrudRepository<Withdrawal, Long> {
}
