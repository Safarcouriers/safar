package com.saffaricarrers.saffaricarrers.Dtos;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadRequest {
    private MultipartFile aadharFrontImage;
    private MultipartFile aadharBackImage;
    private MultipartFile panCardImage;
    private MultipartFile profileImage;
}