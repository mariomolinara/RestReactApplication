package it.unicas.spring.restreactapplication;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FriendRepository extends JpaRepository<Friend, Long> {

    @Query("""
           SELECT f FROM Friend f
           WHERE LOWER(f.name)  LIKE LOWER(CONCAT('%', :q, '%'))
              OR LOWER(f.email) LIKE LOWER(CONCAT('%', :q, '%'))
              OR LOWER(COALESCE(f.phone, '')) LIKE LOWER(CONCAT('%', :q, '%'))
           """)
    Page<Friend> search(@Param("q") String query, Pageable pageable);
}
