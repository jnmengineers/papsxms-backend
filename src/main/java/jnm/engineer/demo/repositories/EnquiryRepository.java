package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.Enquiry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface EnquiryRepository extends JpaRepository<Enquiry, Long> {
    List<Enquiry> findAllByOrderByCreatedAtDesc();
    long countByPhoneAndCreatedAtAfter(String phone, LocalDateTime after);
}
