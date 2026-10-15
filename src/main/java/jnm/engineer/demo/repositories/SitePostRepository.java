package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.SitePost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SitePostRepository extends JpaRepository<SitePost, Long> {
    List<SitePost> findAllByOrderByCreatedAtDesc();
    List<SitePost> findByPublishedTrueOrderByCreatedAtDesc();
}
