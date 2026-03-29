package com.saffaricarrers.saffaricarrers.Repository;


import com.saffaricarrers.saffaricarrers.Entity.Issue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IssueRepository extends JpaRepository<Issue, Long> {

    // Get all issues for a user, newest first
    List<Issue> findByFirebaseUidOrderByCreatedAtDesc(String firebaseUid);

    // Get issues by status for a user
    List<Issue> findByFirebaseUidAndStatusOrderByCreatedAtDesc(String firebaseUid, Issue.Status status);

    // Count open issues for a user
    long countByFirebaseUidAndStatus(String firebaseUid, Issue.Status status);
}