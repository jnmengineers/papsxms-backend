package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    /** Every invoice with its lines in one query (for the money-group reports). */
    @Query("select distinct i from Invoice i left join fetch i.lines")
    List<Invoice> findAllWithLines();

    /** Every item name ever charged on an invoice (for assigning items to money groups). */
    @Query("select distinct l.name from Invoice i join i.lines l")
    List<String> distinctLineNames();

    boolean existsByStudentStudentIdAndYearLabelAndTerm(Long studentId, String yearLabel, Integer term);
    List<Invoice> findByStudentStudentIdOrderByCreatedAtAsc(Long studentId);
    List<Invoice> findByYearLabelAndTerm(String yearLabel, Integer term);

    /** [studentId, total invoiced] for every learner */
    @Query("select i.student.studentId, sum(i.amount) from Invoice i group by i.student.studentId")
    List<Object[]> totalsByStudent();
}
