package jnm.engineer.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class BulkResultResponse {
    private int saved;
    private int updated;
    private int skipped;
    private int failed;
    private List<String> errors;
    private String message;
}