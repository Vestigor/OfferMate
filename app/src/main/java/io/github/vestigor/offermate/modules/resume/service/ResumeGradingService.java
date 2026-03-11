package io.github.vestigor.offermate.modules.resume.service;

import io.github.vestigor.offermate.modules.resume.model.dto.ResumeAnalysisResponse;

public interface ResumeGradingService {

    ResumeAnalysisResponse analyzeResume(String resumeText);
}
