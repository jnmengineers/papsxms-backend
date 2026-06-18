package jnm.engineer.demo.dto;

import lombok.Data;
import java.util.List;

@Data
public class BulkResultRequest {

    private Long examId;

    private List<ResultItem> results;

    @Data
    public static class ResultItem {
        private Long studentId;
        private Long subjectId;
        private Double marksObtained;
        private Double maxMarks = 100.0;
        private Long resultId; // null = new, non-null = update
    }
}