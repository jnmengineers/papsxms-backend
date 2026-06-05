package jnm.engineer.demo.services;

import jnm.engineer.demo.models.AcademicYear;
import jnm.engineer.demo.repositories.AcademicYearRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AcademicYearService {
    private final AcademicYearRepository academicYearRepository;

    public List<AcademicYear> getAllAcademicYears(){
        return academicYearRepository.findAll();
    }

    public AcademicYear getById (Long id){
        return academicYearRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Academic year not found with id: "
                        + id));

    }

    public List<AcademicYear> getActiveYears(){
        return academicYearRepository.findByIsActiveTrue();
    }


    public List<AcademicYear> getByYearLabel(String yearLabel){
        return academicYearRepository.findByYearLabel(yearLabel);
    }

    public AcademicYear create(AcademicYear academicYear){
        return academicYearRepository.save(academicYear);
    }

    public AcademicYear update(Long id, AcademicYear updated){
        AcademicYear existing = getById(id);
        existing.setYearLabel(updated.getYearLabel());
        existing.setTerm(updated.getTerm());
        existing.setStartDate(updated.getStartDate());
        existing.setEndDate(updated.getEndDate());
        existing.setIsActive(updated.getIsActive());
        return academicYearRepository.save(existing);
    }

    public void delete(Long id){
        getById(id);
        academicYearRepository.deleteById(id);
    }

    //Deactivate all, then activate the selected one
    public AcademicYear setActive(Long id) {
        List<AcademicYear> allActive = academicYearRepository.findByIsActiveTrue();

        allActive.forEach(y -> {
            y.setIsActive(false);
            academicYearRepository.save(y);
        });
        AcademicYear target =getById(id);
        target.setIsActive(true);
        return academicYearRepository.save(target);
    }


}






