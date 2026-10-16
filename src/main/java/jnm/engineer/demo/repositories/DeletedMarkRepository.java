package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.DeletedMark;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeletedMarkRepository extends JpaRepository<DeletedMark, Long> {
    List<DeletedMark> findTop500ByOrderByDeletedAtDesc();
}
