package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.StaffMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StaffMemberRepository extends JpaRepository<StaffMember, Long> {
    List<StaffMember> findAllByOrderByActiveDescFullNameAsc();
    boolean existsByStaffNumberIgnoreCaseAndStaffIdNot(String staffNumber, Long staffId);
    boolean existsByStaffNumberIgnoreCase(String staffNumber);
    List<StaffMember> findByActiveTrueOrderByFullNameAsc();
}
