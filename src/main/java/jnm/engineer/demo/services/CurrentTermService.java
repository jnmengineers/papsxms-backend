package jnm.engineer.demo.services;

import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The current term — the Academic Year row marked active on the Academic Years page.
 * Looks the fields up in the AcademicYear model's structure first (yearLabel, term, and
 * isActive or active), so it never runs a query that could fail inside someone's save.
 */
@Service
@RequiredArgsConstructor
public class CurrentTermService {
    private final EntityManager entityManager;

    public record Term(String yearLabel, int term) {
        public String label() { return yearLabel + " Term " + term; }
    }

    @Transactional(readOnly = true)
    public Term current() {
        EntityType<?> entity = entityManager.getMetamodel().getEntities().stream()
                .filter(e -> e.getName().equals("AcademicYear") || e.getJavaType().getSimpleName().equals("AcademicYear"))
                .findFirst()
                .orElseThrow(() -> problem("The Academic Year model wasn't found."));
        Set<String> fields = entity.getAttributes().stream().map(Attribute::getName).collect(Collectors.toSet());
        String activeField = fields.contains("isActive") ? "isActive" : fields.contains("active") ? "active" : null;
        if (activeField == null || !fields.contains("yearLabel") || !fields.contains("term"))
            throw problem("The Academic Year model doesn't have the expected fields (yearLabel, term, isActive).");

        List<Object[]> rows = entityManager.createQuery(
                "SELECT a.yearLabel, a.term FROM " + entity.getName() + " a WHERE a." + activeField + " = true", Object[].class)
                .getResultList();
        return rows.stream()
                .map(r -> new Term(String.valueOf(r[0]).trim(), parseTerm(r[1])))
                .filter(t -> t.yearLabel().matches("\\d{4}") && t.term() >= 1 && t.term() <= 3)
                .max(Comparator.comparing(Term::yearLabel).thenComparingInt(Term::term))   // if several are active, the latest
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "No current term is set. Ask the admin to mark the current term as active on the Academic Years page."));
    }

    private static int parseTerm(Object v) {
        try { return Integer.parseInt(String.valueOf(v).replaceAll("\\D", "")); } catch (NumberFormatException e) { return 0; }
    }

    private static ResponseStatusException problem(String m) {
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, m + " Please send AcademicYear.java to the developer.");
    }
}
