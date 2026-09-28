package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findBySpentOnBetweenOrderBySpentOnDescExpenseIdDesc(LocalDate from, LocalDate to);
    List<Expense> findByYearLabelAndTermOrderBySpentOnDescExpenseIdDesc(String yearLabel, Integer term);
    long countByGroupGroupId(Long groupId);
}
