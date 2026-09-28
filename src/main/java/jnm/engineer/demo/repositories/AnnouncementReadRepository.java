package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.AnnouncementRead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnnouncementReadRepository extends JpaRepository<AnnouncementRead, Long> {
    List<AnnouncementRead> findByUsername(String username);
    List<AnnouncementRead> findByAnnouncementAnnouncementId(Long announcementId);
    boolean existsByAnnouncementAnnouncementIdAndUsername(Long announcementId, String username);
    long countByAnnouncementAnnouncementId(Long announcementId);
}
