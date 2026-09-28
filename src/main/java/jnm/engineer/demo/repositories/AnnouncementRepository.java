package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    List<Announcement> findByArchivedFalseOrderByPinnedDescStartsOnDescAnnouncementIdDesc();
    List<Announcement> findAllByOrderByArchivedAscPinnedDescStartsOnDescAnnouncementIdDesc();
}
