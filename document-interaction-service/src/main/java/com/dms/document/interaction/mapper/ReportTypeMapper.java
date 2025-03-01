package com.dms.document.interaction.mapper;

import com.dms.document.interaction.dto.CommentReportResponse;
import com.dms.document.interaction.dto.ReportResponse;
import com.dms.document.interaction.dto.ReportTypeResponse;
import com.dms.document.interaction.dto.TranslationDTO;
import com.dms.document.interaction.model.CommentReport;
import com.dms.document.interaction.model.DocumentReport;
import com.dms.document.interaction.model.MasterData;
import org.springframework.stereotype.Component;

@Component
public class ReportTypeMapper {

    public ReportTypeResponse mapToReportTypeResponse(MasterData masterData) {
        TranslationDTO translation = new TranslationDTO();
        translation.setEn(masterData.getTranslations().getEn());
        translation.setVi(masterData.getTranslations().getVi());

        return new ReportTypeResponse(
                masterData.getCode(),
                translation,
                masterData.getDescription()
        );
    }

    public ReportResponse mapToResponse(DocumentReport report, MasterData reportType) {
        TranslationDTO translation = new TranslationDTO();
        translation.setEn(reportType.getTranslations().getEn());
        translation.setVi(reportType.getTranslations().getVi());

        return new ReportResponse(
                report.getId(),
                report.getDocumentId(),
                report.getReportTypeCode(),
                translation,
                report.getDescription(),
                report.getCreatedAt()
        );
    }

    public CommentReportResponse mapToResponse(CommentReport report, MasterData reportType) {
        TranslationDTO translation = new TranslationDTO();
        translation.setEn(reportType.getTranslations().getEn());
        translation.setVi(reportType.getTranslations().getVi());

        return new CommentReportResponse(
                report.getId(),
                report.getDocumentId(),
                report.getCommentId(),
                report.getReportTypeCode(),
                translation,
                report.getDescription(),
                report.getCreatedAt()
        );
    }


}
