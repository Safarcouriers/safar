package com.saffaricarrers.saffaricarrers.Controller;

import com.saffaricarrers.saffaricarrers.Dtos.IssueDTO;
import com.saffaricarrers.saffaricarrers.Services.IssueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users/{firebaseUid}/issues")
@RequiredArgsConstructor
public class IssueController {

    private final IssueService issueService;

    // POST /api/users/{firebaseUid}/issues
    @PostMapping
    public ResponseEntity<IssueDTO.ApiResponse<IssueDTO.Response>> createIssue(
            @PathVariable String firebaseUid,
            @RequestBody IssueDTO.CreateRequest request
    ) {
        request.setFirebaseUid(firebaseUid);
        IssueDTO.Response response = issueService.createIssue(request);
        return ResponseEntity.ok(IssueDTO.ApiResponse.ok("Issue submitted successfully", response));
    }

    // GET /api/users/{firebaseUid}/issues
    @GetMapping
    public ResponseEntity<IssueDTO.ApiResponse<List<IssueDTO.Response>>> getUserIssues(
            @PathVariable String firebaseUid
    ) {
        List<IssueDTO.Response> issues = issueService.getUserIssues(firebaseUid);
        return ResponseEntity.ok(IssueDTO.ApiResponse.ok("Issues fetched successfully", issues));
    }

    // GET /api/users/{firebaseUid}/issues/{issueId}
    @GetMapping("/{issueId}")
    public ResponseEntity<IssueDTO.ApiResponse<IssueDTO.Response>> getIssue(
            @PathVariable String firebaseUid,
            @PathVariable Long issueId
    ) {
        IssueDTO.Response issue = issueService.getIssue(issueId, firebaseUid);
        return ResponseEntity.ok(IssueDTO.ApiResponse.ok("Issue fetched successfully", issue));
    }

    // PUT /api/users/{firebaseUid}/issues/{issueId}/status  (admin use)
    @PutMapping("/{issueId}/status")
    public ResponseEntity<IssueDTO.ApiResponse<IssueDTO.Response>> updateStatus(
            @PathVariable String firebaseUid,
            @PathVariable Long issueId,
            @RequestBody IssueDTO.UpdateStatusRequest request
    ) {
        IssueDTO.Response response = issueService.updateIssueStatus(issueId, request);
        return ResponseEntity.ok(IssueDTO.ApiResponse.ok("Issue status updated", response));
    }

    // DELETE /api/users/{firebaseUid}/issues/{issueId}
    @DeleteMapping("/{issueId}")
    public ResponseEntity<IssueDTO.ApiResponse<Void>> deleteIssue(
            @PathVariable String firebaseUid,
            @PathVariable Long issueId
    ) {
        issueService.deleteIssue(issueId, firebaseUid);
        return ResponseEntity.ok(IssueDTO.ApiResponse.ok("Issue deleted", null));
    }
}