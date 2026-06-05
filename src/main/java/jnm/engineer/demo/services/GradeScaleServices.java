package jnm.engineer.demo.services;

import jnm.engineer.demo.models.GradingScale;
import jnm.engineer.demo.repositories.GradeScaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GradeScaleServices {

    private final GradeScaleRepository gradeScaleRepository;

    public List<GradingScale> getAllGradeScales(){
        return gradeScaleRepository.findAll();
    }

    public GradingScale getById(Long id){
        return gradeScaleRepository.findById(id)
                .orElseThrow(()-> new RuntimeException("The grade scale does not exist"));
    }

    public GradingScale getGradeForMark(Double mark){
        return gradeScaleRepository.findByMark(mark)
                .orElseThrow(()-> new RuntimeException("no grade found for the mark " + mark));
    }

    public GradingScale create( GradingScale gradingScale){
        return  gradeScaleRepository.save(gradingScale);
    }

    public GradingScale update(Long id, GradingScale updated){
        GradingScale existing = getById(id);
        existing.setGradeLetter(updated.getGradeLetter());
        existing.setPoints(updated.getPoints());
        existing.setMinMark(updated.getMinMark());
        existing.setMaxMark(updated.getMaxMark());
        existing.setRemarks(updated.getRemarks());
        return gradeScaleRepository.save(existing);
    }

    public void delete(Long id){
        getById(id);
        gradeScaleRepository.deleteById(id);
    }
}
