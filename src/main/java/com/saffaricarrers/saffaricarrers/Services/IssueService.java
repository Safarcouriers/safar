package com.saffaricarrers.saffaricarrers.Services;


import com.saffaricarrers.saffaricarrers.Dtos.IssueDTO;
import com.saffaricarrers.saffaricarrers.Entity.Issue;
import com.saffaricarrers.saffaricarrers.Repository.IssueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IssueService {

    private final IssueRepository issueRepository;

    // ── Create issue ──────────────────────────────────────────────────────────
    public IssueDTO.Response createIssue(IssueDTO.CreateRequest request) {
        Issue issue = Issue.builder()
                .firebaseUid(request.getFirebaseUid())
                .category(request.getCategory())
                .description(request.getDescription())
                .priority(request.getPriority() != null ? request.getPriority() : Issue.Priority.medium)
                .status(Issue.Status.open)
                .build();

        return IssueDTO.Response.from(issueRepository.save(issue));
    }

    // ── Get all issues for user ───────────────────────────────────────────────
    public List<IssueDTO.Response> getUserIssues(String firebaseUid) {
        return issueRepository
                .findByFirebaseUidOrderByCreatedAtDesc(firebaseUid)
                .stream()
                .map(IssueDTO.Response::from)
                .collect(Collectors.toList());
    }

    // ── Get single issue ──────────────────────────────────────────────────────
    public IssueDTO.Response getIssue(Long issueId, String firebaseUid) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new RuntimeException("Issue not found"));

        if (!issue.getFirebaseUid().equals(firebaseUid)) {
            throw new RuntimeException("Access denied");
        }

        return IssueDTO.Response.from(issue);
    }

    // ── Update status (admin or system) ──────────────────────────────────────
    public IssueDTO.Response updateIssueStatus(Long issueId, IssueDTO.UpdateStatusRequest request) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new RuntimeException("Issue not found"));

        issue.setStatus(request.getStatus());
        if (request.getAdminNotes() != null) issue.setAdminNotes(request.getAdminNotes());
        if (request.getResolvedBy() != null) issue.setResolvedBy(request.getResolvedBy());

        return IssueDTO.Response.from(issueRepository.save(issue));
    }

    // ── Delete issue ──────────────────────────────────────────────────────────
    public void deleteIssue(Long issueId, String firebaseUid) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new RuntimeException("Issue not found"));

        if (!issue.getFirebaseUid().equals(firebaseUid)) {
            throw new RuntimeException("Access denied");
        }

        issueRepository.delete(issue);
    }
}